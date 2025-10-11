package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.Calendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import java.text.SimpleDateFormat 
import java.util.Locale 
import com.lagradost.cloudstream3.ActorData // Zaten mevcuttu
// Hata veren IMetadataProvider.Companion import'ları kaldırıldı.

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
    
    private fun parseXmlTvDate(dateString: String): Long {
        // XMLTV formatı: YYYYMMDDhhmmss +0000
        val dateOnlyString = if (dateString.length >= 14) dateString.substring(0, 14) else return 0L

        val format = "yyyyMMddHHmmss"

        return try {
            val sdf = SimpleDateFormat(format, Locale.ENGLISH)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateOnlyString)
            date?.time ?: 0L
        } catch (e: Exception) {
            Log.e("EpgXmlParser", "KRİTİK HATA: SimpleDateFormat ile tarih ayrıştırma başarısız: $dateString - ${e.message}")
            return 0L
        }
    }


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

            val normalizedChannelId = channelId.lowercase()

            val title = titleRegex.find(block)?.groupValues?.getOrNull(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "")
            val description = descRegex.find(block)?.groupValues?.getOrNull(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "")

            val startTime = parseXmlTvDate(startStr)
            val endTime = parseXmlTvDate(endStr)

            if (startTime > 0L && endTime > 0L && !title.isNullOrBlank()) {
                programs.add(
                    EpgProgram(
                        name = title,
                        description = description,
                        start = startTime,
                        end = endTime,
                        channel = normalizedChannelId
                    )
                )
            }
        }
        val groupedPrograms = programs.groupBy { it.channel }
        return EpgData(groupedPrograms)
    }
}


class XmlPlaylistParser {
    private val channelRegex = Regex("<channel>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL)
    private val titleRegex = Regex("<title>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</title>", RegexOption.DOT_MATCHES_ALL)
    
    private val nationTagRegex = Regex("<nation>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</nation>", RegexOption.DOT_MATCHES_ALL)
    
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
            
            val nation = nationTagRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()


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
    
    // ⭐ YENİ FONKSİYON: Öneri Listesi Oluşturma (this.recommendations için gerekli)
    private suspend fun getRecommendations(currentTitle: String): List<SearchResponse> {
        val allChannels = mutableListOf<PlaylistItem>()
        
        // Üç URL'deki kanalları çekmeye çalış
        try {
            allChannels.addAll(XmlPlaylistParser().parseXML(app.get(mainUrl).text).items)
        } catch (e: Exception) { Log.e("Xmltv", "Öneriler için birincil URL yüklenemedi: ${e.message}") }
        try {
            allChannels.addAll(XmlPlaylistParser().parseXML(app.get(secondaryXmlUrl).text).items)
        } catch (e: Exception) { Log.e("Xmltv", "Öneriler için ikincil URL yüklenemedi: ${e.message}") }
        try {
            allChannels.addAll(XmlPlaylistParser().parseXML(app.get(tertiaryXmlUrl).text).items)
        } catch (e: Exception) { Log.e("Xmltv", "Öneriler için üçüncül URL yüklenemedi: ${e.message}") }

        // Mevcut kanalı listeden çıkar
        val filteredChannels = allChannels.filter { it.title != currentTitle }

        // Kanalları grupla ve SearchResponse formatına çevir
        val allGroupedItems = filteredChannels.groupBy { it.title }
            .mapNotNull { (title, items) ->
                if (items.isEmpty()) return@mapNotNull null
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
                newLiveSearchResponse( // Artık bu fonksiyonu sorunsuz kullanabiliriz.
                    name = title,
                    url = dataUrl,
                    type = TvType.Live
                ) {
                    this.posterUrl = logoUrl
                    groupedData.nation?.let { this.lang = it }
                }
            }
        
        // Karıştır ve sadece ilk 6 tanesini öneri olarak al
        return allGroupedItems.shuffled().take(6)
    }

    private suspend fun loadEpgData(): EpgData {
        if (cachedEpgData != null) return cachedEpgData!!

        return epgMutex.withLock {
            if (cachedEpgData != null) return cachedEpgData!!

            var epgResponse: String? = null

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
                groupedData.nation?.let { 
                    this.lang = it 
                }
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

    
    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)

        val epgData = loadEpgData()

        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")
        val normalizedTvgId = channelTvgId?.lowercase()

        var epgPlotText: String
        var programs: List<ProgramInfo> = emptyList()

        if (epgData.errorMessage != null) {
            epgPlotText = "\n\n--- EPG AKIŞI HATA RAPORU ---\n" +
                            "HATA: EPG yüklenirken/ayrıştırılırken sorun oluştu.\n" +
                            "Rapor: ${epgData.errorMessage}" +
                            "\n--- HATA SONU ---"
        } else if (normalizedTvgId == null) {
            epgPlotText = "\n\n--- EPG AKIŞI BİLGİSİ ---\n" +
                          "UYARI: Kanal listesinde 'tvg-id' bilgisi bulunamadı. EPG eşleştirilemiyor."
        } else {
            programs = epgData.programs[normalizedTvgId]
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
                val currentTime = System.currentTimeMillis()
                val localTimeZone = TimeZone.getDefault() 
                
                // BAŞLIK İÇİN ANLIK SAATİ ALMA
                val nowCal = Calendar.getInstance().apply { 
                    timeZone = localTimeZone 
                }
                val nowHour = String.format("%02d", nowCal.get(Calendar.HOUR_OF_DAY))
                val nowMinute = String.format("%02d", nowCal.get(Calendar.MINUTE))
                
                val formattedPrograms = programs
                    .filter { it.start >= currentTime } 
                    .joinToString(separator = "\n") { program ->
                        val startCal = Calendar.getInstance().apply {
                            timeInMillis = program.start 
                            timeZone = localTimeZone 
                        }
                        val startHour = String.format("%02d", startCal.get(Calendar.HOUR_OF_DAY))
                        val startMinute = String.format("%02d", startCal.get(Calendar.MINUTE))
                        val descriptionText = program.description?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""

                        "[$startHour:$startMinute] ${program.name}$descriptionText"
                    }

                // Başlığı anlık saat bilgisiyle oluştur
                epgPlotText = "\n\n--- EPG YAYIN AKIŞI (Saat: $nowHour:$nowMinute) ---\n" + formattedPrograms
            } else {
                val availableTvgIds = epgData.programs.keys.take(5).joinToString(", ")
                val totalChannels = epgData.programs.size

                epgPlotText = "\n\n--- EPG BİLGİSİ ---\n" +
                              "HATA KODU: EPG_ID_MISMATCH (PROGRAM BOŞ)\n" +
                              "Aranan ID: '${channelTvgId}' (Normalize: '${normalizedTvgId}')\n" +
                              "Sonuç: Bu ID için yayın akışı bulunamadı. Toplam $totalChannels kanal EPG'de yüklü.\n" +
                              "Örnek Bulunan ID'ler: ${availableTvgIds}... \n\n" +
                              "Çözüm Önerisi: XML kontrolü."
            }
        }

        val actorsList = mutableListOf<ActorData>()
        actorsList.add(
            ActorData(
                actor = Actor(
                    name = "MoOnCrOwN",
                    image = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"
                ),
                roleString = "yazılım amalesi"
            )
        )


        val originalPlot = groupedData.description ?: ""
        val finalPlot = originalPlot + epgPlotText

        // ⭐ ÖNERİ LİSTESİ OLUŞTURULUYOR
        val recommendedList = getRecommendations(groupedData.title)
        // ⭐ this.recommendations = recommendedList yapısı kullanılıyor

        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.toJson(),
            dataUrl = groupedData.toJson(),
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = finalPlot
            this.type = TvType.Live
            this.actors = actorsList
            
            // İstenen nation bilgisini tags olarak ekliyoruz.
            groupedData.nation?.let { 
                this.tags = listOf(it.uppercase()) 
            }
            
            // ⭐ Öneri listesi atanıyor!
            this.recommendations = recommendedList
        }
    }

    
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
