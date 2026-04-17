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
    override var mainUrl = "https://dl.dropbox.com/scl/fi/r4p9v7g76ikwt8zsyuhyn/sile.m3u?rlkey=esnalbpm4kblxgkvym51gjokm"
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"

    @Volatile
    private var cachedEpgData: EpgData? = null
    private val epgMutex = Mutex()

    override var name = "ANİME-TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    // --- EPG VERİSİNİ GÜVENLİ ÇEKME ---
    private suspend fun loadEpgData(): EpgData {
        val cached = cachedEpgData
        if (cached != null) return cached
        
        return epgMutex.withLock {
            val cachedInner = cachedEpgData
            if (cachedInner != null) return@withLock cachedInner
            
            try {
                val response = app.get(epgUrl, timeout = 30).text
                if (response.isBlank()) return@withLock EpgData()
                
                val parsed = EpgXmlParser().parseEPG(response)
                cachedEpgData = parsed
                parsed
            } catch (e: OutOfMemoryError) {
                EpgData(errorMessage = "EPG dosyası bellek için çok büyük.")
            } catch (e: Exception) {
                EpgData(errorMessage = "EPG yüklenemedi: ${e.message}")
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val playlistText = app.get(mainUrl).text
            val kanallar = IptvPlaylistParser().parseM3U(playlistText)

            val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { group ->
                val title = group.key
                val show = group.value.mapNotNull { kanal ->
                    val streamurl = kanal.url ?: return@mapNotNull null
                    val channelname = kanal.title ?: "Bilinmeyen Kanal"
                    val posterurl = kanal.attributes["tvg-logo"] ?: ""
                    val chGroup = kanal.attributes["group-title"] ?: ""
                    val nation = kanal.attributes["tvg-country"] ?: ""
                    val tvgId = kanal.attributes["tvg-id"] ?: ""

                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, chGroup, nation, tvgId).toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }
                HomePageList(title, show, isHorizontalImages = true)
            }
            newHomePageResponse(homePageLists, hasNext = false)
        } catch (e: Exception) {
            newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            kanallar.items.filter { it.title?.contains(query, ignoreCase = true) == true }.map { kanal ->
                newLiveSearchResponse(
                    kanal.title ?: "",
                    LoadData(kanal.url ?: "", kanal.title ?: "", kanal.attributes["tvg-logo"] ?: "", kanal.attributes["group-title"] ?: "", kanal.attributes["tvg-country"] ?: "", kanal.attributes["tvg-id"] ?: "").toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = kanal.attributes["tvg-logo"]
                    this.lang = kanal.attributes["tvg-country"]
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val loadData = fetchDataFromUrlOrJson(url)
            val epgData = loadEpgData()
            
            val normalizedTvgId = loadData.tvgId.lowercase()
            val programs = epgData.programs[normalizedTvgId] ?: emptyList()
            val currentTime = System.currentTimeMillis()

            val epgPlotText = if (programs.isNotEmpty()) {
                val localTimeZone = TimeZone.getDefault()
                val nowCal = Calendar.getInstance().apply { timeZone = localTimeZone }
                val nowTime = String.format("%02d:%02d", nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE))

                val formattedPrograms = programs
                    .filter { it.end > currentTime }
                    .take(6)
                    .joinToString("\n") { prog ->
                        val startCal = Calendar.getInstance().apply { 
                            timeInMillis = prog.start
                            timeZone = localTimeZone 
                        }
                        val startTime = String.format("%02d:%02d", startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE))
                        "[$startTime] ${prog.name}"
                    }
                "\n\n--- YAYIN AKIŞI (Saat: $nowTime) ---\n$formattedPrograms"
            } else ""

            val displayInfo = if (loadData.group == "NSFW") {
                "⚠️🔞 » ${loadData.group} | ${loadData.nation} « 🔞⚠️"
            } else {
                "» ${loadData.group} | ${loadData.nation} «"
            }

            newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
                this.posterUrl = loadData.poster
                this.plot = displayInfo + epgPlotText
                this.tags = listOf(loadData.group, loadData.nation)
            }
        } catch (e: Exception) { null }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val loadData = fetchDataFromUrlOrJson(data)
            callback.invoke(
                newExtractorLink(this.name, this.name, loadData.url, ExtractorLinkType.M3U8) {
                    this.quality = Qualities.Unknown.value
                }
            )
            true
        } catch (e: Exception) { false }
    }

    // --- DATA VE PARSER SINIFLARI ---

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String, val tvgId: String = "")

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        return try {
            if (data.startsWith("{")) {
                parseJson<LoadData>(data)
            } else {
                val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
                val kanal = kanallar.items.first { it.url == data }
                LoadData(kanal.url ?: "", kanal.title ?: "", kanal.attributes["tvg-logo"] ?: "", kanal.attributes["group-title"] ?: "", kanal.attributes["tvg-country"] ?: "", kanal.attributes["tvg-id"] ?: "")
            }
        } catch (e: Exception) { LoadData("", "", "", "", "", "") }
    }

    data class EpgProgram(val name: String, val start: Long, val end: Long, val channel: String)
    data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap(), val errorMessage: String? = null)

    class EpgXmlParser {
        fun parseEPG(xml: String): EpgData {
            val programs = mutableListOf<EpgProgram>()
            val progRegex = Regex("""<programme start="([^"]*)" stop="([^"]*)" channel="([^"]*)">.*?<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
            progRegex.findAll(xml).forEach { m ->
                val start = parseXmlDate(m.groupValues[1])
                val end = parseXmlDate(m.groupValues[2])
                val title = m.groupValues[4].replace("<![CDATA[", "").replace("]]>", "").trim()
                programs.add(EpgProgram(title, start, end, m.groupValues[3].lowercase()))
            }
            return EpgData(programs.groupBy { it.channel })
        }
        private fun parseXmlDate(s: String): Long {
            return try { SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s.substring(0, 14))?.time ?: 0L } catch (e: Exception) { 0L }
        }
    }
}

// --- M3U PARSER (Senin çalışan parser'ın) ---

data class Playlist(val items: List<PlaylistItem> = emptyList())
data class PlaylistItem(val title: String? = null, val attributes: Map<String, String> = emptyMap(), val headers: Map<String, String> = emptyMap(), val url: String? = null, val userAgent: String? = null)

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (reader.readLine()?.startsWith("#EXTM3U") != true) return Playlist()
        val playlistItems = mutableListOf<PlaylistItem>()
        var line: String? = reader.readLine()
        while (line != null) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                when {
                    trimmedLine.startsWith("#EXTINF") -> {
                        val title = trimmedLine.split(",").lastOrNull()?.trim()
                        val attributes = trimmedLine.getM3uAttributes()
                        playlistItems.add(PlaylistItem(title = title, attributes = attributes))
                    }
                    !trimmedLine.startsWith("#") -> {
                        if (playlistItems.isNotEmpty()) {
                            val lastIdx = playlistItems.lastIndex
                            playlistItems[lastIdx] = playlistItems[lastIdx].copy(url = trimmedLine)
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }
    private fun String.getM3uAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        Regex("([\\w-]+)=\"([^\"]*)\"").findAll(this).forEach { m -> attributes[m.groupValues[1]] = m.groupValues[2] }
        return attributes
    }
}
