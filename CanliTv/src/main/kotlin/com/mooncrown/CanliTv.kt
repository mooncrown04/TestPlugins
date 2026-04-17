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

    data class CategoryData(val name: String, val poster: String)
    data class PlaylistItem(val title: String?, val url: String?, val logo: String?, val tvgId: String?, val group: String?)
    data class GroupedEpisodeData(val title: String, val urls: List<String>, val logo: String?, val tvgId: String?)

    private suspend fun parsePlaylist(): List<PlaylistItem> {
        return try {
            val response = app.get(mainUrl).text
            val items = mutableListOf<PlaylistItem>()
            var lastInf: String? = null

            response.lines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("#EXTINF")) {
                    lastInf = t
                } else if (t.isNotEmpty() && !t.startsWith("#") && lastInf != null) {
                    val title = lastInf?.split(",")?.lastOrNull()?.trim()
                    val logo = Regex("""tvg-logo="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                    val tvgId = Regex("""tvg-id="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                    val group = Regex("""group-title="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                    items.add(PlaylistItem(title, t, logo, tvgId, group))
                    lastInf = null
                }
            }
            items
        } catch (e: Exception) { emptyList() }
    }

    private fun extractEpg(epgData: String, tvgId: String?): String {
        if (tvgId.isNullOrBlank() || !epgData.contains("channel=\"$tvgId\"")) return ""
        return try {
            val now = System.currentTimeMillis()
            val sdfIn = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            val sdfOut = SimpleDateFormat("HH:mm", Locale.getDefault())

            val programs = Regex("""<programme start="([^"]*)"[^>]*channel="${Regex.escape(tvgId)}">.*?<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(epgData).mapNotNull { m ->
                    val start = sdfIn.parse(m.groupValues[1].substring(0, 14))?.time ?: 0L
                    if (start > now - 3600000) {
                        val t = m.groupValues[2].replace(Regex("<!\\[CDATA\\[|]]>"), "").trim()
                        "[${sdfOut.format(Date(start))}] $t"
                    } else null
                }.take(3).toList()

            if (programs.isNotEmpty()) "\n\n📺 Yayın Akışı:\n" + programs.joinToString("\n") else ""
        } catch (e: Exception) { "" }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allItems = parsePlaylist()
        val categories = allItems.groupBy { it.group ?: "Genel" }

        val responses = categories.map { (name, items) ->
            val poster = items.firstOrNull { !it.logo.isNullOrBlank() }?.logo ?: DEFAULT_POSTER_URL
            newAnimeSearchResponse(name, CategoryData(name, poster).toJson()) {
                this.posterUrl = poster
                this.type = TvType.TvSeries
            }
        }
        return newHomePageResponse(listOf(HomePageList("Kategoriler", responses, isHorizontalImages = true)), false)
    }

    override suspend fun load(url: String): LoadResponse {
        val cat = parseJson<CategoryData>(url)
        val allItems = parsePlaylist()
        val fullEpgData = try { app.get(epgUrl, timeout = 10).text } catch (e: Exception) { "" }
        
        val filtered = allItems.filter { (it.group ?: "Genel") == cat.name }
        val grouped = filtered.groupBy { it.title ?: "Bilinmeyen" }

        val episodesList = grouped.entries.mapIndexed { index, entry ->
            val title = entry.key
            val items = entry.value
            val first = items.first()

            val epData = GroupedEpisodeData(
                title, 
                items.mapNotNull { it.url }, 
                first.logo,
                first.tvgId
            ).toJson()
            
            val channelEpg = extractEpg(fullEpgData, first.tvgId)

            newEpisode(epData) {
                this.name = title
                this.episode = index + 1
                this.season = 1
                this.posterUrl = first.logo ?: cat.poster
                this.description = "Kaynak: ${items.size} adet$channelEpg"
            }
        }

        return newAnimeLoadResponse(cat.name, url, TvType.TvSeries) {
            this.posterUrl = cat.poster
            this.episodes = mutableMapOf(DubStatus.Subbed to episodesList)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val grouped = parseJson<GroupedEpisodeData>(data)
        
        grouped.urls.forEachIndexed { i, link ->
            // DERLEME HATASI ÇÖZÜLDÜ: 
            // Parametreleri isimle vermek yerine süslü parantez içinde atıyoruz.
            callback.invoke(
                newExtractorLink(
                    this.name,
                    "${grouped.title} Kaynak ${i + 1}",
                    link,
                    "" // Referer buraya (üçüncü sıraya) String olarak gelir
                ) {
                    this.quality = Qualities.P1080.value
                    this.type = ExtractorLinkType.M3U8
                }
            )
        }
        return true
    }
}
