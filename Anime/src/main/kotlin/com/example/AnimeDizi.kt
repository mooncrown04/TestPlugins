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
    // EPG URL'sini buraya ekledik
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"

    @Volatile
    private var cachedEpgData: EpgData? = null
    private val epgMutex = Mutex()

    override var name = "ANİME-TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    // --- EPG VERİSİNİ İNDİREN FONKSİYON ---
    private suspend fun loadEpgData(): EpgData {
        if (cachedEpgData != null) return cachedEpgData!!
        return epgMutex.withLock {
            if (cachedEpgData != null) return cachedEpgData!!
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

        val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { group ->
            val title = group.key
            val show = group.value.mapNotNull { kanal ->
                val streamurl = kanal.url ?: return@mapNotNull null
                val channelname = kanal.title ?: "Bilinmeyen Kanal"
                val posterurl = kanal.attributes["tvg-logo"] ?: ""
                val tvgId = kanal.attributes["tvg-id"] ?: "" // tvg-id'yi aldık

                newLiveSearchResponse(
                    channelname,
                    // tvgId bilgisini LoadData içine ekledik
                    LoadData(streamurl, channelname, posterurl, group.key, kanal.attributes["tvg-country"] ?: "", tvgId).toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = posterurl
                }
            }
            HomePageList(title, show, isHorizontalImages = true)
        }
        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val epgData = loadEpgData()
        
        // EPG Eşleştirme
        val normalizedTvgId = loadData.tvgId.lowercase()
        val programs = epgData.programs[normalizedTvgId] ?: emptyList()
        val currentTime = System.currentTimeMillis()

        val epgPlot = if (programs.isNotEmpty()) {
            "\n\n--- YAYIN AKIŞI ---\n" + programs
                .filter { it.end > currentTime } // Bitmemiş programları göster
                .take(5) // Gelecek 5 programı listele
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

    // LoadData sınıfına tvgId eklendi
    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String, val tvgId: String = "")

    // --- EPG PARSER SINIFLARI ---
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

    // Diğer fonksiyonlar (search, loadLinks, IptvPlaylistParser) aynı kalacak...
    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return kanallar.items.filter { it.title?.contains(query, ignoreCase = true) == true }.map { kanal ->
            newLiveSearchResponse(
                kanal.title ?: "",
                LoadData(kanal.url ?: "", kanal.title ?: "", kanal.attributes["tvg-logo"] ?: "", kanal.attributes["group-title"] ?: "", kanal.attributes["tvg-country"] ?: "", kanal.attributes["tvg-id"] ?: "").toJson(),
                type = TvType.Live
            ) { this.posterUrl = kanal.attributes["tvg-logo"] }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = parseJson<LoadData>(data)
        callback.invoke(newExtractorLink(this.name, this.name, loadData.url, ExtractorLinkType.M3U8) { this.quality = Qualities.Unknown.value })
        return true
    }
}
