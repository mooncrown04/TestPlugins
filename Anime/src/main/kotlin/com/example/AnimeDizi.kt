package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream

class NeonSpor : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/chn3cr4g67hnah3w2c19m/eyuptv.m3u?rlkey=2ubdclpcrhkcgj8iogwipuj3r"
    override var name = "Anime dene1"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlistText = app.get(mainUrl).text
        val kanallar = IptvPlaylistParser().parseM3U(playlistText)

        val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { group ->
            val title = group.key
            val show = group.value.mapNotNull { kanal ->
                val streamurl = kanal.url ?: return@mapNotNull null
                val channelname = kanal.title ?: "Bilinmeyen Kanal"
                val posterurl = kanal.attributes["tvg-logo"] ?: ""
                val chGroup = kanal.attributes["group-title"] ?: ""
                val nation = kanal.attributes["tvg-country"] ?: ""

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
            HomePageList(title, show, isHorizontalImages = true)
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return kanallar.items.filter { it.title?.contains(query, ignoreCase = true) == true }.map { kanal ->
            val streamurl = kanal.url ?: ""
            val channelname = kanal.title ?: ""
            val posterurl = kanal.attributes["tvg-logo"] ?: ""
            val chGroup = kanal.attributes["group-title"] ?: ""
            val nation = kanal.attributes["tvg-country"] ?: ""

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                type = TvType.Live
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = fetchDataFromUrlOrJson(url)
        val displayInfo = if (loadData.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${loadData.group} | ${loadData.nation} «"
        }

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val recommendations = kanallar.items
            .filter { it.attributes["group-title"] == loadData.group && it.title != loadData.title }
            .map { kanal ->
                newLiveSearchResponse(
                    kanal.title ?: "",
                    LoadData(kanal.url ?: "", kanal.title ?: "", kanal.attributes["tvg-logo"] ?: "", kanal.attributes["group-title"] ?: "", kanal.attributes["tvg-country"] ?: "").toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = kanal.attributes["tvg-logo"]
                    this.lang = kanal.attributes["tvg-country"]
                }
            }

        return newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
            this.posterUrl = loadData.poster
            this.plot = displayInfo
            this.tags = listOf(loadData.group, loadData.nation)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val kanal = kanallar.items.firstOrNull { it.url == loadData.url }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = loadData.url,
                ExtractorLinkType.M3U8
            ) {
                this.headers = kanal?.headers ?: emptyMap()
                this.referer = kanal?.headers?.get("referrer") ?: ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String)

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        return if (data.startsWith("{")) {
            parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.first { it.url == data }
            LoadData(
                kanal.url ?: "",
                kanal.title ?: "",
                kanal.attributes["tvg-logo"] ?: "",
                kanal.attributes["group-title"] ?: "",
                kanal.attributes["tvg-country"] ?: ""
            )
        }
    }
}

// --- PARSER VE DATA SINIFLARI ---

data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
)

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())

    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (reader.readLine()?.startsWith("#EXTM3U") != true) {
            throw Exception("Geçersiz M3U Dosyası")
        }

        val playlistItems = mutableListOf<PlaylistItem>()
        var line: String? = reader.readLine()

        while (line != null) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                when {
                    trimmedLine.startsWith("#EXTINF") -> {
                        val title = trimmedLine.split(",").lastOrNull()?.trim()
                        val attributes = trimmedLine.getM3uAttributes()
                        playlistItems.add(PlaylistItem(title = title, attributes = attributes))
                    }
                    trimmedLine.startsWith("#EXTVLCOPT") -> {
                        if (playlistItems.isNotEmpty()) {
                            val lastIdx = playlistItems.lastIndex
                            val item = playlistItems[lastIdx]
                            val ua = trimmedLine.getTagValue("http-user-agent") ?: item.userAgent
                            val ref = trimmedLine.getTagValue("http-referrer")
                            val newHeaders = item.headers.toMutableMap()
                            ua?.let { newHeaders["user-agent"] = it }
                            ref?.let { newHeaders["referrer"] = it }
                            playlistItems[lastIdx] = item.copy(userAgent = ua, headers = newHeaders)
                        }
                    }
                    !trimmedLine.startsWith("#") -> {
                        if (playlistItems.isNotEmpty()) {
                            val lastIdx = playlistItems.lastIndex
                            playlistItems[lastIdx] = playlistItems[lastIdx].copy(url = trimmedLine)
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.getM3uAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("([\\w-]+)=\"([^\"]*)\"")
        regex.findAll(this).forEach { match ->
            attributes[match.groupValues[1]] = match.groupValues[2]
        }
        return attributes
    }

    private fun String.getTagValue(key: String): String? {
        return Regex("$key=(.*)", RegexOption.IGNORE_CASE).find(this)?.groups?.get(1)?.value?.replace("\"", "")?.trim()
    }
}
