package com.owencz1998

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.WebViewResolver // Bu import orijinal dosyada vardı, korunuyor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject // Bu import orijinal dosyada vardı, korunuyor

class PornHubProvider : MainAPI() {
    private val globalTvType = TvType.NSFW
    override var mainUrl             = "https://www.pornhub.com"
    override var name                = "PornHub"
    override val hasMainPage         = true
    override var lang                = "en"
    override val hasQuickSearch      = false // İkinci dosyada da false idi, ancak implementasyon eklendi
    override val hasDownloadSupport  = true // Orijinal dosyadan korundu
    override val hasChromecastSupport = true // Orijinal dosyadan korundu
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
        // İkinci dosyada olmayan, orijinal dosyadan gelen ek kategoriler korunuyor
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

    // Orijinal dosyadan gelen çerezler korunuyor
    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    // İkinci dosyadan alınan toSearchResult uzantı fonksiyonu, ilk dosyanın seçicilerine uyarlandı
    private fun Element.toSearchResult(): SearchResponse? {
        // İlk dosyanın ana sayfa ve arama yapısına uygun seçiciler
        val title = this.selectFirst("span.title a")?.text() ?: return null
        val link = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fetchImgUrl(this.selectFirst("img")) // İlk dosyanın fetchImgUrl'si kullanılıyor

        return MovieSearchResponse(
            name = title,
            url = link,
            apiName = this@PornHubProvider.name, // Sınıfın 'name' özelliğine erişim
            type = globalTvType,
            posterUrl = posterUrl
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val categoryData = request.data
            val categoryName = request.name
            val pagedLink = if (page > 0) categoryData + page else categoryData
            val soup = app.get(pagedLink, cookies = cookies).document

            // toSearchResult fonksiyonu kullanılarak basitleştirildi
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
            throw ErrorLoadingException() // Hata durumunda istisna fırlatmayı sürdür
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url, cookies = cookies).document

        // toSearchResult fonksiyonu kullanılarak basitleştirildi
        return document.select("div.sectionWrapper div.wrap").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    // İkinci dosyadan alınan quickSearch implementasyonu
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = cookies).document // Çerezler korunuyor

        val title = document.selectFirst("h1.title span[class='inlineFree']")?.text()?.trim() ?: "" // İkinci dosyadan alınan seçici
        val poster: String? = document.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: document.selectFirst("head meta[property=og:image]")?.attr("content") // Orijinal dosyadan korundu

        // İkinci dosyadan alınan yıl, derecelendirme ve süre çekimi
        val year = Regex("""uploadDate": "(\d+)""").find(document.html())?.groupValues?.get(1)?.toIntOrNull()
        val rating = document.selectFirst("span.percent")?.text()?.first()?.toString()?.toRatingInt()
        val duration = Regex("duration' : '(.*)',").find(document.html())?.groupValues?.get(1)?.toIntOrNull()

        val tags = document.select("div.categoriesWrapper a[data-label='Category']") // İkinci dosyadan alınan seçici
            .map { it?.text()?.trim().toString().replace(", ", "") } // Orijinal dosyadan formatlama korunuyor

        val recommendations = document.selectXpath("//a[contains(@class, 'img')]").mapNotNull { // İkinci dosyadan alınan seçici
            val recName = it.attr("title").trim()
            val recHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("src")) // İkinci dosyadan alınan seçici
            MovieSearchResponse(recName, recHref, TvType.NSFW) {
                this.posterUrl = recPosterUrl
            }
        }

        val actors =
            document.select("div.pornstarsWrapper a[data-label='Pornstar']") // İkinci dosyadan alınan seçici
                .mapNotNull {
                    // İkinci dosyadan Actor nesnesi oluşturma mantığı
                    Actor(it.text().trim(), it.select("img").attr("src")) // İkinci dosyada img.attr("src") vardı, bu da aktör posteri olabilir
                }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year // Yeni eklenen
            this.plot = title // Orijinalden korunuyor
            this.tags = tags
            this.rating = rating // Yeni eklenen
            this.duration = duration // Yeni eklenen
            this.recommendations = recommendations // Tek bir liste olarak birleştirildi
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Bu kısım orijinal dosyadan (ilk dosya) olduğu gibi korunuyor
        // Çünkü daha sağlam ve çoklu kalite desteği sunuyor.
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
                            type = ExtractorLinkType.M3U8,
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

    // Orijinal dosyadan gelen yardımcı fonksiyon korunuyor
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
