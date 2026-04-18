package com.mooncrown

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.*
import java.text.SimpleDateFormat
import java.util.*

class CanliTv(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown/refs/heads/main/guncel_liste.m3u"
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"
    
    override var name = "epg-canlı tv-dizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    // Önbelleği String yerine liste olarak tutarak yükü azaltıyoruz
    private var cachedEpgData: String? = null
    private var lastFetch: Long = 0

    data class CategoryData(val name: String, val poster: String, val items: List<PlaylistItem>)
    data class PlaylistItem(val title: String?, val url: String?, val logo: String?, val tvgId: String?, val group: String?)
    data class GroupedEpisodeData(val title: String, val urls: List<String?>, val logo: String?, val tvgId: String?)

    // EPG verisini sadece ihtiyaç anında ve çok hızlı filtreleyen fonksiyon
    private fun getFastEpg(tvgId: String?): String {
        val xml = cachedEpgData ?: return ""
        if (tvgId.isNullOrBlank()) return ""

        return try {
            val now = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())
            // Tüm dosyayı taramak yerine sadece tvgId'nin geçtiği bloklara odaklanıyoruz
            val pattern = """<programme start="$now.*?channel="${Regex.escape(tvgId)}">.*?<title[^>]*>(.*?)</title>"""
            Regex(pattern, RegexOption.DOT_MATCHES_ALL).findAll(xml)
                .map { it.groupValues[1].replace(Regex("<!\\[CDATA\\[|\\]\\]>"), "").trim() }
                .take(2)
                .joinToString(" -> ") { it }
                .let { if (it.isNotEmpty()) "\n📺 Akış: $it" else "" }
        } catch (e: Exception) { "" }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(mainUrl).text
        val items = mutableListOf<PlaylistItem>()
        var lastInf: String? = null

        response.lines().forEach { line ->
            val t = line.trim()
            if (t.startsWith("#EXTINF")) lastInf = t
            else if (t.isNotEmpty() && !t.startsWith("#") && lastInf != null) {
                val title = lastInf?.split(",")?.lastOrNull()?.trim()
                val logo = Regex("""tvg-logo="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                val tvgId = Regex("""tvg-id="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                val group = Regex("""group-title="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                items.add(PlaylistItem(title, t, logo, tvgId, group))
                lastInf = null
            }
        }

        val categories = items.groupBy { it.group ?: "Genel" }.map { (name, catItems) ->
            val poster = catItems.firstOrNull { !it.logo.isNullOrBlank() }?.logo ?: DEFAULT_POSTER_URL
            newAnimeSearchResponse(name, CategoryData(name, poster, catItems).toJson()) {
                this.posterUrl = poster
                this.type = TvType.TvSeries
            }
        }
        return newHomePageResponse(listOf(HomePageList("Kategoriler", categories, true)), false)
    }

    override suspend fun load(url: String): LoadResponse {
        val cat = parseJson<CategoryData>(url)
        
        // EPG'yi burada çekmek yerine sadece çok eskiyse güncelliyoruz
        if (cachedEpgData == null || (System.currentTimeMillis() - lastFetch > 7200000)) {
            kotlin.runCatching {
                cachedEpgData = app.get(epgUrl, timeout = 10).text
                lastFetch = System.currentTimeMillis()
            }
        }

        val episodesList = cat.items.groupBy { it.title ?: "Kanal" }.entries.mapIndexed { index, entry ->
            val first = entry.value.first()
            val epgSnippet = getFastEpg(first.tvgId) // Çok daha hızlı filtreleme

            newEpisode(GroupedEpisodeData(entry.key, entry.value.map { it.url }, first.logo, first.tvgId).toJson()) {
                this.name = entry.key
                this.episode = index + 1
                this.posterUrl = first.logo ?: cat.poster
                this.description = "Kaynak: ${entry.value.size} adet $epgSnippet"
            }
        }

        return newAnimeLoadResponse(cat.name, url, TvType.TvSeries) {
            this.posterUrl = cat.poster
            this.episodes = mutableMapOf(DubStatus.Subbed to episodesList)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val grouped = parseJson<GroupedEpisodeData>(data)
        grouped.urls.forEachIndexed { i, link ->
            if (!link.isNullOrBlank()) {
                callback.invoke(newExtractorLink(this.name, "${grouped.title} S${i+1}", link, "", Qualities.P1080.value, true))
            }
        }
        return true
    }
}
