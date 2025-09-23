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
import java.net.URL

// --- Ana Eklenti Sınıfı ---
class AnimeDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    // Ana M3U dosyasının URL'si
    override var mainUrl = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    // Eklenti adı
    override var name = "35 mooncrown always 00444714568 "
    // Ana sayfa destekleniyor mu?
    override val hasMainPage = true
    // Dil ayarı
    override var lang = "tr"
    // Hızlı arama destekleniyor mu?
    override val hasQuickSearch = true
    // İndirme destekleniyor mu?
    override val hasDownloadSupport = true
    // Desteklenen içerik türleri
    override val supportedTypes = setOf(TvType.TvSeries)

    // Poster URL'si bulunamazsa kullanılacak varsayılan resim
    private val DEFAULT_POSTER_URL =
        "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    // Playlist'i bellekte tutmak için değişken
    private var cachedPlaylist: Playlist? = null
    // SharedPref için cache anahtarı
    private val CACHE_KEY = "iptv_playlist_cache"


    /**
     * Verilen URL'nin geçerli olup olmadığını HEAD isteği ile kontrol eder.
     * Geçerli değilse veya hata oluşursa null döner.
     */
    private suspend fun checkPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        return try {
            // Sadece başlık bilgisini istediğimiz için HEAD isteği kullanıyoruz.
            val response = app.head(url)
            if (response.isSuccessful) {
                // İstek başarılıysa URL geçerlidir
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
        // Dosya başlığını kontrol et, değilse hata fırlat
        if (!reader.readLine().isExtendedM3u()) throw PlaylistParserException.InvalidHeader()

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var line: String? = reader.readLine()
        var currentItem: PlaylistItem? = null

        while (line != null) {
            if (line.isNotEmpty()) {
                when {
                    line.startsWith(PlaylistItem.EXT_INF) -> {
                        currentItem = PlaylistItem(
                            title = line.getTitle(),
                            attributes = line.getAttributes(),
                            score = line.getAttributes()["tvg-score"]?.toDoubleOrNull()
                        )
                    }
                    line.startsWith(PlaylistItem.EXT_VLC_OPT) -> {
                        if (currentItem != null) {
                            val userAgent = line.getVlcOptUserAgent()
                            currentItem = currentItem.copy(userAgent = userAgent)
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
    private fun String.getVlcOptUserAgent(): String? =
        substringAfter("http-user-agent=").trim()
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

/**
 * Bölüm bilgisini başlık metninden ayrıştırır.
 * Düzenli ifade (regex) desenleri daha spesifik olandan daha genel olana doğru sıralanmıştır.
 * Bu, yanlış eşleşmeleri en aza indirmeye yardımcı olur.
 */
fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    // Unicode karakterleri temizle
    val textWithCleanedChars = text.replace(Regex("[\\u200E\\u200F]"), "")

    // Regex desenleri - En spesifik olandan en genel olana doğru
    val regexPatterns = listOf(
        // Desene örnek: "Başlık 1. Sezon 2. Bölüm"
        Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE),
        // Desene örnek: "Başlık S1E2" veya "Başlık s1e2"
        Regex("""(.*?)\s*[Ss](\d+)[Ee](\d+).*""", RegexOption.IGNORE_CASE),
        // Desene örnek: "Başlık Sezon 1 Bölüm 2"
        Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""", RegexOption.IGNORE_CASE),
        // Desene örnek: "Başlık 5. Sezon"
        Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon.*""", RegexOption.IGNORE_CASE),
        // Desene örnek: "Başlık 2. Bölüm" veya "Başlık 2 Bölüm"
        Regex("""(.*?)[^\w\d]+(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE),
        // Desene örnek: "Başlık 2"
        Regex("""(.*?)\s*(\d+)$""", RegexOption.IGNORE_CASE)
    )

    for (regex in regexPatterns) {
        val matchResult = regex.find(textWithCleanedChars)
        if (matchResult != null) {
            val (title, season, episode) = when (matchResult.groups.size) {
                // Sadece başlık ve bölüm
                3 -> Triple(matchResult.groups[1]?.value, null, matchResult.groups[2]?.value)
                // Başlık, sezon ve bölüm
                4 -> Triple(matchResult.groups[1]?.value, matchResult.groups[2]?.value, matchResult.groups[3]?.value)
                else -> Triple(null, null, null)
            }
            if (title != null) {
                // Log.d("parseEpisodeInfo", "Metin: '$text' -> Başlık: '${title.trim()}', Sezon: ${season?.toIntOrNull()}, Bölüm: ${episode?.toIntOrNull()}")
                return Triple(title.trim(), season?.toIntOrNull(), episode?.toIntOrNull())
            }
        }
    }

    // Hiçbir desen eşleşmezse, sadece başlığı döndür
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

/**
 * Playlist verisini bellekteki önbellekten alır veya ağdan indirir.
 * Ağdan indirilen veri, bir sonraki kullanım için önbelleğe alınır.
 */
private suspend fun getOrFetchPlaylist(): Playlist {
    Log.d(name, "Playlist verisi ağdan indiriliyor.")
    val content = app.get(mainUrl).text
    val newPlaylist = IptvPlaylistParser().parseM3U(content)
    cachedPlaylist = newPlaylist
    sharedPref?.edit()?.putString(CACHE_KEY, newPlaylist.toJson())?.apply()
    return newPlaylist
}

/**
 * Başlık veya dildeki anahtar kelimelerle Dublaj durumunu kontrol eder.
 */
private fun isDubbed(item: PlaylistItem): Boolean {
    val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
    val language = item.attributes["tvg-language"]?.lowercase(Locale.getDefault()) ?: ""
    val title = item.title.toString().lowercase(Locale.getDefault())

    return dubbedKeywords.any { title.contains(it) } || language == "tr" || language == "turkish" || language == "dublaj"
}

/**
 * Başlık veya dildeki anahtar kelimelerle Altyazı durumunu kontrol eder.
 */
private fun isSubbed(item: PlaylistItem): Boolean {
    val subbedKeywords = listOf("altyazılı", "altyazi")
    val language = item.attributes["tvg-language"]?.lowercase(Locale.getDefault()) ?: ""
    val title = item.title.toString().lowercase(Locale.getDefault())

    return subbedKeywords.any { title.contains(it) } || language == "en" || language == "eng"
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
    val fullAlphabet = turkishAlphabet + listOf("Q", "W", "X")

    val allGroupsToProcess = mutableListOf<String>()
    if (alphabeticGroups.containsKey("0-9")) allGroupsToProcess.add("0-9")
    fullAlphabet.forEach { char ->
        if (alphabeticGroups.containsKey(char)) {
            allGroupsToProcess.add(char)
        }
    }
    if (alphabeticGroups.containsKey("#")) allGroupsToProcess.add("#")

    allGroupsToProcess.forEach { char ->
        val shows = alphabeticGroups[char]
        if (shows != null && shows.isNotEmpty()) {

            // Liste elemanlarını 3 kez çoğaltarak sonsuz döngü hissi yaratabilir,
            // ancak şimdilik orijinal listeyi kullanmak daha iyi.
            // val infiniteList = shows + shows + shows
            val listTitle = when (char) {
                "0-9" -> "🔢 0-9"
                "#" -> "🔣 #"
                else -> "🎬 $char"
            }
            finalHomePageLists.add(HomePageList(listTitle, shows, isHorizontalImages = true))
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
          .shuffled() // Önerileri karıştırarak farklı içerikler göster
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
        
        val videoUrl = item.url.toString()
        val headersMap = mutableMapOf<String, String>()

        // Video URL'sinden domaini alıp Referer olarak ayarla
        val refererUrl = try {
            val urlObject = URL(videoUrl)
            "${urlObject.protocol}://${urlObject.host}"
        } catch (e: Exception) {
            mainUrl // Hata durumunda varsayılan Referer'ı kullan
        }
        
        headersMap["Referer"] = refererUrl
        
        // Ortak bir masaüstü tarayıcı User-Agent'i ekle
        headersMap["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

        // Eğer PlaylistItem'de özel bir User-Agent varsa, onu kullan
        item.userAgent?.let {
            headersMap["User-Agent"] = it
        }

        val linkName = loadData.title + " Kaynak ${index + 1}"
        val linkQuality = Qualities.P1080.value  
        
        val videoType = when {
            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
            videoUrl.endsWith(".mp4", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> ExtractorLinkType.M3U8
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
