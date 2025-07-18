package com.owencz1998

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject

class PornHubProvider : MainAPI() {
    private val globalTvType = TvType.NSFW
    override var mainUrl             = "https://www.pornhub.com"
    override var name                = "PornHub"
    override val hasMainPage         = true
    override var lang                = "en"
    override val hasQuickSearch      = false
    override val hasDownloadSupport  = true
    override val hasChromecastSupport = true
    override val supportedTypes      = setOf(TvType.NSFW)
    override val vpnStatus           = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/video?o=mr&hd=1&page="        to "Recently Featured",
        "${mainUrl}/video?o=tr&t=w&hd=1&page="    to "Top Rated",
        "${mainUrl}/video?o=mv&t=w&hd=1&page="    to "Most Viewed",
        "${mainUrl}/video?o=ht&t=w&hd=1&page="    to "Hottest",
        "${mainUrl}/video?p=professional&hd=1&page=" to "Professional",
        "${mainUrl}/video?o=lg&hd=1&page="        to "Longest",
        "${mainUrl}/video?p=homemade&hd=1&page="  to "Homemade",
        "${mainUrl}/video?o=cm&t=w&hd=1&page="    to "Newest",
        "${mainUrl}/video?c=35&page="             to "Anal",
        "${mainUrl}/video?c=27&page="             to "Lesbian",
        "${mainUrl}/video?c=98&page="             to "Arab",
        "${mainUrl}/video?c=1&page="              to "Asian",
        "${mainUrl}/video?c=89&page="             to "Babysitter",
        "${mainUrl}/video?c=6&page="              to "BBW",
        "${mainUrl}/video?c=141&page="            to "Behind The Scenes",
        "${mainUrl}/video?c=4&page="              to "Big Ass",
        "${mainUrl}/video?c=7&page="              to "Big Dick",
        "${mainUrl}/video?c=8&page="              to "Big Tits",
        "${mainUrl}/video?c=13&page="             to "Blowjob",
    )

    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    // toSearchResult uzantı fonksiyonu
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("span.title a")?.text() ?: return null
        val link = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fetchImgUrl(this.selectFirst("img"))

        // MovieSearchResponse constructor'ına posterUrl'ı doğrudan parametre olarak geçiyoruz
        // 'data' parametresi ve lambda kaldırıldı çünkü constructor'da beklenmiyor
        return MovieSearchResponse(
            name = title,
            url = link,
            apiName = this@PornHubProvider.name,
            type = globalTvType,
            posterUrl = posterUrl // posterUrl'ı doğrudan parametre olarak geçiyoruz
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val categoryData = request.data
            val categoryName = request.name
            val pagedLink = if (page > 0) categoryData + page else categoryData
            val soup = app.get(pagedLink, cookies = cookies).document

            val home = soup.select("div.sectionWrapper div.wrap").mapNotNull { it.toSearchResult() }

            if (home.isNotEmpty()) {
                return newHomePageResponse(
                    list = HomePageList(
                        name = categoryName, list = home, isHorizontalImages = true
                    ), hasNext = true
                )
            } else {
                throw ErrorLoadingException("No homepage data found!")
            }
        } catch (e: Exception) {
            logError(e)
            throw ErrorLoadingException()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url, cookies = cookies).document

        return document.select("div.sectionWrapper div.wrap").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = cookies).document

        val title = document.selectFirst("h1.title span[class='inlineFree']")?.text()?.trim() ?: ""
        val poster: String? = document.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: document.selectFirst("head meta[property=og:image]")?.attr("content")

        val year = Regex("""uploadDate": "(\d+)""").find(document.html())?.groupValues?.get(1)?.toIntOrNull()
        val rating = document.selectFirst("span.percent")?.text()?.first()?.toString()?.toRatingInt()
        val duration = Regex("duration' : '(.*)',").find(document.html())?.groupValues?.get(1)?.toIntOrNull()

        val tags = document.select("div.categoriesWrapper a[data-label='Category']")
            .map { it?.text()?.trim().toString().replace(", ", "") }

        val recommendations = document.selectXpath("//a[contains(@class, 'img')]").mapNotNull {
            val recName = it.attr("title").trim()
            val recHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
            // newMovieSearchResponse'a posterUrl'ı doğrudan parametre olarak geçiyoruz
            // ve 'data' parametresi kaldırıldı çünkü constructor'da beklenmiyor
            newMovieSearchResponse(
                name = recName,
                url = recHref,
                type = globalTvType,
                posterUrl = recPosterUrl // posterUrl'ı doğrudan parametre olarak geçiyoruz
            )
        }

        val actors =
            document.select("div.pornstarsWrapper a[data-label='Pornstar']")
                .mapNotNull {
                    // Actor nesnesi oluşturulurken fetchImgUrl kullanılarak resim URL'si çekiliyor
                    Actor(it.text().trim(), fetchImgUrl(it.selectFirst("img")))
                }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = title
            this.tags = tags
            this.rating = rating
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val request = app.get(
            url = data, cookies = cookies
        )
        val document = request.document
        val mediaDefinitions = JSONObject(
            document.selectXpath("//script[contains(text(),'flashvars')]").first()?.data()
                ?.substringAfter("=")?.substringBefore(";")
        ).getJSONArray("mediaDefinitions")

        for (i in 0 until mediaDefinitions.length()) {
            if (mediaDefinitions.getJSONObject(i).optString("quality") != null) {
                val quality = mediaDefinitions.getJSONObject(i).getString("quality")
                val videoUrl = mediaDefinitions.getJSONObject(i).getString("videoUrl")
                val extlinkList = mutableListOf<ExtractorLink>()
                M3u8Helper().m3u8Generation(
                    M3u8Helper.M3u8Stream(
                        videoUrl
                    ), true
                ).apmap { stream ->
                    extlinkList.add(
                        newExtractorLink(
                            source = name,
                            name = "${this.name}",
                            url = stream.streamUrl,
                            type = ExtractorLinkType.M3u8,
                        ) {
                            this.quality = Regex("(\\d+)").find(quality ?: "")?.groupValues?.get(1)
                                .let { getQualityFromName(it) }
                            this.referer = mainUrl
                        }
                    )
                }
                extlinkList.forEach(callback)
            }
        }

        return true
    }

    private fun fetchImgUrl(imgsrc: Element?): String? {
        return try {
            imgsrc?.attr("src") ?: imgsrc?.attr("data-src") ?: imgsrc?.attr("data-mediabook")
            ?: imgsrc?.attr("alt") ?: imgsrc?.attr("data-mediumthumb")
            ?: imgsrc?.attr("data-thumb_url")
        } catch (e: Exception) {
            null
        }
    }
}
