package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.text.*
import kotlin.collections.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.Calendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock 

// ***************************************************************
// 1. VERİ MODELLERİ
// ***************************************************************
data class ProgramInfo(
    val name: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val start: Long,
    val end: Long
)
data class EpgProgram(val name: String, val description: String?, val start: Long, val end: Long, val channel: String)
// EpgData artık bir hata mesajı da taşıyacak
data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap(), val errorMessage: String? = null)

data class PlaylistItem(
    val title: String,
    val url: String,
    val description: String? = null,
    val nation: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)
data class Playlist(val items: List<PlaylistItem> = emptyList())


// ***************************************************************
// 2. PARSER SINIFLARI
// ***************************************************************

class EpgXmlParser { 
    fun parseEPG(xml: String): EpgData {
        if (xml.isBlank()) return EpgData(errorMessage = "XML içeriği boş.")
        val programs = mutableListOf<EpgProgram>()
        
        val programmeRegex = Regex("<programme\\s+[^>]*>", RegexOption.DOT_MATCHES_ALL) 
        val channelRegex = Regex("channel=\"([^\"]+)\"")
        val startRegex = Regex("start=\"([^\"]+)\"")
        val stopRegex = Regex("stop=\"([^\"]+)\"")

        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val descRegex = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)
        
        val blocks = xml.split("</programme>")

        for (block in blocks) {
            val programmeMatch = programmeRegex.find(block) ?: continue
            val attributesString = programmeMatch.value

            val startStr = startRegex.find(attributesString)?.groupValues?.getOrNull(1)?.trim()
            val endStr = stopRegex.find(attributesString)?.groupValues?.getOrNull(1)?.trim()
            val channelId = channelRegex.find(attributesString)?.groupValues?.getOrNull(1)?.trim()

            if (startStr == null || endStr == null || channelId == null) continue

            val title = titleRegex.find(block)?.groupValues?.getOrNull(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "")
            val description = descRegex.find(block)?.groupValues?.getOrNull(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "")

            val startTime = startStr.take(14).toLongOrNull()?.let { parseXmlTvDate(it) } ?: 0L
            val endTime = endStr.take(14).toLongOrNull()?.let { parseXmlTvDate(it) } ?: 0L

            if (startTime > 0L && endTime > 0L && !title.isNullOrBlank()) {
                programs.add(
                    EpgProgram(
                        name = title,
                        description = description,
                        start = startTime,
                        end = endTime,
                        channel = channelId
                    )
                )
            }
        }
        val groupedPrograms = programs.groupBy { it.channel }
        return EpgData(groupedPrograms)
    }

    private fun parseXmlTvDate(dateLong: Long): Long {
        return try {
            val year = (dateLong / 1000000000000L) % 10000
            val month = (dateLong / 10000000000L) % 100
            val day = (dateLong / 100000000L) % 100
            val hour = (dateLong / 1000000L) % 100
            val minute = (dateLong / 10000L) % 100
            val second = (dateLong / 100L) % 100
            val calendar = Calendar.getInstance().apply {
                set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }
}


class XmlPlaylistParser {
    private val nationRegex = Regex("nation\\s*:\\s*(.*)", RegexOption.IGNORE_CASE)
    private val channelRegex = Regex("<channel>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL)
    private val titleRegex = Regex("<title>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</title>", RegexOption.DOT_MATCHES_ALL)
    private val logoRegex = Regex("<logo_30x30>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</logo_30x30>", RegexOption.DOT_MATCHES_ALL)
    private val streamUrlRegex = Regex("<stream_url>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</stream_url>", RegexOption.DOT_MATCHES_ALL)
    private val descriptionRegex = Regex("<description>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</description>", RegexOption.DOT_MATCHES_ALL)
    private val tvgIdRegex = Regex("<tvg_id>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</tvg_id>", RegexOption.DOT_MATCHES_ALL)


    fun parseXML(content: String): Playlist {
        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        for (channelMatch in channelRegex.findAll(content)) {
            val channelBlock = channelMatch.groupValues.getOrNull(1) ?: continue
            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = streamUrlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val description = descriptionRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val tvgId = tvgIdRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val nationMatch = description?.let { nationRegex.find(it) }
            val nation = nationMatch?.groupValues?.getOrNull(1)?.trim()

            if (!title.isNullOrBlank() && !url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                val attributesMap = mutableMapOf<String, String>()
                attributesMap["tvg-logo"] = logo ?: ""
                attributesMap["group-title"] = "XML Kanalları"
                tvgId?.let { attributesMap["tvg-id"] = it }

                playlistItems.add(
                    PlaylistItem(
                        title = title,
                        url = url,
                        description = description,
                        nation = nation,
                        attributes = attributesMap.toMap(),
                        headers = emptyMap(),
                        userAgent = null
                    )
                )
            }
        }
        return Playlist(playlistItems)
    }
}


// ***************************************************************
// 3. ANA API SINIFI
// ***************************************************************

class Xmltv : MainAPI() {
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/vg40bpys8ym1jjrcuv1wp/XMLTvcs.xml?rlkey=7g2chxiol35z6kg6b36c4nyv8"
    private val tertiaryXmlUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b-2.xml"
    private val epgUrl = "https://raw.githubusercontent.com/braveheart1983/tvg-macther/refs/heads/main/tr-epg.xml"
    
    @Volatile
    private var cachedEpgData: EpgData? = null 
    private val epgMutex = Mutex() 

    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    data class GroupedChannelData(
        val title: String,
        val posterUrl: String,
        val description: String? = null,
        val nation: String? = null,
        val items: List<PlaylistItem>
    )

    // ⭐ loadEpgData'yı EPGData objesinde hata mesajı döndürecek şekilde güncelledik
    private suspend fun loadEpgData(): EpgData {
        if (cachedEpgData != null) return cachedEpgData!!
        
        return epgMutex.withLock {
            if (cachedEpgData != null) return cachedEpgData!!
            
            var epgResponse: String? = null
            
            // ADIM 1: Ağ isteğini dene
            try {
                epgResponse = app.get(epgUrl).text 
                if (epgResponse.isNullOrBlank()) {
                    val error = "HATA: EPG URL'den çekildi ancak içerik boş."
                    Log.e("Xmltv", error)
                    return@withLock EpgData(errorMessage = error)
                }
            } catch (e: Exception) {
                val error = "HATA: EPG URL'den çekilemedi. Bağlantı veya sunucu hatası: ${e.message}"
                Log.e("Xmltv", error, e)
                return@withLock EpgData(errorMessage = error)
            }
            
            // ADIM 2: Parser işlemini dene
            try {
                val epgData = EpgXmlParser().parseEPG(epgResponse!!) 
                
                if (epgData.errorMessage != null) {
                    val error = "HATA: EPG Parser hatası. ${epgData.errorMessage}"
                    Log.e("Xmltv", error)
                    return@withLock EpgData(errorMessage = error)
                }
                
                cachedEpgData = epgData
                return@withLock epgData
                
            } catch (e: OutOfMemoryError) {
                val error = "KRİTİK HATA: Bellek Yetersizliği! XML dosyası cihaz için çok büyük. Boyut: ${epgResponse?.length ?: 0} bayt."
                Log.e("Xmltv", error, e)
                return@withLock EpgData(errorMessage = error) 
            } catch (e: Exception) {
                val error = "HATA: EPG Parser hatası! XML formatı bozuk veya eksik: ${e.message}"
                Log.e("Xmltv", error, e)
                return@withLock EpgData(errorMessage = error)
            }
        }
    }

    private fun createGroupedChannelItems(playlist: Playlist, query: String? = null): List<SearchResponse> {
        val groupedByTitle = playlist.items.groupBy { it.title }
        return groupedByTitle.mapNotNull { (title, items) ->
            if (items.isEmpty()) return@mapNotNull null
            if (query != null && !title.contains(query, ignoreCase = true)) {
                return@mapNotNull null
            }
            val firstItem = items.first()
            val logoUrl = firstItem.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: ""
            val groupedData = GroupedChannelData(
                title = title,
                posterUrl = logoUrl,
                items = items,
                description = firstItem.description,
                nation = firstItem.nation
            )
            val dataUrl = groupedData.toJson()
            newLiveSearchResponse(
                name = title,
                url = dataUrl,
                type = TvType.Live
            ) {
                this.posterUrl = logoUrl
                groupedData.nation?.let { this.lang = it }
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homepageLists = mutableListOf<HomePageList>()
        
        try {
            val primaryResponse = app.get(mainUrl).text
            homepageLists.add(HomePageList("Favori Listem", createGroupedChannelItems(XmlPlaylistParser().parseXML(primaryResponse))))
        } catch (e: Exception) { Log.e("Xmltv", "Birincil URL yüklenemedi: ${e.message}") }

        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            homepageLists.add(HomePageList("Diğer Kanallar", createGroupedChannelItems(XmlPlaylistParser().parseXML(secondaryResponse))))
        } catch (e: Exception) { Log.e("Xmltv", "İkincil URL yüklenemedi: ${e.message}") }
        
        try {
            val tertiaryResponse = app.get(tertiaryXmlUrl).text
            homepageLists.add(HomePageList("Favori Listem 2", createGroupedChannelItems(XmlPlaylistParser().parseXML(tertiaryResponse))))
        } catch (e: Exception) { Log.e("Xmltv", "Üçüncül URL yüklenemedi: ${e.message}") }

        return newHomePageResponse(homepageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allResults = mutableListOf<SearchResponse>()
        try {
            val primaryResponse = app.get(mainUrl).text
            allResults.addAll(createGroupedChannelItems(XmlPlaylistParser().parseXML(primaryResponse), query))
        } catch (e: Exception) { Log.e("Xmltv", "Arama için birincil URL yüklenemedi: ${e.message}") }

        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            allResults.addAll(createGroupedChannelItems(XmlPlaylistParser().parseXML(secondaryResponse), query))
        } catch (e: Exception) { Log.e("Xmltv", "Arama için ikincil URL yüklenemedi: ${e.message}") }

        return allResults.distinctBy { it.name }
    }
    
    // ⭐ LOAD FONKSİYONU: Hata mesajını Açıklama kısmına yazdıracak şekilde güncellendi
    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)
        
        val epgData = loadEpgData() 
        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")

        var epgPlotText: String 
        var programs: List<ProgramInfo> = emptyList()

        if (epgData.errorMessage != null) {
            // EPG yüklenirken veya parse edilirken hata oluştuysa, hatayı göster
            epgPlotText = "\n\n--- YAYIN AKIŞI HATA RAPORU ---\n" + 
                           "HATA: EPG yüklenirken/ayrıştırılırken sorun oluştu.\n" +
                           "Rapor: ${epgData.errorMessage}" +
                           "\n--- HATA SONU ---"
        } else if (channelTvgId == null) {
            // tvg-id bulunamadıysa uyarıyı göster
            epgPlotText = "\n\n--- YAYIN AKIŞI BİLGİSİ ---\n" +
                          "UYARI: Kanal listesinde 'tvg-id' bilgisi bulunamadı. EPG eşleştirilemiyor."
        } else {
            // EPG verisi yüklü ve tvg-id var, şimdi eşleştirmeyi dene
            programs = epgData.programs[channelTvgId]
                ?.map { epgProgram: EpgProgram -> 
                    ProgramInfo( 
                        name = epgProgram.name,
                        description = epgProgram.description,
                        start = epgProgram.start,
                        end = epgProgram.end
                    )
                }
                ?.sortedBy { it.start } 
                ?: emptyList()

            if (programs.isNotEmpty()) {
                // Başarılı eşleşme
                val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                
                val formattedPrograms = programs
                    .filter { Calendar.getInstance().apply { timeInMillis = it.start }.get(Calendar.DAY_OF_YEAR) in (today)..(today + 1) }
                    .joinToString(separator = "\n") { program ->
                        val startCal = Calendar.getInstance().apply { timeInMillis = program.start }
                        val startHour = String.format("%02d", startCal.get(Calendar.HOUR_OF_DAY))
                        val startMinute = String.format("%02d", startCal.get(Calendar.MINUTE))
                        val descriptionText = program.description?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                        
                        "[$startHour:$startMinute] ${program.name}$descriptionText"
                    }
                
                epgPlotText = "\n\n--- YAYIN AKIŞI ---\n" + formattedPrograms
            } else {
                // EPG yüklü ama bu TVG-ID için program bulunamadı
                epgPlotText = "\n\n--- YAYIN AKIŞI BİLGİSİ ---\n" +
                              "UYARI: EPG verisi yüklendi ancak '${channelTvgId}' ID'li kanala ait yayın akışı bulunamadı. ID hatası olabilir."
            }
        }

        val originalPlot = groupedData.description ?: ""
        val finalPlot = originalPlot + epgPlotText

        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.toJson(), 
            dataUrl = groupedData.toJson(), 
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = finalPlot
            this.type = TvType.Live
       }
    }

    // loadLinks fonksiyonu (Aynı)
    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val groupedData = parseJson<GroupedChannelData>(data)
        var foundLink = false
        groupedData.items.forEachIndexed { index, item ->
            val videoUrl = item.url
            if (videoUrl.isNullOrBlank()) return@forEachIndexed
            val linkName = groupedData.title + " Kaynak ${index + 1}"
            val linkType = when {
                videoUrl.endsWith(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                videoUrl.endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.M3U8 
            }
            callback.invoke(
                newExtractorLink(
                    source = linkName,
                    name = groupedData.title,
                    url = videoUrl,
                    type = linkType
                ) {
                    this.referer = "" 
                    this.quality = Qualities.Unknown.value
                    item.userAgent?.let { ua -> this.headers = mapOf("User-Agent" to ua) }
                }
            )
            foundLink = true
        }
        return foundLink
    }
}
