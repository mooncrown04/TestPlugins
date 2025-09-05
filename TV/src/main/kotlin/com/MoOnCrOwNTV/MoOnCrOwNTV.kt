package com.MoOnCrOwNTV

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern

data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val url: String? = null
) {
    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
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
                    Log.w("IptvPlaylistParser", "URL'ye karşılık gelen EXTINF satırı bulunamadı, atlanıyor: $line")
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
        quotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value.trim()
        }
        return attributes
    }

    private fun String.getUrl(): String? = split("|").firstOrNull()?.trim()
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

class MoOnCrOwNTV(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/r4p9v7g76ikwt8zsyuhyn/sile.m3u?rlkey=esnalbpm4kblxgkvym51gjokm"
    override var name = "35 MoOnCrOwN TV 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Live)

    private val DEFAULT_POSTER_URL = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val groupedByGroupTitle = kanallar.items.groupBy {
            it.attributes["group-title"] ?: "Diğer"
        }

        val homePageLists = groupedByGroupTitle.map { (groupTitle, items) ->
            val groupedChannels = items.groupBy { it.title.toString() }
            val channelList = groupedChannels.mapNotNull { (title, items) ->
                val firstItem = items.firstOrNull() ?: return@mapNotNull null
                newSearchResponse(
                    name = title,
                    url = firstItem.url.toString(),
                    type = TvType.Live,
                    posterUrl = firstItem.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
                )
            }
            HomePageList(groupTitle, channelList, isHorizontalImages = true)
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val groupedChannels = kanallar.items.groupBy { it.title.toString() }

        return groupedChannels.filter { (title, _) ->
            title.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
        }.mapNotNull { (title, items) ->
            val firstItem = items.firstOrNull() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = firstItem.url.toString(),
                type = TvType.Live,
                posterUrl = firstItem.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
            )
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val selectedKanal = kanallar.items.firstOrNull { it.url == url }
            ?: throw Exception("Kanal bulunamadı: $url")

        val channelTitle = selectedKanal.title.toString()

        val allUrls = kanallar.items.filter { it.title == channelTitle }.map { it.url.toString() }

        return newMovieLoadResponse(
            name = channelTitle,
            url = url,
            type = TvType.Live,
            dataUrl = allUrls.toJson()
        ) {
            poster = selectedKanal.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = parseJson<List<String>>(data)

        urls.forEachIndexed { index, videoUrl ->
            val linkName = "Kaynak ${index + 1}"
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = linkName,
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    private fun newSearchResponse(
        name: String,
        url: String,
        type: TvType,
        posterUrl: String? = null
    ): SearchResponse {
        return LiveSearchResponse(
            name = name,
            url = url,
            apiName = this.name,
            type = type,
            poster = posterUrl
        )
    }
}
