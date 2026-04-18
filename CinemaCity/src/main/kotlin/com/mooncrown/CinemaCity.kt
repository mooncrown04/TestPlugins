package com.mooncrown

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element

class CinemaCity(private val plugin: CinemaCityPlugin) : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // JS'deki Cookies ve Header yapısı
    private val protectionHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Cookie" to "dle_user_id=32729; dle_password=894171c6a8dab18ee594d5c652009a35;",
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
        return if (href.contains("/tv-series/")) {
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
        val doc = app.get(url, headers = protectionHeaders + ("Referer" to url)).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "İsimsiz"
        val poster = fixUrlNull(doc.selectFirst("div.dar-full_poster img")?.attr("src"))
        val plot = doc.selectFirst("div.ta-full_text1")?.text()?.trim()
        
        val episodes = mutableListOf<Episode>()
        val script = doc.select("script").map { it.html() }.firstOrNull { it.contains("atob") }

        if (script != null) {
            val base64Match = """atob\s*\(\s*["'](.*?)["']\s*\)""".toRegex().find(script)
            val decoded = base64Match?.let { base64Decode(it.groupValues[1]) } ?: ""
            val fileRegex = """file\s*:\s*['"](\[.*?\]|http.*?)['"]""".toRegex(RegexOption.DOT_MATCHES_ALL)
            var fileData = fileRegex.find(decoded)?.groupValues?.get(1)

            if (fileData != null) {
                // JS'deki unescape temizliği (Kritik: JSON bozulmasını önler)
                val cleanedFileData = fileData.replace("\\/", "/").replace("\\\"", "\"")

                if (cleanedFileData.startsWith("[")) {
                    val jArray = JSONArray(cleanedFileData)
                    for (i in 0 until jArray.length()) {
                        val item = jArray.getJSONObject(i)
                        if (item.has("folder")) {
                            val folder = item.getJSONArray("folder")
                            for (j in 0 until folder.length()) {
                                val epObj = folder.getJSONObject(j)
                                episodes.add(newEpisode(epObj.toString()) {
                                    this.name = epObj.optString("title")
                                    this.season = i + 1
                                    this.episode = j + 1
                                })
                            }
                        } else {
                            episodes.add(newEpisode(item.toString()) {
                                this.name = item.optString("title", title)
                            })
                        }
                    }
                } else {
                    val movieJson = JSONObject().put("file", cleanedFileData)
                    episodes.add(newEpisode(movieJson.toString()) { this.name = title })
                }
            }
        }

        // Play butonu için fallback
        if (episodes.isEmpty()) {
            episodes.add(newEpisode(JSONObject().put("file", "empty").toString()) { this.name = "Kaynak Bulunamadı" })
        }

        return if (url.contains("/tv-series/")) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.actors = listOf(ActorData(Actor("MoOnCrOwN"), roleString = "Developer"))
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.first().data) {
                this.posterUrl = poster
                this.plot = plot
                this.actors = listOf(ActorData(Actor("MoOnCrOwN"), roleString = "Developer"))
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
            val fileStr = json.optString("file")
            if (fileStr.isNullOrBlank() || fileStr == "empty") return false

            fileStr.split(",").forEach { part ->
                val qualityMatch = """\[(.*?)\]""".toRegex().find(part)
                val qualityLabel = qualityMatch?.groupValues?.get(1) ?: ""
                val streamUrl = part.replace("[$qualityLabel]", "").trim()

                if (streamUrl.startsWith("http")) {
                    val urlLower = streamUrl.toLowerCase()
                    val flags = mutableListOf<String>()

                    // JS'deki Genişletilmiş Dil Haritası
                    val langMap = mapOf(
                        "turkish" to "🇹🇷", "_tr" to "🇹🇷",
                        "english" to "🇺🇸", "_en" to "🇺🇸",
                        "german" to "🇩🇪", "_de" to "🇩🇪",
                        "french" to "🇫🇷", "_fr" to "🇫🇷",
                        "russian" to "🇷🇺", "_ru" to "🇷🇺"
                    )
                    
                    langMap.forEach { (key, flag) ->
                        if (urlLower.contains(key)) flags.add(flag)
                    }

                    // JS'deki Ses Kanalı Sayma (.m4a sayısına göre)
                    val audioCount = "\\.m4a".toRegex().findAll(streamUrl).count()
                    val infoLabel = when {
                        audioCount > 1 -> "Multi: $audioCount ${flags.distinct().joinToString("")}"
                        flags.isNotEmpty() -> flags.distinct().joinToString("")
                        else -> "Orijinal 📄"
                    }

                    callback.invoke(
                        newExtractorLink(
                            source = "$name [$infoLabel]",
                            name = "$name $qualityLabel".trim(),
                            url = streamUrl,
                            referer = "$mainUrl/",
                            quality = getQualityFromName(qualityLabel),
                            type = if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
