package com.MoOnCrOwNTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "https://mooncrowntv.com"
    override var name = "MoOnCrOwNTV"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val channels = document.select("div.channel-card")

        val items = channels.mapNotNull {
            val name = it.selectFirst("h3")?.text() ?: return@mapNotNull null
            val url = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = fixUrlNull(it.selectFirst("img")?.attr("src"))

            newLiveSearchResponse(
                name = name,
                url = url,
                type = TvType.Live,
                poster = poster
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
            val poster = fixUrlNull(it.selectFirst("img")?.attr("src"))

            newLiveSearchResponse(
                name = name,
                url = url,
                type = TvType.Live,
                poster = poster
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val name = document.selectFirst("h1")?.text() ?: "MoOnCrOwNTV"
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val streamUrl = document.selectFirst("iframe")?.attr("src") ?: url

        return newLiveStreamLoadResponse(
            name = name,
            url = streamUrl,
            dataUrl = url,
            type = TvType.Live,
            poster = poster,
            contentRating = null
        )
    }
}
