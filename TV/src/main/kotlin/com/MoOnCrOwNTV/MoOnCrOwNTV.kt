package com.MoOnCrOwNTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "https://mooncrowntv.com"
    override var name = "MoOnCrOwNTV"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document
        val channels = document.select("div.channel-card")

        val items = channels.mapNotNull {
            val name = it.selectFirst("h3")?.text() ?: return@mapNotNull null
            val url = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))

            newLiveSearchResponse(
                name = name,
                url = url,
                type = TvType.Live,
                posterUrl = posterUrl
            )
        }

        return HomePageResponse(listOf(HomePageList("Canlı Kanallar", items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=$query"
        val document = app.get(searchUrl).document
        val results = document.select("div.channel-card")

        return results.mapNotNull {
            val name = it.selectFirst("h3")?.text() ?: return@mapNotNull null
            val url = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))

            newLiveSearchResponse(
                name = name,
                url = url,
                type = TvType.Live,
                posterUrl = posterUrl
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val name = document.selectFirst("h1")?.text() ?: "MoOnCrOwNTV"
        val posterUrl = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val streamUrl = document.selectFirst("iframe")?.attr("src") ?: url

        return LiveStreamLoadResponse(
            name = name,
            url = streamUrl,
            apiName = this.name,
            type = TvType.Live,
            posterUrl = posterUrl
        )
    }
}
