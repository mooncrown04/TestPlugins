 package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
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

    // --- EPG YÜKLEME VE ÖNBELLEKLEME ---
    private suspend fun loadEpgData(): EpgData {
        val cached = cachedEpgData
        if (cached != null) return cached
        
        return epgMutex.withLock {
            val cachedInner = cachedEpgData
            if (cachedInner != null) return@withLock cachedInner
            
            try {
                val response = app.get(epgUrl).text
                if (response.isBlank()) return@withLock EpgData(errorMessage = "EPG Boş")
                
                val parsed = EpgXmlParser().parseEPG(response)
                cachedEpgData = parsed
                parsed
            } catch (e: OutOfMemoryError) {
                Log.e("EPG", "Bellek yetersiz, EPG yüklenemedi")
                EpgData(errorMessage = "Bellek Yetersiz")
            } catch (e: Exception) {
                Log.e("EPG", "EPG hatası: ${e.message}")
                EpgData(errorMessage = e.message)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val playlistText = app.get(mainUrl).text
            val kanallar = IptvPlaylistParser().parseM3U(playlistText)

            val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { entry ->
                val groupName = entry.key
                val show = entry.value.mapNotNull { kanal ->
                    val streamurl = kanal.url ?: return@mapNotNull null
                    val tvgId = kanal.attributes["tvg-id"] ?: ""

                    newLiveSearchResponse(
                        kanal.title ?: "Kanal",
                        LoadData(streamurl, kanal.title ?: "", kanal.attributes["tvg-logo"] ?: "", groupName, kanal.attributes["tvg-country"] ?: "", tvgId).toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = kanal.attributes["tvg-logo"]
                    }
                }
                HomePageList(groupName, show, isHorizontalImages = true)
            }
            newHomePageResponse(homePageLists, hasNext = false)
        } catch (e: Exception) {
            newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val loadData = parseJson<LoadData>(url)
            val epgData = loadEpgData()
            
            val normalizedTvgId = loadData.tvgId.lowercase()
            val programs = epgData.programs[normalizedTvgId] ?: emptyList()
            val currentTime = System.currentTimeMillis()

            // EPG Bilgisini Oluşturma (Xmltv sınıfındaki mantık)
            val epgPlotText = if (epgData.errorMessage != null) {
                "\n\n--- EPG HATA ---\n${epgData.errorMessage}"
            } else if (programs.isNotEmpty()) {
                val localTimeZone = TimeZone.getDefault()
                val nowCal = Calendar.getInstance().apply { timeZone = localTimeZone }
                val nowTime = String.format("%02d:%02d", nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE))

                val formattedPrograms = programs
                    .filter { it.end > currentTime } // Bitmemiş programları göster
                    .sortedBy { it.start }
                    .take(6) // Gelecek 6 programı al
                    .joinToString("\n") { prog ->
                        val startCal = Calendar.getInstance().apply { 
                            timeInMillis = prog.start
                            timeZone = localTimeZone 
                        }
                        val startTime = String.format("%02d:%02d", startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE))
                        "[$startTime] ${prog.name}"
                    }
                "\n\n--- YAYIN AKIŞI (Saat: $nowTime) ---\n$formattedPrograms"
            } else {
                "\n\n--- Yayın akışı bulunamadı (ID: $normalizedTvgId) ---"
            }

            newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
                this.posterUrl = loadData.poster
                this.plot = "» ${loadData.group} | ${loadData.nation} «$epgPlotText"
                this.tags = listOf(loadData.group, loadData.nation)
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val loadData = parseJson<LoadData>(data)
            callback.invoke(newExtractorLink(this.name, this.name, loadData.url, ExtractorLinkType.M3U8) { this.quality = Qualities.Unknown.value })
            true
        } catch (e: Exception) { false }
    }

    // --- DATA VE PARSER SINIFLARI ---
    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String, val tvgId: String = "")
    data class EpgProgram(val name: String, val start: Long, val end: Long, val channel: String)
    data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap(), val errorMessage: String? = null)

    class EpgXmlParser {
        fun parseEPG(xml: String): EpgData {
            val programs = mutableListOf<EpgProgram>()
            // Daha sağlam bir regex (Xmltv sınıfına benzer)
            val programmeRegex = Regex("""<programme start="([^"]*)" stop="([^"]*)" channel="([^"]*)">.*?<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
            
            programmeRegex.findAll(xml).forEach { m ->
                val start = parseXmlDate(m.groupValues[1])
                val end = parseXmlDate(m.groupValues[2])
                val channelId = m.groupValues[3].lowercase()
                val title = m.groupValues[4].replace("<![CDATA[", "").replace("]]>", "").trim()
                
                if (start > 0L && end > 0L) {
                    programs.add(EpgProgram(title, start, end, channelId))
                }
            }
            return EpgData(programs.groupBy { it.channel })
        }

        private fun parseXmlDate(dateString: String): Long {
            return try {
                val datePart = if (dateString.length >= 14) dateString.substring(0, 14) else return 0L
                val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
                sdf.timeZone = TimeZone.getTimeZone("UTC") // Standart XMLTV UTC'dir
                sdf.parse(datePart)?.time ?: 0L
            } catch (e: Exception) { 0L }
        }
    }
}

// M3U PARSER
data class Playlist(val items: List<PlaylistItem> = emptyList())
data class PlaylistItem(val title: String? = null, val attributes: Map<String, String> = emptyMap(), val url: String? = null)

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist {
        val playlistItems = mutableListOf<PlaylistItem>()
        var currentAttributes = mutableMapOf<String, String>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                currentAttributes = mutableMapOf()
                Regex("([\\w-]+)=\"([^\"]*)\"").findAll(trimmed).forEach { m ->
                    currentAttributes[m.groupValues[1]] = m.groupValues[2]
                }
                playlistItems.add(PlaylistItem(title = trimmed.split(",").lastOrNull()?.trim(), attributes = currentAttributes))
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && playlistItems.isNotEmpty()) {
                playlistItems[playlistItems.lastIndex] = playlistItems.last().copy(url = trimmed)
            }
        }
        return Playlist(playlistItems)
    }
}
