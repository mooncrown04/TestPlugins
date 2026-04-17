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
    
    override var name = "canlı tv-dizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    // --- Veri Modelleri ---
    data class CategoryData(
        val name: String,
        val poster: String,
        val items: List<PlaylistItem>
    )

    data class PlaylistItem(
        val title: String? = null,
        val url: String? = null,
        val logo: String? = null,
        val tvgId: String? = null,
        val group: String? = null
    )

    // --- EPG Bilgisi Çekme ---
    private suspend fun fetchEpg(tvgId: String?): String {
        if (tvgId.isNullOrBlank()) return ""
        return kotlin.runCatching {
            val response = app.get(epgUrl, timeout = 5).text
            val now = System.currentTimeMillis()
            val sdfInput = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            val pattern = """<programme start="([^"]*)"[^>]*channel="${Regex.escape(tvgId)}">.*?<title[^>]*>(.*?)</title>"""
            val programs = Regex(pattern, RegexOption.DOT_MATCHES_ALL).findAll(response).mapNotNull { m ->
                val startTime = sdfInput.parse(m.groupValues[1].substring(0, 14))?.time ?: 0L
                if (startTime > now - 3600000) {
                    val title = m.groupValues[2].replace("<![CDATA[", "").replace("]]>", "").trim()
                    "[${sdfOutput.format(Date(startTime))}] $title"
                } else null
            }.take(3).toList()

            if (programs.isNotEmpty()) "\n\n📺 Yayın Akışı:\n" + programs.joinToString("\n") else ""
        }.getOrElse { "" }
    }

    // --- M3U Parser ---
    private suspend fun parsePlaylist(): List<PlaylistItem> {
        val response = app.get(mainUrl).text
        val items = mutableListOf<PlaylistItem>()
        var lastInf: String? = null

        response.lines().forEach { line ->
            if (line.startsWith("#EXTINF")) {
                lastInf = line
            } else if (line.isNotEmpty() && !line.startsWith("#") && lastInf != null) {
                val title = lastInf?.split(",")?.lastOrNull()?.trim()
                val logo = Regex("""tvg-logo="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                val tvgId = Regex("""tvg-id="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                val group = Regex("""group-title="([^"]*)"""").find(lastInf!!)?.groupValues?.get(1)
                
                items.add(PlaylistItem(title, line.trim(), logo, tvgId, group))
                lastInf = null
            }
        }
        return items
    }

    // --- Ana Sayfa: Kategorileri "Dizi" olarak listele ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allItems = parsePlaylist()
        val categories = allItems.groupBy { it.group ?: "Genel" }

        val responses = categories.map { (name, items) ->
            val poster = items.firstOrNull { !it.logo.isNullOrBlank() }?.logo ?: DEFAULT_POSTER_URL
            val data = CategoryData(name, poster, items).toJson()

            newAnimeSearchResponse(name, data) {
                this.posterUrl = poster
                this.type = TvType.TvSeries
            }
        }

        return newHomePageResponse(listOf(HomePageList("Tüm Kategoriler", responses)), false)
    }

    // --- Dizi Detayı: Kategorideki Kanalları "Bölüm" yap ---
    override suspend fun load(url: String): LoadResponse {
        val cat = parseJson<CategoryData>(url)
        
        val episodesList = cat.items.mapIndexed { index, item ->
            // Bölüm verisi olarak kanalın tüm detaylarını gömüyoruz
            val epData = item.toJson() 
            
            newEpisode(epData) {
                this.name = item.title
                this.episode = index + 1
                this.season = 1
                this.posterUrl = item.logo ?: cat.poster
                this.description = "Kanal: ${item.title}\nKategori: ${cat.name}"
            }
        }

        return newAnimeLoadResponse(cat.name, url, TvType.TvSeries) {
            this.posterUrl = cat.poster
            this.episodes = mapOf(DubStatus.Subbed to episodesList)
            this.plot = "${cat.name} kategorisinde ${cat.items.size} kanal bulundu."
        }
    }

    // --- Video Oynatıcı: Link ve EPG ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val item = parseJson<PlaylistItem>(data)
        if (item.url.isNullOrBlank()) return false

        // Yayın akışını sadece link yüklenirken loglara veya plot'a eklemek istersen fetchEpg kullanabilirsin.
        // Ancak loadLinks içinde sadece link döndürülür.
        
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${item.title} Kaynak 1",
                url = item.url,
                type = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.P1080.value
            }
        )
        return true
    }
}
