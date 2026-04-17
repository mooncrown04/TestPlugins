package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.text.SimpleDateFormat
import java.util.*

class NeonSpor : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown/refs/heads/main/guncel_liste.m3u"
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"

    override var name = "EPG-TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    // EPG Çekme Fonksiyonu (Bellek Dostu)
    private suspend fun getEpgForChannel(tvgId: String): String {
        if (tvgId.isBlank()) return ""
        return kotlin.runCatching {
            val response = app.get(epgUrl, timeout = 8).text
            if (response.isBlank()) return ""

            val now = System.currentTimeMillis()
            val sdfInput = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
            val programs = mutableListOf<String>()

            val pattern = """<programme start="([^"]*)"[^>]*channel="${Regex.escape(tvgId)}">.*?<title[^>]*>(.*?)</title>"""
            Regex(pattern, RegexOption.DOT_MATCHES_ALL).findAll(response).forEach { m ->
                val startTime = sdfInput.parse(m.groupValues[1].substring(0, 14))?.time ?: 0L
                if (startTime > now - 3600000) { 
                    val title = m.groupValues[2].replace("<![CDATA[", "").replace("]]>", "").trim()
                    programs.add("[${sdfOutput.format(Date(startTime))}] $title")
                }
            }

            if (programs.isNotEmpty()) {
                "\n\n--- YAYIN AKIŞI ---\n" + programs.take(4).joinToString("\n")
            } else ""
        }.getOrElse { "" }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return kotlin.runCatching {
            val playlistText = app.get(mainUrl).text
            val items = mutableListOf<PlaylistItem>()
            
            var currentAttr = mutableMapOf<String, String>()
            playlistText.lines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("#EXTINF")) {
                    currentAttr = mutableMapOf()
                    Regex("([\\w-]+)=\"([^\"]*)\"").findAll(t).forEach { 
                        currentAttr[it.groupValues[1]] = it.groupValues[2] 
                    }
                    val title = t.split(",").lastOrNull()?.trim() ?: "Kanal"
                    items.add(PlaylistItem(title, currentAttr))
                } else if (t.isNotEmpty() && !t.startsWith("#") && items.isNotEmpty()) {
                    items[items.lastIndex] = items.last().copy(url = t)
                }
            }

            val homePageLists = items.groupBy { it.attributes["group-title"] ?: "Genel" }.map { group ->
                val responses = group.value.mapNotNull { kanal ->
                    val streamUrl = kanal.url ?: return@mapNotNull null
                    
                    val data = LoadData(
                        url = streamUrl,
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

            newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
                this.posterUrl = loadData.poster
                this.plot = "Kategori: ${loadData.group}$epgInfo"
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
            
            // İstediğin link ismi formatı: Kanal Adı + Kaynak No
            val linkName = "${loadData.title} Kaynak 1"
            
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = linkName, 
                    url = loadData.url,
                    type = ExtractorLinkType.M3U8
                ) {
                    // Kaliteyi bilinmiyor olarak işaretliyoruz (Auto)
                    quality = Qualities.Unknown.value
                }
            )
            true
        }.getOrElse { false }
    }

    data class LoadData(
        val url: String, 
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
