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
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.ui.settings.SettingsFragment
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.add
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.textSetting
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.singleSelectionSettings
import android.content.Context

class AnimeDizi(val plugin: CloudstreamPlugin) : MainAPI() {
    private val DEFAULT_M3U_URL = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    private val DEFAULT_NAME = "35 Anime Diziler 🎬"
    private val CACHE_KEY = "iptv_playlist_cache"
    
    private val sharedPreferences: SharedPreferences?
        get() = plugin.context?.getSharedPreferences("com.lagradost.cloudstream3.app_settings", 0)

    override var mainUrl: String
        get() = sharedPreferences?.getString("m3u_url_key", DEFAULT_M3U_URL) ?: DEFAULT_M3U_URL
        set(value) {}
        
    override var name: String
        get() = sharedPreferences?.getString("plugin_name_key", DEFAULT_NAME) ?: DEFAULT_NAME
        set(value) {}

    override val hasMainPage: Boolean
        get() = sharedPreferences?.getString("layout_preference_key", "Yatay") == "Yatay"
        
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL =
        "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    private var cachedPlaylist: Playlist? = null

    private suspend fun checkPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        return suspendSafeApiCall {
            val response = app.head(url)
            if (response.isSuccessful) {
                url
            } else {
                Log.e(name, "Resim URL'si geçersiz: $url, Hata Kodu: ${response.code}")
                null
            }
        } ?: null
    }

    override fun load(context: Context) {
        CloudstreamPlugin.addSettings(this) {
            this.add(
                this.textSetting(
                    name = "Eklenti Adı",
                    defaultValue = DEFAULT_NAME,
                    key = "plugin_name_key",
                    description = "Eklentinin görünen adını değiştirin."
                )
            )

            this.add(
                this.textSetting(
                    name = "M3U URL",
                    defaultValue = DEFAULT_M3U_URL,
                    key = "m3u_url_key",
                    description = "Özel bir M3U listesi URL'si girin."
                )
            )

            val layoutOptions = listOf("Yatay", "Dikey")
            this.add(
                this.singleSelectionSettings(
                    name = "Liste Düzeni",
                    defaultValue = 0,
                    entries = layoutOptions,
                    key = "layout_preference_key"
                )
            )
        }
    }

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
        val format4Regex = Regex("""(.*?)\s*(\d+)\s*Bölüm.*""", RegexOption.IGNORE_CASE)

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
        return try {
            Log.d(name, "Playlist verisi ağdan indiriliyor.")
            val content = app.get(mainUrl).text
            val newPlaylist = IptvPlaylistParser().parseM3U(content)
            cachedPlaylist = newPlaylist
            sharedPreferences?.edit()?.putString(CACHE_KEY, newPlaylist.toJson())?.apply()
            newPlaylist
        } catch (e: Exception) {
            Log.e("AnimeDizi", "Playlist verisi indirilirken veya işlenirken bir hata oluştu.", e)
            cachedPlaylist ?: Playlist(emptyList())
        }
    }

    private fun isDubbed(item: PlaylistItem): Boolean {
        val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
        val language = item.attributes["tvg-language"]?.lowercase()
        return dubbedKeywords.any { keyword -> item.title.toString().lowercase().contains(keyword) } || language == "tr" || language == "turkish" || language == "dublaj" || language == "TÜRKÇE"
    }

    private fun isSubbed(item: PlaylistItem): Boolean {
        val subbedKeywords = listOf("altyazılı", "altyazi")
        val language = item.attributes["tvg-language"]?.lowercase()
        return subbedKeywords.any { keyword -> item.title.toString().lowercase().contains(keyword) } || language == "en" || language == "eng"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = getOrFetchPlaylist()
        val isHorizontal = sharedPreferences?.getString("layout_preference_key", "Yatay") == "Yatay"
        val groupedByCleanTitle = kanallar.items.groupBy {
            val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
            cleanTitle
        }

        val finalHomePageLists = mutableListOf<HomePageList>()
        val turkishAlphabet = "ABCÇDEFGĞHIİİJKLMNOÖPRSŞTUVYZ".split("").filter { it.isNotBlank() }
        val fullAlphabet = listOf("0-9") + turkishAlphabet + listOf("Q", "W", "X", "#")

        val alphabeticGroups = groupedByCleanTitle.mapNotNull { (cleanTitle, shows) ->
            val firstShow = shows.firstOrNull() ?: return@mapNotNull null
            val firstChar = cleanTitle.firstOrNull()?.uppercaseChar()
            val groupKey = when {
                firstChar?.isDigit() == true -> "0-9"
                firstChar == null || !firstChar.isLetterOrDigit() -> "#"
                else -> firstChar.toString()
            }
            val rawPosterUrl = firstShow.attributes["tvg-logo"]
            val verifiedPosterUrl = checkPosterUrl(rawPosterUrl)
            val finalPosterUrl = verifiedPosterUrl ?: DEFAULT_POSTER_URL
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
            val searchResponse = newAnimeSearchResponse(cleanTitle, loadData.toJson()).apply {
                posterUrl = loadData.poster
                type = TvType.Anime
                this.score = score?.let { Score.from10(it) }
                this.quality = SearchQuality.HD
                addDubStatus(dubExist = isDubbed, subExist = isSubbed)
            }
            Pair(groupKey, searchResponse)
        }.groupBy { it.first }.mapValues { it.value.map { it.second } }.toSortedMap()

        fullAlphabet.forEach { char ->
            val shows = alphabeticGroups[char]
            if (shows != null && shows.isNotEmpty()) {
                val infiniteList = shows + shows + shows
                val listTitle = when (char) {
                    "0-9" -> "🔢 0-9"
                    "#" -> "🔣 #"
                    else -> {
                        val currentIndex = fullAlphabet.indexOf(char)
                        val remainingAlphabet = if (currentIndex != -1) {
                            fullAlphabet.subList(currentIndex, fullAlphabet.size)
                        } else {
                            emptyList()
                        }
                        val remainingString = remainingAlphabet.joinToString(" ") { it.lowercase() }
                        "🎬 $remainingString"
                    }
                }
                finalHomePageLists.add(HomePageList(listTitle, infiniteList, isHorizontalImages = isHorizontal))
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
            val rawPosterUrl = firstShow.attributes["tvg-logo"]
            val verifiedPosterUrl = checkPosterUrl(rawPosterUrl)
            val finalPosterUrl = verifiedPosterUrl ?: DEFAULT_POSTER_URL
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
        val scoreToUse = loadData.score
        val dubbedEpisodes = mutableListOf<Episode>()
        val subbedEpisodes = mutableListOf<Episode>()
        val groupedEpisodesByTitle = allShows.groupBy {
            val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
            cleanTitle
        }
        groupedEpisodesByTitle.forEach { (title, episodes) ->
            val groupedBySeasonAndEpisode = episodes.groupBy {
                val (_, season, episode) = parseEpisodeInfo(it.title.toString())
                Pair(season ?: 1, episode ?: 1)
            }
            groupedBySeasonAndEpisode.forEach { (key, episodeItems) ->
                val (season, episode) = key
                val item = episodeItems.first()
                val (itemCleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
                val finalSeason = season
                val finalEpisode = episode
                val isDubbed = isDubbed(item)
                val isSubbed = isSubbed(item)
                val episodePoster = item.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: finalPosterUrl
                val episodeLoadData = LoadData(
                    items = episodeItems,
                    title = itemCleanTitle,
                    poster = finalPosterUrl,
                    group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
                    nation = item.attributes["tvg-country"] ?: "TR",
                    isDubbed = isDubbed,
                    isSubbed = isSubbed,
                    score = item.score
                )
                val episodeObj = newEpisode(episodeLoadData.toJson()) {
                    this.name = if (season > 0 && episode > 0) {
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
                    subbedEpisodes.add(episodeObj)
                }
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
        if (dubbedEpisodes.isNotEmpty()) {
            tags.add("Türkçe Dublaj")
        }
        if (subbedEpisodes.isNotEmpty()) {
            tags.add("Türkçe Altyazılı")
        }
        val recommendedList = (dubbedEpisodes + subbedEpisodes)
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
        loadData.items.forEachIndexed { index, item ->
            val linkName = loadData.title + "Kaynak ${index + 1}"
            val linkQuality = Qualities.P1080.value 
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = linkName,
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
