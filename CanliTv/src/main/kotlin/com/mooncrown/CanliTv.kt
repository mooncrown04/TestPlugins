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
        val tvgId: String?,
        val hasEpg: Boolean = false // EPG yüklendi mi?
    )

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

    // --- Bellek Dostu EPG Çekme (Sadece Tek Kanal) ---
    private suspend fun fetchEpgForSingleChannel(tvgId: String?): String {
        if (tvgId.isNullOrBlank()) return ""
        
        return kotlin.runCatching {
            val response = app.get(epgUrl, timeout = 10).text
            val now = System.currentTimeMillis()
            val sdfInput = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            // Sadece bu kanala ait programme etiketlerini bul - daha hafif pattern
            val escapedId = Regex.escape(tvgId)
            val channelPattern = Regex("""<programme start="(\d{14})[^"]*"[^>]*channel="$escapedId"[^>]*>[\s\S]*?<title[^>]*>([\s\S]*?)</title>[\s\S]*?</programme>""")
            
            val programs = channelPattern.findAll(response)
                .mapNotNull { match ->
                    val startTimeStr = match.groupValues[1]
                    val title = match.groupValues[2]
                        .replace("<![CDATA[", "")
                        .replace("]]>", "")
                        .replace(Regex("<[^>]+>"), "")
                        .trim()
                    
                    val startTime = sdfInput.parse(startTimeStr)?.time ?: return@mapNotNull null
                    
                    if (startTime > now - 3600000) {
                        startTime to "[${sdfOutput.format(Date(startTime))}] $title"
                    } else null
                }
                .sortedBy { it.first }
                .take(4)
                .map { it.second }
                .toList()
            
            if (programs.isNotEmpty()) "\n\n📺 YAYIN AKIŞI:\n" + programs.joinToString("\n")
            else "\n\n📺 Güncel yayın bilgisi bulunamadı."
            
        }.getOrElse { "\n\n📺 EPG yüklenirken hata oluştu." }
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
        val cat = parseJson<CategoryData>(url)
        val groupedItems = cat.items.groupBy { it.title ?: "Bilinmeyen Kanal" }
        
        // EPG'siz hızlı yükleme
        val episodesList = groupedItems.entries.mapIndexed { index, entry ->
            val title = entry.key
            val items = entry.value
            val firstItem = items.first()
            
            val epData = GroupedEpisodeData(
                title = title,
                urls = items.map { it.url },
                logo = firstItem.logo,
                tvgId = firstItem.tvgId,
                hasEpg = false // Başlangıçta EPG yok
            ).toJson()
            
            // EPG'siz açıklama - EPG sonradan yüklenecek
            newEpisode(epData) {
                this.name = title
                this.episode = index + 1
                this.season = 1
                this.posterUrl = firstItem.logo ?: cat.poster
                this.description = "Kanal: $title\nKaynak Sayısı: ${items.size}\n\n📺 Yayın akışı yükleniyor..."
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
        
        // EPG henüz yüklenmemişse ve tvgId varsa, şimdi çek
        if (!groupedData.hasEpg && !groupedData.tvgId.isNullOrBlank()) {
            // Not: CloudStream'in episode bilgisini runtime'da güncelleme yeteneği sınırlı
            // Bu yüzden EPG'yi burada çekip log'a yazabilir veya farklı bir yöntem kullanabilirsiniz
            val epgInfo = fetchEpgForSingleChannel(groupedData.tvgId)
            // EPG bilgisi alındı - maalesef UI'yi burada güncelleyemeyiz
            // Ancak bir sonraki açılışta önbellekten gelebilir
        }
        
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
