package com.mooncrown

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.addDubStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

// --------------------------
// CONFIG
// --------------------------
private const val TMDB_API_KEY = "4032c1fd53e1b6fef5af1b406fccaa72" // <- Buraya TMDB API anahtarını ekle (örn. "abcd1234...")

// --------------------------
// Yardımcı data / sınıflar
// --------------------------
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

class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())

    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        val firstLine = reader.readLine()
        if (firstLine == null || !firstLine.isExtendedM3u()) throw PlaylistParserException.InvalidHeader()

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
        val attributesString = substringAfter(PlaylistItem.EXT_INF).substringAfter(":").trim()
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
    private fun String.getVlcOptUserAgent(): String? = substringAfter("http-user-agent=").trim().takeIf { it.isNotEmpty() }
}

// --------------------------
// Episode title parser
// --------------------------
fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    val textWithCleanedChars = text.replace(Regex("[\\u200E\\u200F]"), "")
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""", RegexOption.IGNORE_CASE)
    val format4Regex = Regex("""(.*?)\s*(\d+)\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format5Regex = Regex("""(.*?)\s*S(\d+)E(\d+).*""", RegexOption.IGNORE_CASE)

    format1Regex.find(textWithCleanedChars)?.let {
        val (title, seasonStr, episodeStr) = it.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }
    format2Regex.find(textWithCleanedChars)?.let {
        val (title, seasonStr, episodeStr) = it.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }
    format3Regex.find(textWithCleanedChars)?.let {
        val (title, seasonStr, episodeStr) = it.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }
    format4Regex.find(textWithCleanedChars)?.let {
        val (title, episodeStr) = it.destructured
        return Triple(title.trim(), 1, episodeStr.toIntOrNull())
    }
    format5Regex.find(textWithCleanedChars)?.let {
        val (title, seasonStr, episodeStr) = it.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }
    return Triple(textWithCleanedChars.trim(), null, null)
}

// --------------------------
// LoadData (JSON içinde kullanılıyor)
// --------------------------
data class LoadData(
    val items: List<PlaylistItem>,
    val title: String,
    val poster: String?,
    val group: String,
    val nation: String,
    val season: Int = 1,
    val episode: Int = 0,
    val isDubbed: Boolean,
    val isSubbed: Boolean,
    val score: Double? = null,
    val videoFormats: Set<String> = emptySet()
)

// --------------------------
// Ana sınıf
// --------------------------
class MoOnCrOwNAlways : MainAPI() {
    override var mainUrl =
        "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    override var name = "35 mooncrown always FULL"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)

    private val DEFAULT_POSTER_URL =
        "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    private var cachedPlaylist: Playlist? = null
    private val CACHE_KEY = "iptv_playlist_cache"

    private val prefs by lazy {
        app.getSharedPreferences("mooncrown_cache", Context.MODE_PRIVATE)
    }

    // --------------------------
    // network helpers
    // --------------------------
    private suspend fun checkPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val response = app.head(url)
            if (response.isSuccessful) url else null
        } catch (e: Exception) {
            Log.w(name, "checkPosterUrl error: ${e.message}")
            null
        }
    }

    private suspend fun checkContentType(url: String?, headers: Map<String, String>): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val response = withContext(Dispatchers.IO) {
                app.head(url, headers = headers)
            }
            if (!response.isSuccessful) return null
            val contentType = response.headers["Content-Type"]?.lowercase(Locale.getDefault())
            when {
                contentType?.contains("video/mp4") == true -> "mp4"
                contentType?.contains("video/x-matroska") == true -> "mkv"
                contentType?.contains("video/mkv") == true -> "mkv"
                contentType?.contains("application/vnd.apple.mpegurl") == true ||
                        contentType?.contains("application/x-mpegurl") == true -> "m3u8"
                contentType?.startsWith("text/") == true || contentType?.contains("json") == true -> "m3u8"
                else -> null
            }
        } catch (e: Exception) {
            Log.w(name, "checkContentType error: ${e.message}")
            null
        }
    }

    // --------------------------
    // Playlist fetch / cache
    // --------------------------
    private suspend fun getOrFetchPlaylist(): Playlist {
        // memory cache
        cachedPlaylist?.let { return it }

        // prefs cache
        prefs.getString(CACHE_KEY, null)?.let { json ->
            try {
                val p = parseJson<Playlist>(json)
                if (p != null) {
                    cachedPlaylist = p
                    return p
                }
            } catch (e: Exception) {
                Log.w(name, "cache parse error: ${e.message}")
            }
        }

        // fetch
        Log.d(name, "Downloading playlist from $mainUrl")
        val content = app.get(mainUrl).text
        val newPlaylist = IptvPlaylistParser().parseM3U(content)
        cachedPlaylist = newPlaylist
        try {
            prefs.edit().putString(CACHE_KEY, newPlaylist.toJson()).apply()
        } catch (e: Exception) {
            Log.w(name, "cache write error: ${e.message}")
        }
        return newPlaylist
    }

    // --------------------------
    // dub/sub helpers
    // --------------------------
    private fun itemIsDubbed(item: PlaylistItem): Boolean {
        val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
        val language = item.attributes["tvg-language"]?.lowercase(Locale.getDefault())
        val titleLower = (item.title ?: "").lowercase(Locale.getDefault())
        return dubbedKeywords.any { titleLower.contains(it) } || language?.contains("tr") == true || language?.contains("dublaj") == true
    }

    private fun itemIsSubbed(item: PlaylistItem): Boolean {
        val subbedKeywords = listOf("altyazılı", "altyazi")
        val language = item.attributes["tvg-language"]?.lowercase(Locale.getDefault())
        val titleLower = (item.title ?: "").lowercase(Locale.getDefault())
        return subbedKeywords.any { titleLower.contains(it) } || language?.contains("eng") == true || language?.contains("en") == true
    }

    // --------------------------
    // SearchResponse builder (shared)
    // --------------------------
    private suspend fun createSearchResponse(cleanTitle: String, shows: List<PlaylistItem>): SearchResponse? {
        val firstShow = shows.firstOrNull() ?: return null
        val rawPoster = firstShow.attributes["tvg-logo"]
        val verifiedPoster = checkPosterUrl(rawPoster)
        val poster = verifiedPoster ?: DEFAULT_POSTER_URL
        val score = shows.mapNotNull { it.score }.maxOrNull()
        val isDub = itemIsDubbed(firstShow)
        val isSub = itemIsSubbed(firstShow)

        val videoFormats = shows.mapNotNull { it.url?.let { u ->
            when {
                u.endsWith(".mkv", true) -> "MKV"
                u.endsWith(".mp4", true) -> "MP4"
                else -> "M3U8"
            }
        } }.toSet()

        val loadData = LoadData(
            items = shows,
            title = cleanTitle,
            poster = poster,
            group = firstShow.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = firstShow.attributes["tvg-country"] ?: "TR",
            isDubbed = isDub,
            isSubbed = isSub,
            score = score,
            videoFormats = videoFormats
        )

        return newAnimeSearchResponse(cleanTitle, loadData.toJson()).apply {
            posterUrl = loadData.poster
            type = TvType.Anime
            this.score = score?.let { Score.from10(it) }
            val qualityString = firstShow.attributes["tvg-quality"]
            this.quality = when (qualityString) {
                "P360", "P480" -> SearchQuality.SD
                "P720", "P1080" -> SearchQuality.HD
                "P2160" -> SearchQuality.UHD
                else -> null
            }
            if (isDub || isSub) addDubStatus(dubExist = isDub, subExist = isSub)
        }
    }

    // --------------------------
    // MainPage / Search
    // --------------------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlist = getOrFetchPlaylist()
        val grouped = playlist.items.groupBy {
            val (cleanTitle, _, _) = parseEpisodeInfo(it.title ?: "")
            cleanTitle
        }

        val alphabeticGroups = grouped.toSortedMap().mapNotNull { (cleanTitle, shows) ->
            val sr = createSearchResponse(cleanTitle, shows) ?: return@mapNotNull null
            val firstChar = cleanTitle.firstOrNull()?.uppercaseChar() ?: '#'
            val groupKey = when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#"
            }
            Pair(groupKey, sr)
        }.groupBy { it.first }.mapValues { it.value.map { it.second } }.toSortedMap()

        val lists = mutableListOf<HomePageList>()
        val turkishAlphabet = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUVYZ".split("").filter { it.isNotBlank() }
        val fullAlphabet = turkishAlphabet + listOf("Q", "W", "X")
        val allGroups = mutableListOf<String>()
        if (alphabeticGroups.containsKey("0-9")) allGroups.add("0-9")
        fullAlphabet.forEach { if (alphabeticGroups.containsKey(it)) allGroups.add(it) }
        if (alphabeticGroups.containsKey("#")) allGroups.add("#")

        allGroups.forEach { char ->
            val shows = alphabeticGroups[char]
            if (!shows.isNullOrEmpty()) {
                val listTitle = when (char) {
                    "0-9" -> "🔢 0-9 ${fullAlphabet.joinToString(" ") { it.lowercase(Locale.getDefault()) }}"
                    "#" -> "🔣 # ${fullAlphabet.joinToString(" ") { it.lowercase(Locale.getDefault()) }}"
                    else -> "🎬 $char"
                }
                lists.add(HomePageList(listTitle, shows, isHorizontalImages = true))
            }
        }

        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val playlist = getOrFetchPlaylist()
        val grouped = playlist.items.groupBy {
            val (cleanTitle, _, _) = parseEpisodeInfo(it.title ?: "")
            cleanTitle
        }
        return grouped.filter { (cleanTitle, _) ->
            cleanTitle.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
        }.mapNotNull { (cleanTitle, shows) -> createSearchResponse(cleanTitle, shows) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // --------------------------
    // TMDB helpers
    // --------------------------
    private suspend fun fetchTMDBData(title: String): Triple<JSONObject?, TvType, Int?> = withContext(Dispatchers.IO) {
        try {
            if (TMDB_API_KEY.isEmpty()) return@withContext Triple(null, TvType.TvSeries, null)
            val encoded = URLEncoder.encode(title.replace(Regex("\\([^)]*\\)"), "").trim(), "UTF-8")
            val searchTvUrl = "https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&query=$encoded&language=tr-TR"
            val tvResponse = JSONObject(URL(searchTvUrl).readText())
            val tvResults = tvResponse.optJSONArray("results")

            val searchMovieUrl = "https://api.themoviedb.org/3/search/movie?api_key=$TMDB_API_KEY&query=$encoded&language=tr-TR"
            val movieResponse = JSONObject(URL(searchMovieUrl).readText())
            val movieResults = movieResponse.optJSONArray("results")

            if (tvResults != null && tvResults.length() > 0) {
                val tvId = tvResults.optJSONObject(0)?.optInt("id")
                if (tvId != null) {
                    val detailsUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$TMDB_API_KEY&append_to_response=credits&language=tr-TR"
                    val details = JSONObject(URL(detailsUrl).readText())
                    return@withContext Triple(details, TvType.TvSeries, tvId)
                }
            }

            if (movieResults != null && movieResults.length() > 0) {
                val movieId = movieResults.optJSONObject(0)?.optInt("id")
                if (movieId != null) {
                    val detailsUrl = "https://api.themoviedb.org/3/movie/$movieId?api_key=$TMDB_API_KEY&append_to_response=credits&language=tr-TR"
                    val details = JSONObject(URL(detailsUrl).readText())
                    return@withContext Triple(details, TvType.Movie, movieId)
                }
            }

            Triple(null, TvType.TvSeries, null)
        } catch (e: Exception) {
            Log.w(name, "fetchTMDBData error: ${e.message}")
            Triple(null, TvType.TvSeries, null)
        }
    }

    private suspend fun fetchEpisodeData(tvId: Int, seasonNum: Int, episodeNum: Int): JSONObject? = withContext(Dispatchers.IO) {
        try {
            if (TMDB_API_KEY.isEmpty()) return@withContext null
            val url = "https://api.themoviedb.org/3/tv/$tvId/season/$seasonNum/episode/$episodeNum?api_key=$TMDB_API_KEY&language=tr-TR"
            return@withContext JSONObject(URL(url).readText())
        } catch (e: Exception) {
            Log.w(name, "fetchEpisodeData error: ${e.message}")
            null
        }
    }

    // --------------------------
    // Load (detay) - bölümleri oluşturur
    // --------------------------
    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val (tmdbData, tmdbType, tmdbId) = fetchTMDBData(loadData.title)
        val tmdbPosterPath = tmdbData?.optString("poster_path")
        val tmdbPosterUrl = if (tmdbPosterPath.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/w780/$tmdbPosterPath"
        val finalPosterUrl = tmdbPosterUrl ?: loadData.poster ?: DEFAULT_POSTER_URL

        val plot = buildString {
            if (tmdbData != null) {
                val overview = tmdbData.optString("overview", "")
                val releaseDate = if (tmdbType == TvType.Movie) tmdbData.optString("release_date", "") else tmdbData.optString("first_air_date", "")
                val ratingValue = tmdbData.optDouble("vote_average", -1.0)
                val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                val tagline = tmdbData.optString("tagline", "")
                if (tagline.isNotEmpty()) append("💭 <b>Slogan:</b><br>$tagline<br><br>")
                if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>$overview<br><br>")
                if (releaseDate.isNotEmpty()) append("📅 <b>Yapım Yılı:</b> ${releaseDate.take(4)}<br>")
                if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>")
            } else {
                append("<i>Film/Dizi detayları alınamadı.</i><br><br>")
            }
        }

        val allShows = loadData.items
        val groupedEpisodes = allShows.groupBy {
            val (_, season, episode) = parseEpisodeInfo(it.title ?: "")
            Pair(season ?: 1, episode ?: 1)
        }

        val dubbedList = mutableListOf<Episode>()
        val subbedList = mutableListOf<Episode>()

        for ((key, items) in groupedEpisodes) {
            val (season, episode) = key
            val item = items.first()
            val (cleanTitle, _, _) = parseEpisodeInfo(item.title ?: "")
            val episodeTmdb = if (tmdbId != null && episode > 0) fetchEpisodeData(tmdbId, season, episode) else null

            val episodeName = episodeTmdb?.optString("name") ?: cleanTitle
            val episodeOverview = episodeTmdb?.optString("overview")
            val stillPath = episodeTmdb?.optString("still_path")
            val epPoster = if (!stillPath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w780/$stillPath" else item.attributes["tvg-logo"] ?: finalPosterUrl

            // Episode LoadData (bir bölümdeki kaynakları taşıyan)
            val epLoadData = LoadData(
                items = items,
                title = cleanTitle,
                poster = epPoster,
                group = item.attributes["group-title"] ?: loadData.group,
                nation = item.attributes["tvg-country"] ?: loadData.nation,
                season = season,
                episode = episode,
                isDubbed = itemIsDubbed(item),
                isSubbed = itemIsSubbed(item),
                score = item.score
            )

            val episodeObj = newEpisode(epLoadData.toJson()) {
                name = "$episodeName S${season}E${episode}"
                this.season = season
                this.episode = episode
                posterUrl = epLoadData.poster
                description = episodeOverview ?: ""
            }

            if (epLoadData.isDubbed) dubbedList.add(episodeObj)
            if (epLoadData.isSubbed) subbedList.add(episodeObj)
            if (!epLoadData.isDubbed && !epLoadData.isSubbed) subbedList.add(episodeObj) // fallback original -> subbed group
        }

        // sort
        dubbedList.sortWith(compareBy({ it.season }, { it.episode }))
        subbedList.sortWith(compareBy({ it.season }, { it.episode }))

        val episodesMap = mutableMapOf<DubStatus, List<Episode>>()
        if (dubbedList.isNotEmpty()) episodesMap[DubStatus.Dubbed] = dubbedList
        if (subbedList.isNotEmpty()) episodesMap[DubStatus.Subbed] = subbedList

        // recommendations: show first 24 episodes as simple recommendations
        val recommendations = (dubbedList + subbedList).take(24).mapNotNull { ep ->
            val ld = parseJson<LoadData>(ep.data)
            val titleNumber = if (ld.episode > 0) "${ld.title} S${ld.season}E${ld.episode}" else ld.title
            newAnimeSearchResponse(titleNumber, ep.data).apply {
                posterUrl = ld.poster
                type = TvType.Anime
                score = ld.score?.let { Score.from10(it) }
                if (ld.isDubbed || ld.isSubbed) addDubStatus(dubExist = ld.isDubbed, subExist = ld.isSubbed)
            }
        }

        return when (tmdbType) {
            TvType.Movie -> newMovieLoadResponse(loadData.title, url, TvType.Movie, (dubbedList + subbedList).firstOrNull()?.data ?: "") {
                posterUrl = finalPosterUrl
                backgroundPosterUrl = finalPosterUrl
                this.plot = plot
                this.score = loadData.score?.let { Score.from10(it) }
                this.tags = listOf(loadData.group, loadData.nation) + loadData.videoFormats
                addDubStatus(dubExist = loadData.isDubbed, subExist = loadData.isSubbed)
                this.recommendations = recommendations
            }
            else -> newTvSeriesLoadResponse(loadData.title, url, TvType.TvSeries, (dubbedList + subbedList)) {
                posterUrl = finalPosterUrl
                backgroundPosterUrl = finalPosterUrl
                this.plot = plot
                this.score = loadData.score?.let { Score.from10(it) }
                this.tags = listOf(loadData.group, loadData.nation) + loadData.videoFormats
                this.episodes = episodesMap
                this.recommendations = recommendations
                addDubStatus(dubExist = loadData.isDubbed, subExist = loadData.isSubbed)
            }
        }
    }

    // --------------------------
    // loadLinks: her bölümün içindeki tüm kaynakları döndürür
    // --------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        loadData.items.forEachIndexed { index, item ->
            val linkName = "${loadData.title} Kaynak ${index + 1}"
            val qualityString = item.attributes["tvg-quality"]
            val quality = when (qualityString) {
                "P360" -> Qualities.P360.value
                "P480" -> Qualities.P480.value
                "P720" -> Qualities.P720.value
                "P1080" -> Qualities.P1080.value
                "P2160" -> Qualities.P2160.value
                else -> Qualities.Unknown.value
            }
            val videoUrl = item.url.orEmpty()
            val headers = mutableMapOf<String, String>()
            headers["Referer"] = mainUrl
            item.userAgent?.let { headers["User-Agent"] = it }

            val detected = checkContentType(videoUrl, headers)
            val type = when {
                detected == "mkv" -> ExtractorLinkType.VIDEO
                detected == "mp4" -> ExtractorLinkType.VIDEO
                detected == "m3u8" -> ExtractorLinkType.M3U8
                videoUrl.endsWith(".mkv", true) -> ExtractorLinkType.VIDEO
                videoUrl.endsWith(".mp4", true) -> ExtractorLinkType.VIDEO
                else -> ExtractorLinkType.M3U8
            }

            callback(
                newExtractorLink(
                    source = this.name,
                    name = linkName,
                    url = videoUrl,
                    type = type
                ) {
                    headers = headers
                    quality = quality
                }
            )
        }
        return true
    }
}

// --------------------------
// Dil haritası - yardımcı
// --------------------------
val languageMap = mapOf(
    "en" to "İngilizce", "tr" to "Türkçe", "ja" to "Japonca", "de" to "Almanca", "fr" to "Fransızca",
    "es" to "İspanyolca", "it" to "İtalyanca", "ru" to "Rusça", "pt" to "Portekizce", "ko" to "Korece",
    "zh" to "Çince", "hi" to "Hintçe", "ar" to "Arapça", "nl" to "Felemenkçe", "sv" to "İsveççe",
    "no" to "Norveççe", "da" to "Danca", "fi" to "Fince", "pl" to "Lehçe", "cs" to "Çekçe",
    "hu" to "Macarca", "ro" to "Rumence", "el" to "Yunanca", "uk" to "Ukraynaca", "bg" to "Bulgarca",
    "sr" to "Sırpça", "hr" to "Hırvatça", "sk" to "Slovakça", "sl" to "Slovence", "th" to "Tayca",
    "vi" to "Vietnamca", "id" to "Endonezce", "ms" to "Malayca", "tl" to "Tagalogca", "fa" to "Farsça",
    "he" to "İbranice", "la" to "Latince", "xx" to "Belirsiz", "mul" to "Çok Dilli"
)

fun getTurkishLanguageName(code: String?): String? = code?.lowercase()?.let { languageMap[it] }
