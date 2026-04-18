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

    // --- EPG Önbellek Değişkenleri ---
    private var cachedEpgData: Map<String, List<Pair<Long, String>>> = emptyMap()
    private var epgLastFetchTime: Long = 0
    private val EPG_CACHE_DURATION_MS = 30 * 60 * 1000 // 30 dakika
    private val EPG_MAX_PROGRAMS_PER_CHANNEL = 4

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

    data class GroupedEpisodeData(
        val title: String,
        val urls: List<String?>,
        val logo: String?,
        val tvgId: String?
    )

    // --- EPG Önbelleğini Güncelle ---
    private suspend fun updateEpgCache() {
        val now = System.currentTimeMillis()
        if (now - epgLastFetchTime < EPG_CACHE_DURATION_MS && cachedEpgData.isNotEmpty()) {
            return // Önbellek hâlâ geçerli
        }

        kotlin.runCatching {
            // EPG'yi küçük parçalar halinde indir ve parse et
            val response = app.get(epgUrl, timeout = 15).text
            val newCache = mutableMapOf<String, MutableList<Pair<Long, String>>>()
            
            val nowTime = System.currentTimeMillis()
            val sdfInput = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            
            // Daha verimli regex - sadece gerekli alanları çek
            val programmeRegex = Regex("""<programme start="(\d{14})[^"]*"[^>]*channel="([^"]*)">[\s\S]*?<title[^>]*>([\s\S]*?)</title>[\s\S]*?</programme>""")
            
            programmeRegex.findAll(response).forEach { match ->
                val startTimeStr = match.groupValues[1]
                val channelId = match.groupValues[2]
                val title = match.groupValues[3]
                    .replace("<![CDATA[", "")
                    .replace("]]>", "")
                    .replace(Regex("<[^>]+>"), "") // HTML etiketlerini temizle
                    .trim()
                
                val startTime = sdfInput.parse(startTimeStr)?.time ?: return@forEach
                
                // Sadece yakın zamandaki ve gelecekteki programları al
                if (startTime > nowTime - 3600000) {
                    val list = newCache.getOrPut(channelId) { mutableListOf() }
                    if (list.size < EPG_MAX_PROGRAMS_PER_CHANNEL * 2) { // Biraz fazla al, sonra filtrele
                        list.add(startTime to title)
                    }
                }
            }
            
            // Her kanal için sadece ilk 4 programı tut, zaman sıralı
            cachedEpgData = newCache.mapValues { (_, programs) ->
                programs.sortedBy { it.first }.take(EPG_MAX_PROGRAMS_PER_CHANNEL)
            }
            epgLastFetchTime = now
            
        }.onFailure {
            // Hata durumunda boş önbellek bırakma, eski veriyi koru veya boş bırak
            if (cachedEpgData.isEmpty()) {
                cachedEpgData = emptyMap()
            }
        }
    }

    // --- Önbellekten EPG Bilgisi Al ---
    private fun getEpgFromCache(tvgId: String?): String {
        if (tvgId.isNullOrBlank()) return "\n\n📺 Yayın akışı bilgisi yok."
        
        val programs = cachedEpgData[tvgId] ?: return "\n\n📺 Yayın akışı bilgisi bulunamadı."
        
        if (programs.isEmpty()) return "\n\n📺 Güncel yayın bilgisi bulunamadı."
        
        val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val formatted = programs.joinToString("\n") { (timestamp, title) ->
            "[${sdfOutput.format(Date(timestamp))}] $title"
        }
        
        return "\n\n📺 YAYIN AKIŞI:\n$formatted"
    }

    private suspend fun parsePlaylist(): List<PlaylistItem> {
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
        return items
    }

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
        return newHomePageResponse(listOf(HomePageList("Tüm Kategoriler", responses, isHorizontalImages = true)), false)
    }

    override suspend fun load(url: String): LoadResponse {
        // EPG önbelleğini güncelle (ana sayfada bir kez)
        updateEpgCache()
        
        val cat = parseJson<CategoryData>(url)
        val groupedItems = cat.items.groupBy { it.title ?: "Bilinmeyen Kanal" }
        
        val episodesList = groupedItems.entries.mapIndexed { index, entry ->
            val title = entry.key
            val items = entry.value
            val firstItem = items.first()
            
            val epData = GroupedEpisodeData(
                title = title,
                urls = items.map { it.url },
                logo = firstItem.logo,
                tvgId = firstItem.tvgId
            ).toJson()
            
            // Önbellekten hızlıca EPG al
            val epgInfo = getEpgFromCache(firstItem.tvgId)

            newEpisode(epData) {
                this.name = title
                this.episode = index + 1
                this.season = 1
                this.posterUrl = firstItem.logo ?: cat.poster
                this.description = "Kanal: $title\nKaynak Sayısı: ${items.size}$epgInfo"
            }
        }

        return newAnimeLoadResponse(cat.name, url, TvType.TvSeries) {
            this.posterUrl = cat.poster
            this.episodes = mutableMapOf(DubStatus.Subbed to episodesList)
            this.plot = "${cat.name} kategorisinde ${groupedItems.size} benzersiz içerik bulundu."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val groupedData = parseJson<GroupedEpisodeData>(data)
        
        groupedData.urls.forEachIndexed { index, link ->
            if (!link.isNullOrBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${groupedData.title} - Kaynak ${index + 1}",
                        url = link,
                        type = ExtractorLinkType.M3U8
                    ) {
                        quality = Qualities.P1080.value
                    }
                )
            }
        }
        return true
    }
}
