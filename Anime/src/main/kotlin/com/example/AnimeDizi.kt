package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class AnimeDizi : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    override var name = "35 Anime Diziler 🎬"
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasMainPage = true
    override var lang = "tr"

    data class M3uChannel(
        val name: String,
        val url: String,
        val logo: String? = null,
        val group: String? = null
    )

    // Basit M3U parser
    private fun parseM3u(content: String): List<M3uChannel> {
        val lines = content.lines()
        val channels = mutableListOf<M3uChannel>()

        var currentName = ""
        var currentLogo: String? = null
        var currentGroup: String? = null

        for (line in lines) {
            if (line.startsWith("#EXTINF")) {
                val namePart = line.substringAfter(",").trim()
                currentName = namePart
                currentLogo = Regex("tvg-logo=\"(.*?)\"").find(line)?.groupValues?.get(1)
                currentGroup = Regex("group-title=\"(.*?)\"").find(line)?.groupValues?.get(1)
            } else if (line.startsWith("http")) {
                channels.add(
                    M3uChannel(
                        name = currentName,
                        url = line.trim(),
                        logo = currentLogo,
                        group = currentGroup
                    )
                )
            }
        }
        return channels
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(mainUrl).text
        val channels = parseM3u(response)

        val items = channels.map {
            newTvSeriesSearchResponse(
                it.name,
                it.url,
                TvType.TvSeries
            ) {
                posterUrl = it.logo
            }
        }

        return newHomePageResponse(listOf(HomePageList(name, items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get(mainUrl).text
        val channels = parseM3u(response)

        return channels.filter { it.name.contains(query, ignoreCase = true) }.map {
            newTvSeriesSearchResponse(
                it.name,
                it.url,
                TvType.TvSeries
            ) {
                posterUrl = it.logo
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        return newTvSeriesLoadResponse(name, url, TvType.TvSeries) {
            addEpisodes(
                DubStatus.Dubbed,
                listOf(Episode(url, name = name))
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                data,
                referer = "",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }
}
