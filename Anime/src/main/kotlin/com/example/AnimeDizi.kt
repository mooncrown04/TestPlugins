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

// --- Yardımcı Sınıflar ---
data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
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
                    playlistItems.add(PlaylistItem(title, attributes))
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

// --- Ana Eklenti Sınıfı ---
class AnimeDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    //override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var mainUrl = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    override var name = "35 anime Dizi 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL =
        "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    private var cachedPlaylist: Playlist? = null
    private val CACHE_KEY = "iptv_playlist_cache"

    data class LoadData(
        val items: List<PlaylistItem>,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 1,
        val episode: Int = 0,
       // val dubStatus: DubStatus? = null
      val isDubbed: Boolean,
        val isSubbed: Boolean

   )

    private suspend fun getOrFetchPlaylist(): Playlist {
        if (cachedPlaylist != null) {
            return cachedPlaylist!!
        }
        val cachedJson = sharedPref?.getString(CACHE_KEY, null)
        if (cachedJson != null) {
            Log.d(name, "Playlist verisi önbellekten yükleniyor.")
            cachedPlaylist = parseJson<Playlist>(cachedJson)
            return cachedPlaylist!!
        }
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
            
       
          val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
          val subbedKeywords = listOf("altyazılı", "altyazi")
            
            
            val language = firstShow.attributes["tvg-language"]?.lowercase()

// Dublaj kontrolü:
val isDubbed = dubbedKeywords.any { keyword -> firstShow.title.toString().lowercase().contains(keyword) } || language == "tr" || language == "turkish"|| language == "dublaj"

// Altyazı kontrolü:
val isSubbed = subbedKeywords.any { keyword -> firstShow.title.toString().lowercase().contains(keyword) } || language == "en" || language == "eng"
     //       val languageStatus = if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed

            val loadData = LoadData(
                items = shows,
                title = cleanTitle,
                poster = firstShow.attributes["tvg-logo"] ?: DEFAULT_POSTER_URL,
                group = firstShow.attributes["group-title"] ?: "Bilinmeyen Grup",
                nation = firstShow.attributes["tvg-country"] ?: "TR",
                isDubbed = isDubbed,
                isSubbed = isSubbed
            )

            val searchResponse = newAnimeSearchResponse(cleanTitle, loadData.toJson())
            searchResponse.apply {
                posterUrl = loadData.poster
                type = TvType.Anime
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
            

          //  val dubbedKeywords = listOf("dublaj", "türkçe", "turkish", "tr")
          //  val subbedKeywords = listOf("altyazılı", "altyazi", "en", "eng")
          //  val languageAndTitle = (item.title.toString() + " " + (item.attributes["tvg-language"] ?: "")).lowercase()
          //  val isDubbed = dubbedKeywords.any { languageAndTitle.contains(it) }
          //  val isSubbed = subbedKeywords.any { languageAndTitle.contains(it) } 
          //  val languageStatus = when {
          //      isDubbed -> DubStatus.Dubbed
          //      isSubbed -> DubStatus.Subbed
           //     else -> null
          //  }





               
          val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
val subbedKeywords = listOf("altyazılı", "altyazi")
            
            
            val language = firstShow.attributes["tvg-language"]?.lowercase()

// Dublaj kontrolü:
val isDubbed = dubbedKeywords.any { keyword -> firstShow.title.toString().lowercase().contains(keyword) } || language == "tr" || language == "turkish"|| language == "dublaj"
// Altyazı kontrolü:
val isSubbed = subbedKeywords.any { keyword -> firstShow.title.toString().lowercase().contains(keyword) } || language == "en" || language == "eng"
     //       val languageStatus = if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed


 val loadData = LoadData(
                items = shows,
                title = cleanTitle,
                poster = firstShow.attributes["tvg-logo"] ?: DEFAULT_POSTER_URL,
                group = firstShow.attributes["group-title"] ?: "Bilinmeyen Grup",
                nation = firstShow.attributes["tvg-country"] ?: "TR",
                isDubbed = isDubbed,
                isSubbed = isSubbed
            )
            
            val searchResponse = newAnimeSearchResponse(cleanTitle, loadData.toJson())
            searchResponse.apply {
                posterUrl = loadData.poster
                type = TvType.Anime
              
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

    val dubbedEpisodes = mutableListOf<Episode>()
    val subbedEpisodes = mutableListOf<Episode>()
    val unknownEpisodes = mutableListOf<Episode>()

    val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
    val subbedKeywords = listOf("altyazılı", "altyazi")
        
    allShows.forEach { item ->
        val (itemCleanTitle, season, episode) = parseEpisodeInfo(item.title.toString())
        val finalSeason = season ?: 1
        val finalEpisode = episode ?: 1
        val language = item.attributes["tvg-language"]?.lowercase()
        
        val isDubbed = dubbedKeywords.any { keyword -> item.title.toString().lowercase().contains(keyword) } || language == "tr" || language == "turkish" || language == "dublaj"
        val isSubbed = subbedKeywords.any { keyword -> item.title.toString().lowercase().contains(keyword) } || language == "en" || language == "eng"
        
        val episodePoster = item.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: finalPosterUrl

        val episodeObj = newEpisode(
            LoadData(
                items = listOf(item),
                title = itemCleanTitle,
                poster = finalPosterUrl,
                group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
                nation = item.attributes["tvg-country"] ?: "TR",
                season = finalSeason,
                episode = finalEpisode,
                isDubbed = isDubbed,
                isSubbed = isSubbed
            ).toJson()
        ) {
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
        } else if (isSubbed) {
            subbedEpisodes.add(episodeObj)
        } else {
            unknownEpisodes.add(episodeObj)
        }
    }
    
    dubbedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))
    subbedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))
    unknownEpisodes.sortWith(compareBy({ it.season }, { it.episode }))

    val episodesMap = mutableMapOf<DubStatus, List<Episode>>()

    if (dubbedEpisodes.isNotEmpty()) {
        episodesMap[DubStatus.Dubbed] = dubbedEpisodes
    }
    if (subbedEpisodes.isNotEmpty()) {
        episodesMap[DubStatus.Subbed] = subbedEpisodes
    }

    // Etiketsiz bölümler, eğer varlarsa ve başka etiketli bölüm yoksa, 
    // "Dubbed" veya "Subbed" olarak gösterilmek yerine kendi başlarına listelenir.
    // Cloudstream arayüzünde oynatma tuşu için bir kategoriye ait olmaları gerekir.
    // Bu yüzden en iyi çözüm, tüm bölümleri tek bir liste altında birleştirmektir.
    val combinedEpisodes = mutableListOf<Episode>()
    combinedEpisodes.addAll(dubbedEpisodes)
    combinedEpisodes.addAll(subbedEpisodes)
    combinedEpisodes.addAll(unknownEpisodes)
    combinedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))
    
    episodesMap[DubStatus.Subbed] = combinedEpisodes
    
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

    val recommendedList = (dubbedEpisodes + subbedEpisodes + unknownEpisodes)
        .shuffled()
        .take(10)
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
        this.tags = tags
        this.episodes = episodesMap
        this.recommendations = recommendedList
    }
}

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        loadData.items.forEach { item ->
            val linkQuality = Qualities.Unknown.value
            
            val titleText = loadData.title
            
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = titleText,
                    url = item.url.toString(),
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = linkQuality
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
