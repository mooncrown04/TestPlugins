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
import com.lagradost.cloudstream3.SubtitleFile

// --- Ana Eklenti Sınıfı ---
class AnimeDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl =
        "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
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
        if (url.isNullOrBlank()) return null
        return try {
            val response = app.head(url)
            if (response.isSuccessful) url else null
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
            if (!reader.readLine().startsWith(PlaylistItem.EXT_M3U))
                throw PlaylistParserException.InvalidHeader()

            val playlistItems = mutableListOf<PlaylistItem>()
            var currentIndex = 0
            var line: String? = reader.readLine()

            while (line != null) {
                if (line.startsWith(PlaylistItem.EXT_INF)) {
                    val title = line.split(",").lastOrNull()?.trim()
                    val attrs = parseAttributes(line)
                    val score = attrs["tvg-score"]?.toDoubleOrNull()
                    playlistItems.add(PlaylistItem(title, attrs, score = score))
                } else if (!line.startsWith("#")) {
                    val item = playlistItems.getOrNull(currentIndex)
                    if (item != null) {
                        playlistItems[currentIndex] =
                            item.copy(url = line.trim())
                        currentIndex++
                    }
                }
                line = reader.readLine()
            }
            return Playlist(playlistItems)
        }

        private fun parseAttributes(line: String): Map<String, String> {
            val attrs = mutableMapOf<String, String>()
            val attrPart = line.substringAfter("#EXTINF:-1 ")
            val regex = Regex("""([a-zA-Z0-9-]+)="(.*?)"""")
            regex.findAll(attrPart).forEach { m ->
                val (k, v) = m.destructured
                attrs[k] = v
            }
            return attrs
        }
    }

    sealed class PlaylistParserException(message: String) : Exception(message) {
        class InvalidHeader : PlaylistParserException("Invalid file header.")
    }

    fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
        val cleaned = text.replace(Regex("[\\u200E\\u200F]"), "")
        val regex = Regex("""(.*?)[^\w\d]+(\d+)[^\w\d]+(\d+)""")
        val match = regex.find(cleaned)
        return if (match != null) {
            val (t, s, e) = match.destructured
            Triple(t.trim(), s.toIntOrNull(), e.toIntOrNull())
        } else Triple(cleaned.trim(), null, null)
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
        cachedPlaylist?.let { return it }
        val content = app.get(mainUrl).text
        val playlist = IptvPlaylistParser().parseM3U(content)
        cachedPlaylist = playlist
        sharedPref?.edit()?.putString(CACHE_KEY, playlist.toJson())?.apply()
        return playlist
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlist = getOrFetchPlaylist()
        val grouped = playlist.items.groupBy { parseEpisodeInfo(it.title ?: "").first }
        val lists = grouped.map { (title, items) ->
            val first = items.firstOrNull()
            val poster = checkPosterUrl(first?.attributes?.get("tvg-logo")) ?: DEFAULT_POSTER_URL
            val score = items.mapNotNull { it.score }.maxOrNull()
            val isDubbed = items.any { it.title?.contains("dublaj", true) == true }
            val isSubbed = items.any { it.title?.contains("altyazılı", true) == true }
            val data = LoadData(
                items, title, poster,
                first?.attributes?.get("group-title") ?: "Bilinmeyen Grup",
                first?.attributes?.get("tvg-country") ?: "TR",
                isDubbed = isDubbed, isSubbed = isSubbed, score = score
            )
            val res = newAnimeSearchResponse(title, data.toJson())
            res.posterUrl = poster
            res.type = TvType.Anime
            if (isDubbed || isSubbed) res.addDubStatus(isDubbed, isSubbed)
            res
        }
        return newHomePageResponse(listOf(HomePageList("Anime Dizi", lists)), hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val playlist = getOrFetchPlaylist()
        return playlist.items
            .filter { it.title?.contains(query, true) == true }
            .groupBy { parseEpisodeInfo(it.title ?: "").first }
            .map { (title, items) ->
                val first = items.firstOrNull()
                val poster = checkPosterUrl(first?.attributes?.get("tvg-logo")) ?: DEFAULT_POSTER_URL
                val score = items.mapNotNull { it.score }.maxOrNull()
                val isDubbed = items.any { it.title?.contains("dublaj", true) == true }
                val isSubbed = items.any { it.title?.contains("altyazılı", true) == true }
                val data = LoadData(
                    items, title, poster,
                    first?.attributes?.get("group-title") ?: "Bilinmeyen Grup",
                    first?.attributes?.get("tvg-country") ?: "TR",
                    isDubbed = isDubbed, isSubbed = isSubbed, score = score
                )
                val res = newAnimeSearchResponse(title, data.toJson())
                res.posterUrl = poster
                res.type = TvType.Anime
                if (isDubbed || isSubbed) res.addDubStatus(isDubbed, isSubbed)
                res
            }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        val dubbed = mutableListOf<Episode>()
        val subbed = mutableListOf<Episode>()
        val unknown = mutableListOf<Episode>()
        val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
        val subbedKeywords = listOf("altyazılı", "altyazi")

        data.items.forEach { item ->
            val (title, season, episode) = parseEpisodeInfo(item.title ?: "")
            val finalSeason = season ?: 1
            val finalEpisode = episode ?: 1
            val isDubbed = dubbedKeywords.any { item.title?.contains(it, true) == true }
            val isSubbed = subbedKeywords.any { item.title?.contains(it, true) == true }
            val episodeObj = newEpisode(item.toJson()) {
                this.name = title
                this.season = finalSeason
                this.episode = finalEpisode
                this.posterUrl = data.poster
            }
            when {
                isDubbed -> dubbed.add(episodeObj)
                isSubbed -> subbed.add(episodeObj)
                else -> unknown.add(episodeObj)
            }
        }

        val episodesMap = mutableMapOf<DubStatus, List<Episode>>()
        if (dubbed.isNotEmpty()) episodesMap[DubStatus.Dubbed] = dubbed
        if (subbed.isNotEmpty()) episodesMap[DubStatus.Subbed] = subbed
        if (unknown.isNotEmpty()) episodesMap[DubStatus.Subbed] =
            (episodesMap[DubStatus.Subbed] ?: emptyList()) + unknown

        return newAnimeLoadResponse(
            data.title, url, TvType.TvSeries
        ) {
            posterUrl = data.poster
            plot = "TMDB özet alınamadı."
            score = data.score?.let { Score.from10(it) }
            episodes = episodesMap
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
            callback(
                newExtractorLink(
                    source = name,
                    name = loadData.title,
                    url = item.url ?: "",
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }
}
