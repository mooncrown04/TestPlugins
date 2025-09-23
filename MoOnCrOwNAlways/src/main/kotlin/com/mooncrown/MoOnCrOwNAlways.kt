package com.mooncrown

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus
import java.io.InputStream
import java.util.Locale
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
import java.io.BufferedReader


// --- Ana Eklenti Sınıfı ---
class AnimeDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    override var name = "35 mooncrown always 1978 "
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL =
        "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    private var cachedPlaylist: Playlist? = null
    private val CACHE_KEY = "iptv_playlist_cache"


    private suspend fun checkPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        return try {
            val response = app.head(url)
            if (response.isSuccessful) {
                url
            } else {
                Log.e(name, "Resim URL'si geçersiz: $url, Hata Kodu: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(name, "Resim URL'si kontrol edilirken hata: $url", e)
            null
        }
    }


// --- Yardımcı Sınıflar ---
data class Playlist(val items: List<PlaylistItem> = emptyList())
data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val score: Double? = null
) {
    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())

    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (!reader.readLine().isExtendedM3u()) throw PlaylistParserException.InvalidHeader()

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var line: String? = reader.readLine()
        var currentItem: PlaylistItem? = null
        val currentHeaders = mutableMapOf<String, String>()

        while (line != null) {
            if (line.isNotEmpty()) {
                when {
                    line.startsWith(PlaylistItem.EXT_INF) -> {
                        currentItem = PlaylistItem(
                            title = line.getTitle(),
                            attributes = line.getAttributes(),
                            score = line.getAttributes()["tvg-score"]?.toDoubleOrNull(),
                            headers = currentHeaders.toMap()
                        )
                        currentHeaders.clear()
                    }
                    line.startsWith(PlaylistItem.EXT_VLC_OPT) -> {
                        val header = line.getVlcOptHeader()
                        if (header != null) {
                            currentHeaders[header.first] = header.second
                        }
                    }
                    !line.startsWith("#") -> {
                        if (currentItem != null) {
                            val url = line.getUrl()
                            currentItem = currentItem.copy(url = url)
                            playlistItems.add(currentItem)
                            currentItem = null
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)
    private fun String.getTitle(): String? = split(",").lastOrNull()?.trim()

    private fun String.getAttributes(): Map<String, String> {
        val attributesString = substringAfter("#EXTINF:-1 ")
        val attributes = mutableMapOf<String, String>()
        val quotedRegex = Regex("""([a-zA-Z0-9-]+)="(.*?)"""")
        val unquotedRegex = Regex("""([a-zA-Z0-9-]+)=([^"\s]+)""")

        quotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value.trim()
        }

        unquotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            if (!attributes.containsKey(key)) {
                attributes[key] = value.trim()
            }
        }
        return attributes
    }

    private fun String.getUrl(): String? = split("|").firstOrNull()?.trim()
    
    // EXT-VLC-OPT başlıklarını ayrıştırmak için yeni fonksiyon
    private fun String.getVlcOptHeader(): Pair<String, String>? {
        val regex = Regex("""([^=]+)="?(.*?)"?""")
        val matchResult = regex.find(this.substringAfter("EXTVLCOPT:"))
        if (matchResult != null) {
            val (key, value) = matchResult.destructured
            return Pair(key.trim(), value.trim())
        }
        return null
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    val textWithCleanedChars = text.replace(Regex("[\\u200E\\u200F]"), "")
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""", RegexOption.IGNORE_CASE)
    val format4Regex = Regex("""(.*?)\s*(\d+)\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format5Regex = Regex("""(.*?)\s*S(\d+)E(\d+).*""", RegexOption.IGNORE_CASE)

    val matchResult1 = format1Regex.find(textWithCleanedChars)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult2 = format2Regex.find(textWithCleanedChars)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult3 = format3Regex.find(textWithCleanedChars)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }
    val matchResult4 = format4Regex.find(textWithCleanedChars)
    if (matchResult4 != null) {
        val (title, episodeStr) = matchResult4.destructured
        return Triple(title.trim(), 1, episodeStr.toIntOrNull())
    }

    val matchResult5 = format5Regex.find(textWithCleanedChars)
    if (matchResult5 != null) {
        val (title, episodeStr) = matchResult5.destructured
        return Triple(title.trim(), 1, episodeStr.toIntOrNull())
    }


    
    return Triple(textWithCleanedChars.trim(), null, null)
}


data class LoadData(
    val items: List<PlaylistItem>,
    val title: String,
    val poster: String,
    val group: String,
    val nation: String,
    val season: Int = 1,
    val episode: Int = 0,
    val isDubbed: Boolean,
    val isSubbed: Boolean,
    val score: Double? = null
)

private suspend fun getOrFetchPlaylist(): Playlist {
    Log.d(name, "Playlist verisi ağdan indiriliyor.")
    val content = app.get(mainUrl).text
    val newPlaylist = IptvPlaylistParser().parseM3U(content)
    cachedPlaylist = newPlaylist
    sharedPref?.edit()?.putString(CACHE_KEY, newPlaylist.toJson())?.apply()
    return newPlaylist
}



// isDubbed ve isSubbed fonksiyonları, kodun tekrarını önlemek için yardımcı fonksiyonlar olarak eklendi
private fun isDubbed(item: PlaylistItem): Boolean {
    val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
    val language = item.attributes["tvg-language"]?.lowercase()
    return dubbedKeywords.any { keyword -> item.title.toString().lowercase().contains(keyword) } || language == "tr" || language == "turkish"|| language == "dublaj"|| language == "TÜRKÇE"
}

private fun isSubbed(item: PlaylistItem): Boolean {
    val subbedKeywords = listOf("altyazılı", "altyazi")
    val language = item.attributes["tvg-language"]?.lowercase()
    return subbedKeywords.any { keyword -> item.title.toString().lowercase().contains(keyword) } || language == "en" || language == "eng"
}



override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val kanallar = getOrFetchPlaylist()
    val groupedByCleanTitle = kanallar.items.groupBy {
        val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
        cleanTitle
    }

    val alphabeticGroups = groupedByCleanTitle.toSortedMap().mapNotNull { (cleanTitle, shows) ->
        val firstShow = shows.firstOrNull() ?: return@mapNotNull null

        // POSTER ATAMASI:
        val rawPosterUrl = firstShow.attributes["tvg-logo"]
        val verifiedPosterUrl = checkPosterUrl(rawPosterUrl)
        val finalPosterUrl = verifiedPosterUrl ?: DEFAULT_POSTER_URL

        // Düzeltme: Tüm bölümlerin puanlarından en yükseğini al.
        val score = shows.mapNotNull { it.score }.maxOrNull()

        val isDubbed = isDubbed(firstShow)
        val isSubbed = isSubbed(firstShow)




        val loadData = LoadData(
            items = shows,
            title = cleanTitle,
            poster = finalPosterUrl,
            group = firstShow.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = firstShow.attributes["tvg-country"] ?: "TR",
            isDubbed = isDubbed,
            isSubbed = isSubbed,
            score = score
        )

        val searchResponse = newAnimeSearchResponse(cleanTitle, loadData.toJson())
        searchResponse.apply {
            posterUrl = loadData.poster
            type = TvType.Anime
            this.score = score?.let { Score.from10(it) }
            this.quality = SearchQuality.HD
            if (isDubbed || isSubbed) {
                addDubStatus(dubExist = isDubbed, subExist = isSubbed)
            }
        }

        val firstChar = cleanTitle.firstOrNull()?.uppercaseChar() ?: '#'
        val groupKey = when {
            firstChar.isLetter() -> firstChar.toString()
            firstChar.isDigit() -> "0-9"
            else -> "#"
        }
        Pair(groupKey, searchResponse)
    }.groupBy { it.first }.mapValues { it.value.map { it.second } }.toSortedMap()


    val finalHomePageLists = mutableListOf<HomePageList>()
    val turkishAlphabet = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUVYZ".split("").filter { it.isNotBlank() }
    // Alfabedeki Q, W, X gibi Türkçe'de olmayan ama listede olabilecek harfleri de ekler
    val fullAlphabet = turkishAlphabet + listOf("Q", "W", "X")

    // Grupları işleme listesine ekler.
    val allGroupsToProcess = mutableListOf<String>()
    if (alphabeticGroups.containsKey("0-9")) allGroupsToProcess.add("0-9")
    fullAlphabet.forEach { char ->
        if (alphabeticGroups.containsKey(char)) {
            allGroupsToProcess.add(char)
        }
    }
    if (alphabeticGroups.containsKey("#")) allGroupsToProcess.add("#")

    // Her harf grubunu dolaşır ve ana sayfa listelerini oluşturur.
    allGroupsToProcess.forEach { char ->
        val shows = alphabeticGroups[char]
        if (shows != null && shows.isNotEmpty()) {
            
    // Liste elemanlarını 3 kez çoğaltarak sonsuz döngü hissi yarat
            val infiniteList = shows  //+ shows + shows

        val listTitle = when (char) {
                "0-9" -> "🔢 0-9 ${fullAlphabet.joinToString(" ") { it.lowercase(Locale.getDefault()) }}"
                "#" -> "🔣 # ${fullAlphabet.joinToString(" ") { it.lowercase(Locale.getDefault()) }}"
                else -> {
                    val startIndex = fullAlphabet.indexOf(char)
                    if (startIndex != -1) {
                        val remainingAlphabet = fullAlphabet.subList(startIndex, fullAlphabet.size).joinToString(" ") { it }
                        "🎬 $char ${remainingAlphabet.substring(1).lowercase(Locale.getDefault())}"
                    } else {
                        // Eğer harf alfabede yoksa yedek başlık
                        "🎬 $char"
                    }
                }
            }
          //    finalHomePageLists.add(HomePageList(listTitle, shows, isHorizontalImages = true))
          finalHomePageLists.add(HomePageList(listTitle, infiniteList, isHorizontalImages = true))
        }
    }

    return newHomePageResponse(finalHomePageLists, hasNext = false)
}

override suspend fun search(query: String): List<SearchResponse> {
    val kanallar = getOrFetchPlaylist()
    val groupedByCleanTitle = kanallar.items.groupBy {
        val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
        cleanTitle
    }

    return groupedByCleanTitle.filter { (cleanTitle, _) ->
        cleanTitle.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
    }.map { (cleanTitle, shows) ->
        val firstShow = shows.firstOrNull() ?: return@map newAnimeSearchResponse(cleanTitle, "")

        // POSTER ATAMASI:
        val rawPosterUrl = firstShow.attributes["tvg-logo"]
        val verifiedPosterUrl = checkPosterUrl(rawPosterUrl)
        val finalPosterUrl = verifiedPosterUrl ?: DEFAULT_POSTER_URL

        // Düzeltme: Tüm bölümlerin puanlarından en yükseğini al.
        val score = shows.mapNotNull { it.score }.maxOrNull()
    
        val isDubbed = isDubbed(firstShow)
        val isSubbed = isSubbed(firstShow)

    
        val loadData = LoadData(
            items = shows,
            title = cleanTitle,
            poster = finalPosterUrl,
            group = firstShow.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = firstShow.attributes["tvg-country"] ?: "TR",
            isDubbed = isDubbed,
            isSubbed = isSubbed,
            score = score
        )

        val searchResponse = newAnimeSearchResponse(cleanTitle, loadData.toJson())
        searchResponse.apply {
            posterUrl = loadData.poster
            type = TvType.Anime              
            this.score = score?.let { Score.from10(it) }
            this.quality = SearchQuality.HD
            if (isDubbed || isSubbed) {
                addDubStatus(dubExist = isDubbed, subExist = isSubbed)
            }
        }
    }
}

override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)
override suspend fun load(url: String): LoadResponse {
    val loadData = parseJson<LoadData>(url)
    val allShows = loadData.items

    val finalPosterUrl = loadData.poster
    val plot = "TMDB'den özet alınamadı."
    // loadData'dan gelen puanı kullan
    val scoreToUse = loadData.score
      val dubbedEpisodes = mutableListOf<Episode>()
      val subbedEpisodes = mutableListOf<Episode>()
    
    // Bölümleri sezon ve bölüme göre gruplandırıp, aynı bölümün tüm kaynaklarını bir arada tutar.
    val groupedEpisodes = allShows.groupBy {
        val (_, season, episode) = parseEpisodeInfo(it.title.toString())
        Pair(season, episode)
    }
    groupedEpisodes.forEach { (key, episodeItems) ->
        val (season, episode) = key
        val item = episodeItems.first()
        val (itemCleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
        val finalSeason = season ?: 1
        val finalEpisode = episode ?: 1
        val isDubbed = isDubbed(item)
        val isSubbed = isSubbed(item)
        val episodePoster = item.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: finalPosterUrl

        val episodeLoadData = LoadData(
            items = episodeItems, // Tüm kaynakları bu listeye ekle
            title = itemCleanTitle,
            poster = finalPosterUrl,
            group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = item.attributes["tvg-country"] ?: "TR",
            season = finalSeason,
            episode = finalEpisode,
            isDubbed = isDubbed,
            isSubbed = isSubbed,
            score = item.score
        )

        val episodeObj = newEpisode(episodeLoadData.toJson()) {
            this.name = if (season != null && episode != null) {
                "${itemCleanTitle} S$finalSeason E$finalEpisode"
            } else {
                itemCleanTitle
            }
            this.season = finalSeason
            this.episode = finalEpisode
            this.posterUrl = episodePoster
        }

        if (isDubbed) {
            dubbedEpisodes.add(episodeObj)
        } else {
            // Eğer Dublajlı değilse ve Altyazı veya Etiketsiz ise buraya ekle
            subbedEpisodes.add(episodeObj)
        }
    }
      
    dubbedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))
    subbedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))

    val episodesMap = mutableMapOf<DubStatus, List<Episode>>()

    if (dubbedEpisodes.isNotEmpty()) {
        episodesMap[DubStatus.Dubbed] = dubbedEpisodes
    }
    if (subbedEpisodes.isNotEmpty()) {
        episodesMap[DubStatus.Subbed] = subbedEpisodes
    }
    val actorsList = mutableListOf<ActorData>()
    actorsList.add(
        ActorData(
            actor = Actor("MoOnCrOwN","https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"),
            roleString = "yazılım amalesi"
        )
    )
    val tags = mutableListOf<String>()
    tags.add(loadData.group)
    tags.add(loadData.nation)
    // Sadece gerçekten dublajlı veya altyazılı bölüm varsa etiket eklenir.
    if (dubbedEpisodes.isNotEmpty()) {
        tags.add("Türkçe Dublaj")
    }
    if (subbedEpisodes.isNotEmpty()) {
        tags.add("Türkçe Altyazılı")
    }

    val recommendedList = (dubbedEpisodes + subbedEpisodes)
      // .shuffled()
        .take(24)
        .mapNotNull { episode ->
            val episodeLoadData = parseJson<LoadData>(episode.data)
            val episodeTitleWithNumber = if (episodeLoadData.episode > 0) {
                "${episodeLoadData.title} S${episodeLoadData.season} E${episodeLoadData.episode}"
            } else {
                episodeLoadData.title
            }
            
            newAnimeSearchResponse(episodeTitleWithNumber, episode.data).apply {
                posterUrl = episodeLoadData.poster
                type = TvType.Anime
                if (episodeLoadData.isDubbed || episodeLoadData.isSubbed) {
                    addDubStatus(dubExist = episodeLoadData.isDubbed, subExist = episodeLoadData.isSubbed)
                }
            }
        }

    return newAnimeLoadResponse(
        loadData.title,
        url,
        TvType.TvSeries
    ) {
        this.posterUrl = finalPosterUrl
        this.plot = plot
        this.score = scoreToUse?.let { Score.from10(it) }
        this.tags = tags
        this.episodes = episodesMap
        this.recommendations = recommendedList
          this.actors = listOf(
                      ActorData(
                          Actor(loadData.title, finalPosterUrl),
                          roleString = "KANAL İSMİ"
                      )
                  ) + actorsList
        
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val loadData = parseJson<LoadData>(data)
      
    // loadData'nın içindeki tüm kaynakları döngüye al
    loadData.items.forEachIndexed { index, item ->
      
        val linkName = loadData.title + " Kaynak ${index + 1}"
          
        val linkQuality = Qualities.P1080.value  
          
        val videoUrl = item.url.toString()
        val videoType = when {
            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
            videoUrl.endsWith(".mp4", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> ExtractorLinkType.M3U8
        }
          
        val headersMap = mutableMapOf<String, String>()
        
        // M3U'dan gelen tüm başlıkları öncelikli olarak kullan
        headersMap.putAll(item.headers)
        
        // Referer başlığını kontrol et, yoksa tvg-logo veya video url'den oluştur
        if (!headersMap.containsKey("Referer") && !headersMap.containsKey("referer")) {
            val refererUrl = item.attributes["tvg-logo"]?.let {
                it.substringBeforeLast("/")
            } ?: videoUrl.substringBeforeLast("/")
            headersMap["Referer"] = refererUrl
        }

        // User-Agent başlığını kontrol et, yoksa genel bir tarayıcı User-Agent'i kullan
        if (!headersMap.containsKey("User-Agent") && !headersMap.containsKey("user-agent")) {
            headersMap["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }

        // ExtractorLink'i oluştur ve callback'e gönder
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = linkName,
                url = videoUrl,
                type = videoType
            ) {
                quality = linkQuality
                headers = headersMap
            }
        )
    }
    return true
}

private data class ParsedEpisode(
    val item: PlaylistItem,
    val itemCleanTitle: String,
    val season: Int?,
    val episode: Int?
)
}
