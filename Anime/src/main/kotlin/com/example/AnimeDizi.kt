package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NeonSpor : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/chn3cr4g67hnah3w2c19m/eyuptv.m3u?rlkey=2ubdclpcrhkcgj8iogwipuj3r"
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"

    @Volatile
    private var cachedEpgData: EpgData? = null
    private val epgMutex = Mutex()

    override var name = "ANİME-TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    private suspend fun loadEpgData(): EpgData {
        val cached = cachedEpgData
        if (cached != null) return cached
        
        return epgMutex.withLock {
            val cachedInner = cachedEpgData
            if (cachedInner != null) return@withLock cachedInner
            
            try {
                val response = app.get(epgUrl).text
                val parsed = EpgXmlParser().parseEPG(response)
                cachedEpgData = parsed
                parsed
            } catch (e: Exception) {
                Log.e("EPG", "EPG yüklenemedi: ${e.message}")
                EpgData()
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlistText = app.get(mainUrl).text
        val kanallar = IptvPlaylistParser().parseM3U(playlistText)

        val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { entry ->
            val groupName = entry.key
            val channelList = entry.value
            
            val show = channelList.mapNotNull { kanal ->
                val streamurl = kanal.url ?: return@mapNotNull null
                val channelname = kanal.title ?: "Bilinmeyen Kanal"
                val posterurl = kanal.attributes["tvg-logo"] ?: ""
                val tvgId = kanal.attributes["tvg-id"] ?: ""

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, groupName, kanal.attributes["tvg-country"] ?: "", tvgId).toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = posterurl
                }
            }
            HomePageList(groupName, show, isHorizontalImages = true)
        }
        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val playlistText = app.get(mainUrl).text
        val kanallar = IptvPlaylistParser().parseM3U(playlistText)

        return kanallar.items.filter { it.title?.contains(query, ignoreCase = true) == true }.map { kanal ->
            val streamurl = kanal.url ?: ""
            val channelname = kanal.title ?: ""
            val posterurl = kanal.attributes["tvg-logo"] ?: ""
            val tvgId = kanal.attributes["tvg-id"] ?: ""
            
            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, kanal.attributes["group-title"] ?: "Diğer", kanal.attributes["tvg-country"] ?: "", tvgId).toJson(),
                type = TvType.Live
            ) {
                this.posterUrl = posterurl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val epgData = loadEpgData()
        
        val normalizedTvgId = loadData.tvgId.lowercase()
        val programs = epgData.programs[normalizedTvgId] ?: emptyList()
        val currentTime = System.currentTimeMillis()

        val epgPlot = if (programs.isNotEmpty()) {
            "\n\n--- YAYIN AKIŞI ---\n" + programs
                .filter { it.end > currentTime }
                .take(5)
                .joinToString("\n") { prog ->
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val start = sdf.format(Date(prog.start))
                    "[$start] ${prog.name}"
                }
        } else {
            "\n\n--- Yayın akışı bulunamadı ---"
        }

        return newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
            this.posterUrl = loadData.poster
            this.plot = "» ${loadData.group} | ${loadData.nation} «$epgPlot"
            this.tags = listOf(loadData.group, loadData.nation)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = parseJson<LoadData>(data)
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                loadData.url,
                ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String, val tvgId: String = "")

    // --- EPG PARSER VE DATA SINIFLARI ---
    data class EpgProgram(val name: String, val start: Long, val end: Long, val channel: String)
    data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap())

    class EpgXmlParser {
        fun parseEPG(xml: String): EpgData {
            val programs = mutableListOf<EpgProgram>()
            val programmeRegex = Regex("<programme start=\"(.*?)\" stop=\"(.*?)\" channel=\"(.*?)\">.*?<title.*?>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            
            programmeRegex.findAll(xml).forEach { match ->
                val start = parseXmlTvDate(match.groupValues[1])
                val end = parseXmlTvDate(match.groupValues[2])
                val channel = match.groupValues[3].lowercase()
                val title = match.groupValues[4].replace("<![CDATA[", "").replace("]]>", "").trim()
                
                programs.add(EpgProgram(title, start, end, channel))
            }
            return EpgData(programs.groupBy { it.channel })
        }

        private fun parseXmlTvDate(dateString: String): Long {
            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            return try { format.parse(dateString.substring(0, 14))?.time ?: 0L } catch (e: Exception) { 0L }
        }
    }
}

// --- M3U PARSER VE DATA SINIFLARI ---
data class Playlist(val items: List<PlaylistItem> = emptyList())
data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val url: String? = null
)

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist {
        val playlistItems = mutableListOf<PlaylistItem>()
        val lines = content.lines()
        var currentAttributes = mutableMapOf<String, String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                currentAttributes = mutableMapOf()
                Regex("([\\w-]+)=\"([^\"]*)\"").findAll(trimmed).forEach { match ->
                    currentAttributes[match.groupValues[1]] = match.groupValues[2]
                }
                val title = trimmed.split(",").lastOrNull()?.trim()
                playlistItems.add(PlaylistItem(title = title, attributes = currentAttributes))
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                if (playlistItems.isNotEmpty()) {
                    val lastIdx = playlistItems.lastIndex
                    playlistItems[lastIdx] = playlistItems[lastIdx].copy(url = trimmed)
                }
            }
        }
        return Playlist(playlistItems)
    }
}
