package com.mooncrown

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element

class CinemaCity(private val plugin: CinemaCityPlugin) : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity"
    override var lang = "tr" // Site içeriği ağırlıklı TR/Multi olduğu için
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Not: Çerezler (Cookies) Cloudstream ayarlarından manuel güncellenmelidir.
    private val protectionHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Filmler",
        "$mainUrl/tv-series/" to "Diziler",
        "$mainUrl/xfsearch/genre/animation/" to "Animasyon",
        "$mainUrl/xfsearch/genre/korku/" to "Korku"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = request.data.trimEnd('/')
        val url = if (page > 1) "$base/page/$page/" else "$base/"
        val doc = app.get(url, headers = protectionHeaders).document
        val items = doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("a[href*='.html']") ?: return null
        val href = fixUrl(link.attr("href"))
        val title = link.text().split("(")[0].trim()
        val poster = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val isTv = href.contains("/tv-series/")

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search&subaction=search&story=$query"
        val doc = app.get(url, headers = protectionHeaders).document
        return doc.select("div.dar-short_item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = protectionHeaders).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: throw ErrorLoadingException("Başlık bulunamadı")
        val poster = fixUrlNull(doc.selectFirst("div.dar-full_poster img")?.attr("src"))
        val plot = doc.selectFirst("div.ta-full_text1")?.text()?.trim()
        
        val isTv = url.contains("/tv-series/")
        val episodes = mutableListOf<Episode>()

        // Şifreli scripti bul ve çöz
        val script = doc.select("script").map { it.html() }.firstOrNull { it.contains("atob") }
        if (script != null) {
            // eval(atob("...")) içindeki Base64 verisini Regex ile yakala
            val base64Match = """atob\s*\(\s*["'](.*?)["']\s*\)""".toRegex().find(script)
            val decoded = base64Match?.let { base64Decode(it.groupValues[1]) } ?: ""

            // Çözülen metnin içinden 'file' JSON verisini yakala
            val fileRegex = """file\s*:\s*['"](\[.*?\]|http.*?)['"]""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val fileData = fileRegex.find(decoded)?.groupValues?.get(1)

            if (fileData != null && fileData.startsWith("[")) {
                val jArray = JSONArray(fileData)
                for (i in 0 until jArray.length()) {
                    val seasonObj = jArray.getJSONObject(i)
                    if (seasonObj.has("folder")) {
                        // Dizi İşleme
                        val sNum = i + 1
                        val folder = seasonObj.getJSONArray("folder")
                        for (j in 0 until folder.length()) {
                            val epObj = folder.getJSONObject(j)
                            episodes.add(newEpisode(epObj.toString()) {
                                this.name = epObj.optString("title")
                                this.season = sNum
                                this.episode = j + 1
                            })
                        }
                    } else {
                        // Film İşleme
                        episodes.add(newEpisode(seasonObj.toString()) {
                            this.name = seasonObj.optString("title", "Film")
                        })
                    }
                }
            }
        }

        return if (isTv) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) { this.posterUrl = poster; this.plot = plot }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) { // Film için tekil link mantığı
                this.posterUrl = poster
                this.plot = plot
                // Eğer film datası episodes içindeyse buraya atanabilir
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val json = JSONObject(data)
            val fileUrl = json.getString("file")
            
            // Bayrak Mantığı (JS'deki mantığın Kotlin versiyonu)
            val flags = mutableListOf<String>()
            if (fileUrl.contains("turkish") || fileUrl.contains("_tr")) flags.add("🇹🇷")
            if (fileUrl.contains("english") || finalUrl.contains("_en")) flags.add("🇺🇸")
            
            val label = if (flags.isEmpty()) "Orijinal" else flags.joinToString("")

            // Kaliteyi linkten çek
            val quality = when {
                fileUrl.contains("1080") -> 1080
                fileUrl.contains("720") -> 720
                else -> 480
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "$name [$label]",
                    url = fileUrl,
                    referer = "$mainUrl/",
                    quality = quality,
                    type = if (fileUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                )
            )
        } catch (e: Exception) {
            return false
        }
        return true
    }
}