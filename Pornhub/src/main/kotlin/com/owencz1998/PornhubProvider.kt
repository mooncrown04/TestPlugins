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
    override var name                = "P\ud83d\udd34rnHub"
    override val hasMainPage         = true
    override var lang                = "en"
    override val hasQuickSearch      = false
    override val hasDownloadSupport  = true
    override val hasChromecastSupport = true
    override val supportedTypes      = setOf(TvType.NSFW)
    override val vpnStatus           = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/video/search?search=giannadior&page="    to "giannadior",
        "${mainUrl}/video/search?search=abelladanger&page="  to "abelladanger",
        "${mainUrl}/video/search?search=kyliepage&page="     to "kyliepage",
        "${mainUrl}/video/search?search=augustames&page="    to "augustames",
        "${mainUrl}/video/search?search=elizaibarra&page="   to "elizaibarra",
        "${mainUrl}/video/search?search=rileyreid&page="     to "rileyreid",
        "${mainUrl}/video/search?search=miakhalifa&page="    to "miakhalifa",
        "${mainUrl}/video/search?search=emilywillis&page="   to "emilywillis",    
        "${mainUrl}/video/search?search=kyliequinn&page="    to "kyliequinn",
        "${mainUrl}/video/search?search=sashagrey&page="     to "sashagrey",
        "${mainUrl}/video/search?search=blacked&page="       to "blacked",
        "${mainUrl}/video/search?search=johnnysins&page="    to "johnnysins",
        "${mainUrl}/video/search?search=jasonluv&page="      to "jasonluv",
        "${mainUrl}/video/search?search=jaxslayher&page="    to "jaxslayher",    
        "${mainUrl}/video/search?search=blacked&page="       to "blacked",
        "${mainUrl}/video/search?search=blackedraw&page="    to "blackedraw",
        "${mainUrl}/video/search?search=tushy&page="         to "tushy",
        "${mainUrl}/video/search?search=vixen&page="         to "vixen",
        "${mainUrl}/video/search?search=brazzers&page="      to "brazzers",
        "${mainUrl}/video/search?search=teamskeet&page="     to "teamskeet",      
        "${mainUrl}/video?o=mr&hd=1&page="                   to "Recently Featured",
        "${mainUrl}/video?o=tr&t=w&hd=1&page="               to "Top Rated",
        "${mainUrl}/video?o=mv&t=w&hd=1&page="               to "Most Viewed",
        "${mainUrl}/video?o=ht&t=w&hd=1&page="               to "Hottest",
        "${mainUrl}/video?p=professional&hd=1&page="         to "Professional",
        "${mainUrl}/video?o=lg&hd=1&page="                   to "Longest",
        "${mainUrl}/video?p=homemade&hd=1&page="             to "Homemade",
        "${mainUrl}/video?o=cm&t=w&hd=1&page="               to "Newest",
        "${mainUrl}/video?c=35&page="                        to "Anal",
        "${mainUrl}/video?c=27&page="                        to "Lesbian",
        "${mainUrl}/video?c=98&page="                        to "Arab",
        "${mainUrl}/video?c=1&page="                         to "Asian",
        "${mainUrl}/video?c=89&page="                        to "Babysitter",    
        "${mainUrl}/video?c=6&page="                         to "BBW",
        "${mainUrl}/video?c=141&page="                       to "Behind The Scenes",
        "${mainUrl}/video?c=4&page="                         to "Big Ass",
        "${mainUrl}/video?c=7&page="                         to "Big Dick",
        "${mainUrl}/video?c=8&page="                         to "Big Tits",
        "${mainUrl}/video?c=13&page="                        to "Blowjob",
    )
    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

   // toSearchResult uzantı fonksiyonu
    private fun Element.toSearchResult(): SearchResponse? {
        // İlk dosyanın ana sayfa ve arama yapısına uygun seçiciler
        val title = this.selectFirst("span.title a")?.text() ?: return null
        val link = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fetchImgUrl(this.selectFirst("img")) // fetchImgUrl kullanılıyor

        // MovieSearchResponse constructor'ı posterUrl'ı lambda içinde ayarlayacak şekilde düzeltildi
        return newMovieSearchResponse(
            name = title,
            url = link,
            type = globalTvType // TvType.Movie yerine globalTvType kullanıldı
        ) {
            this.posterUrl = posterUrl
        }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val categoryData = request.data
            val categoryName = request.name
            val pagedLink = if (page > 0) categoryData + page else categoryData
            val soup = app.get(pagedLink, cookies = cookies).document
            val home = soup.select("div.sectionWrapper div.wrap").mapNotNull {
                if (it == null) {
                    return@mapNotNull null
                }
                val title = it.selectFirst("span.title a")?.text() ?: ""
                val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val img = fetchImgUrl(it.selectFirst("img"))
                newMovieSearchResponse( // newMovieSearchResponse kullanıldı
                    name = title,
                    url = link,
                    type = globalTvType // TvType.Movie yerine globalTvType kullanıldı
                ) {
                    this.posterUrl = img
                }
            }
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
            throw ErrorLoadingException() // Hata durumunda istisna fırlatmayı sürdür
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url, cookies = cookies).document
        return document.select("div.sectionWrapper div.wrap").mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val title = it.selectFirst("span.title a")?.text() ?: return@mapNotNull null
            val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val image = fetchImgUrl(it.selectFirst("img"))
            newMovieSearchResponse( // newMovieSearchResponse kullanıldı
                name = title,
                url = link,
                type = globalTvType // TvType.Movie yerine globalTvType kullanıldı
            ) {
                this.posterUrl = image
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url, cookies = cookies).document
        val title = soup.selectFirst(".title span")?.text() ?: ""
        val poster: String? = soup.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val year = Regex("""uploadDate": "(\d+)""").find(soup.html())?.groupValues?.get(1)?.toIntOrNull()
        val rating = soup.selectFirst("span.percent")?.text()?.first()?.toString()?.toRatingInt()
        val duration = Regex("duration' : '(.*)',").find(soup.html())?.groupValues?.get(1)?.toIntOrNull()

        val tags = soup.select("div.categoriesWrapper a")
            .map { it?.text()?.trim().toString().replace(", ", "") }
        val actors  = soup.select("div.pornstarsWrapper a[data-label='Pornstar']").mapNotNull {
            Actor(it.text().trim(), fetchImgUrl(it.selectFirst("img"))) // fetchImgUrl kullanıldı
        }
        
        // Önerilen kısım: recommendations listesi için MovieSearchResponse düzeltildi
       val recommendations = soup.selectXpath("//a[contains(@class, 'img')]").mapNotNull {
            val recName      = it.attr("title").trim()
            val recHref      = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))
            newMovieSearchResponse(recName, recHref, TvType.NSFW) {
                this.posterUrl = recPosterUrl
            }
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = title
            this.tags = tags
            this.rating = rating
            this.duration = duration
            addActors(actors)
            this.recommendations = recommendations 
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
                            type = ExtractorLinkType.M3U8, // Düzeltildi: M3u8 -> M3U8
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
