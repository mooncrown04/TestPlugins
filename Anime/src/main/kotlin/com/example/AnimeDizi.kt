package com.example

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


// --- Ana Eklenti Sınıfı ---
class AnimeDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    override var name = "35 Anime Dizi son 🎬"
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
        var currentIndex = 0
        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(PlaylistItem.EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    
                    val score = attributes["tvg-score"]?.toDoubleOrNull()
                    playlistItems.add(PlaylistItem(title, attributes, score = score))
                } else if (!line.startsWith("#")) {
                    val item = playlistItems.getOrNull(currentIndex)
                    if (item != null) {
                        val url = line.getUrl()
                        playlistItems[currentIndex] = item.copy(url = url)
                        currentIndex++
                    } else {
                        Log.w("IptvPlaylistParser", "URL eşleşmedi, atlanıyor: $line")
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
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    val textWithCleanedChars = text.replace(Regex("[\\u200E\\u200F]"), "")
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""", RegexOption.IGNORE_CASE)

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
        
        // Dublaj ve Altyazı kontrolü
        val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
        val subbedKeywords = listOf("altyazılı", "altyazi", "sub")
        val language = firstShow.attributes["tvg-language"]?.lowercase()

        val isDubbed = dubbedKeywords.any { keyword -> firstShow.title.toString().lowercase(Locale.getDefault()).contains(keyword) } || language == "tr" || language == "turkish" || language == "dublaj"
        val isSubbed = subbedKeywords.any { keyword -> firstShow.title.toString().lowercase(Locale.getDefault()).contains(keyword) } || language == "en" || language == "eng" || language == "altyazılı"

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

        val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
        val subbedKeywords = listOf("altyazılı", "altyazi", "sub")
        val language = firstShow.attributes["tvg-language"]?.lowercase()

        val isDubbed = dubbedKeywords.any { keyword -> firstShow.title.toString().lowercase(Locale.getDefault()).contains(keyword) } || language == "tr" || language == "turkish" || language == "dublaj"
        val isSubbed = subbedKeywords.any { keyword -> firstShow.title.toString().lowercase(Locale.getDefault()).contains(keyword) } || language == "en" || language == "eng" || language == "altyazılı"

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
    val scoreToUse = loadData.score

    val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
    val subbedKeywords = listOf("altyazılı", "altyazi", "sub")
    
    // Enum'a yeni değerleri ekleyin
    enum class DubStatus {
        Dubbed, Subbed, Both, Other
    }


    val seasonsByDubStatus = mutableMapOf<DubStatus, MutableMap<Int, MutableList<PlaylistItem>>>()
    
    allShows.forEach { item ->
        val (itemCleanTitle, season, episode) = parseEpisodeInfo(item.title.toString())
        val finalSeason = season ?: 1
        
        val isDubbed = dubbedKeywords.any { item.title.toString().lowercase(Locale.getDefault()).contains(it) } || item.attributes["tvg-language"]?.lowercase() == "tr"
        val isSubbed = subbedKeywords.any { item.title.toString().lowercase(Locale.getDefault()).contains(it) } || item.attributes["tvg-language"]?.lowercase() == "en"

        val dubStatus = when {
            isDubbed && isSubbed -> DubStatus.Both
            isDubbed -> DubStatus.Dubbed
            isSubbed -> DubStatus.Subbed
            else -> DubStatus.Other
        }
        
        seasonsByDubStatus.getOrPut(dubStatus) { mutableMapOf() }
                          .getOrPut(finalSeason) { mutableListOf() }
                          .add(item)
    }

    val episodesByDubStatus = mutableMapOf<DubStatus, MutableList<Episode>>()

    seasonsByDubStatus.forEach { (status, seasons) ->
        val allSeasonEpisodes = mutableListOf<Episode>()
        seasons.keys.sorted().forEach { season ->
            val itemsForSeason = seasons[season]
            
            // Eğer sezonda gerçek bölümler varsa
            val validEpisodes = itemsForSeason?.filter { parseEpisodeInfo(it.title.toString()).third != null }
            if (!validEpisodes.isNullOrEmpty()) {
                // Sadece birden fazla bölümü olan sezonlar için başlık ekle
                if (seasons.size > 1 || season > 1) {
                    allSeasonEpisodes.add(newEpisode("") {
                        name = "Sezon $season"
                        this.season = season
                        this.episode = 0
                    })
                }
                
                // Bölümleri oluştur ve listeye ekle
                val sortedItems = validEpisodes.distinctBy { it.url }.sortedBy {
                    parseEpisodeInfo(it.title.toString()).third ?: 0
                }
                
                sortedItems.forEach { item ->
                    val (itemCleanTitle, _, episode) = parseEpisodeInfo(item.title.toString())
                    
                    val episodeLoadData = LoadData(
                        items = listOf(item),
                        title = itemCleanTitle,
                        poster = item.attributes["tvg-logo"] ?: finalPosterUrl,
                        group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
                        nation = item.attributes["tvg-country"] ?: "TR",
                        season = season,
                        episode = episode ?: 0,
                        isDubbed = status == DubStatus.Dubbed || status == DubStatus.Both,
                        isSubbed = status == DubStatus.Subbed || status == DubStatus.Both,
                        score = item.score
                    )
                    
                    val episodeObj = newEpisode(episodeLoadData.toJson()) {
                        name = "S${season} E${episode}"
                        this.season = season
                        this.episode = episode ?: 0
                        this.posterUrl = item.attributes["tvg-logo"] ?: finalPosterUrl
                    }
                    allSeasonEpisodes.add(episodeObj)
                }
            } else if (itemsForSeason != null && itemsForSeason.size == 1) {
                 // Sadece bir bölüm varsa ve numarası yoksa, direkt ekle
                val singleItem = itemsForSeason.first()
                val (itemCleanTitle, _, _) = parseEpisodeInfo(singleItem.title.toString())
                val episodeLoadData = LoadData(
                    items = listOf(singleItem),
                    title = itemCleanTitle,
                    poster = singleItem.attributes["tvg-logo"] ?: finalPosterUrl,
                    group = singleItem.attributes["group-title"] ?: "Bilinmeyen Grup",
                    nation = singleItem.attributes["tvg-country"] ?: "TR",
                    season = 1,
                    episode = 0,
                    isDubbed = status == DubStatus.Dubbed || status == DubStatus.Both,
                    isSubbed = status == DubStatus.Subbed || status == DubStatus.Both,
                    score = singleItem.score
                )
                
                allSeasonEpisodes.add(newEpisode(episodeLoadData.toJson()) {
                    name = itemCleanTitle
                    this.season = 1
                    this.episode = 0
                    this.posterUrl = singleItem.attributes["tvg-logo"] ?: finalPosterUrl
                })
            }
        }
        
        if (allSeasonEpisodes.isNotEmpty()) {
            episodesByDubStatus.getOrPut(status) { mutableListOf() }.addAll(allSeasonEpisodes)
        }
    }


    val actorsList = mutableListOf<ActorData>()
    actorsList.add(
        ActorData(
            actor = Actor("MoOnCrOwN","https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg")
        )
    )

    val tags = mutableListOf<String>()
    tags.add(loadData.group)
    tags.add(loadData.nation)
    if (episodesByDubStatus.containsKey(DubStatus.Dubbed)) {
        tags.add("Türkçe Dublaj")
    }
    if (episodesByDubStatus.containsKey(DubStatus.Subbed)) {
        tags.add("Türkçe Altyazılı")
    }
    if (episodesByDubStatus.containsKey(DubStatus.Both)) {
        tags.add("Hem Dublaj Hem Altyazı")
    }
    if (episodesByDubStatus.containsKey(DubStatus.Other)) {
        tags.add("Diğer")
    }

    val allEpisodes = (episodesByDubStatus[DubStatus.Dubbed].orEmpty() + episodesByDubStatus[DubStatus.Subbed].orEmpty() + episodesByDubStatus[DubStatus.Both].orEmpty() + episodesByDubStatus[DubStatus.Other].orEmpty())
        .filter { it.data.isNotBlank() }
        .distinctBy { it.data }
        .shuffled()
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
        this.episodes = episodesByDubStatus.mapValues { it.value.toList() } as MutableMap<DubStatus, List<Episode>>
        this.recommendations = allEpisodes
        val actor = Actor(loadData.title, finalPosterUrl)
        this.actors = listOf(
            ActorData(actor, null)
        ) + actorsList
    }
}
