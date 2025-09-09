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
import java.io.InputStream
import java.util.Locale

import com.lagradost.cloudstream3.DubStatus

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
        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

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
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "35  AnimeDizi 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    data class LoadData(
        val urls: List<String>,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 1,
        val episode: Int = 0
    )

    // --- ANA SAYFA ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val grouped = kanallar.items.groupBy { it.attributes["group-title"] ?: "Genel" }

        val homePageLists = grouped.map { (group, items) ->
            val shows = items.mapNotNull {
                val title = it.title ?: return@mapNotNull null
                val poster = it.attributes["tvg-logo"].takeIf { !it.isNullOrBlank() } ?: DEFAULT_POSTER_URL
                val (cleanTitle, _, _) = parseEpisodeInfo(title)

                newTvSeriesSearchResponse(
                    name = cleanTitle,
                    url = LoadData(
                        urls = listOf(it.url ?: ""),
                        title = cleanTitle,
                        poster = poster,
                        group = group,
                        nation = it.attributes["tvg-country"] ?: "TR"
                    ).toJson(),
                    apiName = this.name,
                    type = TvType.TvSeries,
                    posterUrl = poster,
                    year = null
                )
            }
            HomePageList(group, shows)
        }

        return newHomePageResponse(homePageLists)
    }

  
  override suspend fun search(query: String): List<SearchResponse> {
    val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

    return playlist.items.mapNotNull { item ->
        val title = item.title ?: return@mapNotNull null
        val (cleanTitle, season, episode) = parseEpisodeInfo(title)

        val poster = item.attributes["tvg-logo"].takeIf { !it.isNullOrBlank() }
            ?: DEFAULT_POSTER_URL

        val loadData = LoadData(
            urls = listOfNotNull(item.url),
            title = cleanTitle,
            poster = poster,
            group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = item.attributes["tvg-country"] ?: "TR",
            season = season ?: 1,
            episode = episode ?: 1
        )

        newTvSeriesSearchResponse(
            name = cleanTitle,
            url = loadData.toJson(),
            type = TvType.TvSeries
        ) {
            this.posterUrl = poster
            this.quality = SearchQuality.HD
        }
    }
}


   override suspend fun search(query: String): List<SearchResponse> {
    val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

    return playlist.items.mapNotNull { item ->
        val title = item.title ?: return@mapNotNull null
        val (cleanTitle, season, episode) = parseEpisodeInfo(title)

        val poster = item.attributes["tvg-logo"].takeIf { !it.isNullOrBlank() }
            ?: DEFAULT_POSTER_URL

        // LoadData paketleyelim
        val loadData = LoadData(
            urls = listOfNotNull(item.url),
            title = cleanTitle,
            poster = poster,
            group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = item.attributes["tvg-country"] ?: "TR",
            season = season ?: 1,
            episode = episode ?: 1
        )

        newTvSeriesSearchResponse(
            cleanTitle,
            loadData.toJson(),
            TvType.TvSeries
        ) {
            posterUrl = poster
            // ✅ rating kaldırıldı, ekleme yok
        }
    }
}

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val cleanTitle = loadData.title
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val allShows = kanallar.items.filter {
            val (itemCleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
            itemCleanTitle == cleanTitle
        }

        val finalPosterUrl = allShows.firstOrNull()?.attributes?.get("tvg-logo")?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
        val plot = "TMDB'den özet alınamadı."

        val groupedEpisodes = allShows.groupBy {
            val (_, season, episode) = parseEpisodeInfo(it.title.toString())
            Pair(season, episode)
        }

        val currentShowEpisodes = groupedEpisodes.mapNotNull { (key, episodeItems) ->
            val (season, episode) = key
            if (season != null && episode != null) {
                val episodePoster = episodeItems.firstOrNull()?.attributes?.get("tvg-logo")?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
                val episodeTitle = episodeItems.firstOrNull()?.title.toString()
                val (episodeCleanTitle, _, _) = parseEpisodeInfo(episodeTitle)
                val allUrls = episodeItems.map { it.url.toString() }

                newEpisode(
                    LoadData(
                        allUrls,
                        episodeTitle,
                        episodePoster,
                        episodeItems.firstOrNull()?.attributes?.get("group-title") ?: "Bilinmeyen Grup",
                        episodeItems.firstOrNull()?.attributes?.get("tvg-country") ?: "TR",
                        season,
                        episode
                    ).toJson()
                ) {
                    this.name = "$episodeCleanTitle S$season E$episode"
                    this.season = season
                    this.episode = episode
                    this.posterUrl = episodePoster
                }
            } else null
        }.sortedWith(compareBy({ it.season }, { it.episode }))

        val processedEpisodes = currentShowEpisodes.map { episode ->
            episode.apply {
                val episodeLoadData = parseJson<LoadData>(this.data)
                this.posterUrl = episodeLoadData.poster
            }
        }

        return newAnimeLoadResponse(
            cleanTitle,
            url,
            TvType.TvSeries
        ) {
            this.posterUrl = finalPosterUrl
            this.plot = plot
            this.tags = listOf(loadData.group, loadData.nation)
            this.episodes = mutableMapOf(
                DubStatus.Subbed to processedEpisodes
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        loadData.urls.forEachIndexed { index, videoUrl ->
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "${loadData.title} Kaynak ${index + 1}",
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }
}
