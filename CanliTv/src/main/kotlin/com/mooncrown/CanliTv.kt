package com.mooncrown

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.text.SimpleDateFormat
import java.util.*

class CanliTv : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown/refs/heads/main/guncel_liste.m3u"
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"

    override var name = "EPG-TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    // --- EPG ÖNBELLEK SİSTEMİ ---
    private var epgCache: String? = null
    private var epgCacheTime: Long = 0
    private val EPG_CACHE_DURATION = 5 * 60 * 1000 // 5 dakika önbellek
    
    // --- EPG'yi önbellekten veya indirerek getir ---
    private suspend fun getEpgData(): String {
        val now = System.currentTimeMillis()
        
        // Önbellek geçerli mi kontrol et
        if (epgCache != null && (now - epgCacheTime) < EPG_CACHE_DURATION) {
            return epgCache!!
        }
        
        // Yeni indir
        return kotlin.runCatching {
            val response = app.get(epgUrl, timeout = 15).text
            epgCache = response
            epgCacheTime = now
            response
        }.getOrElse { 
            epgCache ?: "" // Hata olursa eski önbelleği kullan veya boş döndür
        }
    }

    // --- Önbellekten kanalın EPG'sini parse et ---
    private suspend fun getEpgForChannel(tvgId: String): String {
        if (tvgId.isBlank()) return ""
        
        val epgData = getEpgData()
        if (epgData.isBlank()) return ""
        
        return kotlin.runCatching {
            val now = System.currentTimeMillis()
            val sdfInput = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
            val programs = mutableListOf<String>()
            
            // Sadece bu kanala ait programme etiketlerini bul
            val pattern = """<programme start="([^"]*)"[^>]*channel="${Regex.escape(tvgId)}">.*?<title[^>]*>(.*?)</title>"""
            
            Regex(pattern, RegexOption.DOT_MATCHES_ALL).findAll(epgData).forEach { m ->
                val startTime = sdfInput.parse(m.groupValues[1].substring(0, 14))?.time ?: 0L
                if (startTime > now - 3600000) { 
                    val title = m.groupValues[2]
                        .replace("<![CDATA[", "")
                        .replace("]]>", "")
                        .trim()
                    programs.add("[${sdfOutput.format(Date(startTime))}] $title")
                }
            }
            
            if (programs.isNotEmpty()) {
                "\n\n--- YAYIN AKIŞI ---\n" + programs.take(4).joinToString("\n")
            } else {
                ""
            }
        }.getOrElse { "" }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return kotlin.runCatching {
            val playlistText = app.get(mainUrl).text
            val rawItems = mutableListOf<PlaylistItem>()
            
            var currentAttr = mutableMapOf<String, String>()
            playlistText.lines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("#EXTINF")) {
                    currentAttr = mutableMapOf()
                    Regex("""([\w-]+)="([^"]*)"""").findAll(t).forEach { 
                        currentAttr[it.groupValues[1]] = it.groupValues[2] 
                    }
                    val title = t.split(",").lastOrNull()?.trim() ?: "Kanal"
                    rawItems.add(PlaylistItem(title, currentAttr))
                } else if (t.isNotEmpty() && !t.startsWith("#") && rawItems.isNotEmpty()) {
                    rawItems[rawItems.lastIndex] = rawItems.last().copy(url = t)
                }
            }

            // AYNI KANALLARI GRUPLAMA MANTIĞI
            val groupedChannels = rawItems.groupBy { it.title ?: "Bilinmeyen Kanal" }

            val homePageLists = rawItems.groupBy { it.attributes["group-title"] ?: "Genel" }.map { group ->
                val distinctChannels = group.value.distinctBy { it.title }
                
                val responses = distinctChannels.mapNotNull { kanal ->
                    val allLinksForThisChannel = groupedChannels[kanal.title]?.mapNotNull { it.url } ?: listOf()
                    if (allLinksForThisChannel.isEmpty()) return@mapNotNull null
                    
                    val data = LoadData(
                        urls = allLinksForThisChannel,
                        title = kanal.title ?: "Kanal",
                        poster = kanal.attributes["tvg-logo"] ?: "",
                        group = group.key,
                        tvgId = kanal.attributes["tvg-id"] ?: ""
                    ).toJson()

                    newLiveSearchResponse(kanal.title ?: "Kanal", data, TvType.Live) {
                        this.posterUrl = kanal.attributes["tvg-logo"]
                    }
                }
                HomePageList(group.key, responses, isHorizontalImages = true)
            }
            newHomePageResponse(homePageLists, false)
        }.getOrElse { newHomePageResponse(emptyList(), false) }
    }

    override suspend fun load(url: String): LoadResponse? {
        return kotlin.runCatching {
            val loadData = parseJson<LoadData>(url)
            val epgInfo = getEpgForChannel(loadData.tvgId)

            // DÜZELTME: newLiveStreamLoadResponse doğru parametrelerle
            newLiveStreamLoadResponse(
                name = loadData.title,
                url = loadData.urls.firstOrNull() ?: "",
                referer = url,
                type = TvType.Live
            ) {
                this.posterUrl = loadData.poster
                this.plot = "Kategori: ${loadData.group}\nKaynak Sayısı: ${loadData.urls.size}$epgInfo"
            }
        }.getOrNull()
    }

    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return kotlin.runCatching {
            val loadData = parseJson<LoadData>(data)
            
            loadData.urls.forEachIndexed { index, videoUrl ->
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${loadData.title} Kaynak ${index + 1}",
                        url = videoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        quality = Qualities.Unknown.value
                    }
                )
            }
            true
        }.getOrElse { false }
    }

    // DÜZELTME: data class - val val yazım hatası giderildi
    data class LoadData(
        val urls: List<String>,
        val title: String, 
        val poster: String, 
        val group: String, 
        val tvgId: String
    )
    
    data class PlaylistItem(
        val title: String?, 
        val attributes: Map<String, String>, 
        val url: String? = null
    )
}
