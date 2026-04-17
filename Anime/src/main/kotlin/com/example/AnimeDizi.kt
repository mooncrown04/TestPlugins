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
    override val supportedTypes = setOf(TvType.Live)

    // EPG verisini çekerken oluşacak hataların uygulamayı kapatmasını engeller
    private suspend fun loadEpgData(): EpgData {
        return kotlin.runCatching {
            val cached = cachedEpgData
            if (cached != null) return@runCatching cached
            
            epgMutex.withLock {
                val cachedInner = cachedEpgData
                if (cachedInner != null) return@withLock cachedInner
                
                val response = app.get(epgUrl, timeout = 15).text
                if (response.isBlank()) return@withLock EpgData()
                
                val parsed = EpgXmlParser().parseEPG(response)
                cachedEpgData = parsed
                parsed
            }
        }.getOrElse { EpgData(errorMessage = "EPG Yüklenemedi") }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return kotlin.runCatching {
            val playlistText = app.get(mainUrl).text
            val kanallar = IptvPlaylistParser().parseM3U(playlistText)

            val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { group ->
                val show = group.value.mapNotNull { kanal ->
                    val streamurl = kanal.url ?: return@mapNotNull null
                    val tvgId = kanal.attributes["tvg-id"] ?: ""
                    
                    // KRİTİK: Tüm veriyi LoadData içine koyup JSON yapıyoruz ki 'load' içinde tekrar internete çıkmayalım
                    val data = LoadData(
                        streamurl, 
                        kanal.title ?: "Kanal", 
                        kanal.attributes["tvg-logo"] ?: "", 
                        group.key, 
                        kanal.attributes["tvg-country"] ?: "", 
                        tvgId
                    ).toJson()

                    newLiveSearchResponse(kanal.title ?: "Kanal", data, TvType.Live) {
                        this.posterUrl = kanal.attributes["tvg-logo"]
                    }
                }
                HomePageList(group.key, show, isHorizontalImages = true)
            }
            newHomePageResponse(homePageLists, false)
        }.getOrElse { newHomePageResponse(emptyList(), false) }
    }

    override suspend fun load(url: String): LoadResponse? {
        return kotlin.runCatching {
            val loadData = parseJson<LoadData>(url)
            val epgData = loadEpgData()
            
            val normalizedTvgId = loadData.tvgId.lowercase()
            val programs = epgData.programs[normalizedTvgId] ?: emptyList()
            val currentTime = System.currentTimeMillis()

            val epgPlotText = if (programs.isNotEmpty()) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val formatted = programs
                    .filter { it.end > currentTime }
                    .take(5)
                    .joinToString("\n") { "[${sdf.format(Date(it.start))}] ${it.name}" }
                "\n\n--- YAYIN AKIŞI ---\n$formatted"
            } else ""

            newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
                this.posterUrl = loadData.poster
                this.plot = "» ${loadData.group} | ${loadData.nation} «$epgPlotText"
            }
        }.getOrNull()
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return kotlin.runCatching {
            val loadData = parseJson<LoadData>(data)
            callback.invoke(
                newExtractorLink(this.name, this.name, loadData.url, ExtractorLinkType.M3U8) {
                    this.quality = Qualities.Unknown.value
                }
            )
            true
        }.getOrElse { false }
    }

    // --- YARDIMCI YAPILAR ---

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String, val tvgId: String)

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
            return try { SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).parse(s.substring(0, 14))?.time ?: 0L } catch (e: Exception) { 0L }
        }
    }
}

// STABİL M3U PARSER
data class PlaylistItem(val title: String? = null, val attributes: Map<String, String> = emptyMap(), val url: String? = null)
class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist {
        val items = mutableListOf<PlaylistItem>()
        var currentAttr = mutableMapOf<String, String>()
        content.lines().forEach { line ->
            val t = line.trim()
            if (t.startsWith("#EXTINF")) {
                currentAttr = mutableMapOf()
                Regex("([\\w-]+)=\"([^\"]*)\"").findAll(t).forEach { currentAttr[it.groupValues[1]] = it.groupValues[2] }
                items.add(PlaylistItem(t.split(",").lastOrNull()?.trim(), currentAttr))
            } else if (t.isNotEmpty() && !t.startsWith("#") && items.isNotEmpty()) {
                items[items.lastIndex] = items.last().copy(url = t)
            }
        }
        return Playlist(items)
    }
    data class Playlist(val items: List<PlaylistItem>)
}
