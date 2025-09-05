package com.MoOnCrOwNTV


import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream

class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/Kraptor123/yaylink/refs/heads/main/channels.m3u"
    override var name = "CanliTelevizyon"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val grouped = playlist.items.groupBy { it.attributes["group-title"] ?: "Diğer" }

        val homeLists = grouped.map { (group, items) ->
            val channels = items.map { item ->
                val data = LoadData(
                    url = item.url ?: "",
                    title = item.title ?: "Bilinmeyen",
                    poster = item.attributes["tvg-logo"] ?: "",
                    group = item.attributes["group-title"] ?: "Diğer",
                    nation = item.attributes["tvg-country"] ?: "tr"
                )

                newLiveSearchResponse(data.title, data.toJson(), TvType.Live) {
                    this.posterUrl = data.poster
                    this.lang = data.nation
                }
            }

            HomePageList(group, channels, isHorizontalImages = true)
        }

        return HomePageResponse(homeLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return playlist.items.filter {
            it.title?.contains(query, ignoreCase = true) == true
        }.map { item ->
            val data = LoadData(
                url = item.url ?: "",
                title = item.title ?: "Bilinmeyen",
                poster = item.attributes["tvg-logo"] ?: "",
                group = item.attributes["group-title"] ?: "Diğer",
                nation = item.attributes["tvg-country"] ?: "tr"
            )

            newLiveSearchResponse(data.title, data.toJson(), TvType.Live) {
                this.posterUrl = data.poster
                this.lang = data.nation
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val data = fetchData(url)
        val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        val recommendations = playlist.items.filter {
            it.attributes["group-title"] == data.group && it.title != data.title
        }.map {
            val recData = LoadData(
                url = it.url ?: "",
                title = it.title ?: "Bilinmeyen",
                poster = it.attributes["tvg-logo"] ?: "",
                group = it.attributes["group-title"] ?: "Diğer",
                nation = it.attributes["tvg-country"] ?: "tr"
            )

            newLiveSearchResponse(recData.title, recData.toJson(), TvType.Live) {
                this.posterUrl = recData.poster
                this.lang = recData.nation
            }
        }

        val plot = if (data.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${data.group} | ${data.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${data.group} | ${data.nation} «"
        }

        return newLiveStreamLoadResponse(data.title, data.url, url) {
            this.posterUrl = data.poster
            this.plot = plot
            this.tags = listOf(data.group, data.nation)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchData(data)
        val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val kanal = playlist.items.firstOrNull { it.url == loadData.url } ?: return false

        callback(
            ExtractorLink(
                source = name,
                name = name,
                url = loadData.url,
                headers = kanal.headers,
                referer = kanal.headers["referrer"] ?: "",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        )

        return true
    }

    private suspend fun fetchData(data: String): LoadData {
        return if (data.startsWith("{")) {
            parseJson(data)
        } else {
            val playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = playlist.items.firstOrNull { it.url == data }
                ?: throw Exception("Kanal bulunamadı")

            LoadData(
                url = kanal.url ?: "",
                title = kanal.title ?: "Bilinmeyen",
                poster = kanal.attributes["tvg-logo"] ?: "",
                group = kanal.attributes["group-title"] ?: "Diğer",
                nation = kanal.attributes["tvg-country"] ?: "tr"
            )
        }
    }

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String)
}
