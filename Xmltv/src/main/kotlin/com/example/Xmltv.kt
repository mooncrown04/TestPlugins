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
import com.lagradost.cloudstream3.ActorData
import java.util.Calendar 
// ⭐ DÜZELTME: Program modelini CloudStream3'ten import ettik.
import com.lagradost.cloudstream3.Program 

/**
 * CloudStream için XMLTV tabanlı IPTV eklentisi
 */

class Xmltv : MainAPI() {
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/emegyd857cyocpk94w5lr/xmltv.xml?rlkey=kuyabjk4a8t65xvcob3cpidab"
    private val tertiaryXmlUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b-2.xml"
    private val tertiaryGroupName = "Favori Listem 2"
    private val primaryGroupName = "Favori Listem"
    private val secondaryGroupName = "Diğer Kanallar"
    private val defaultPosterUrl = "https://www.shutterstock.com/shutterstock/photos/2174119547/display_1500/stock-vector-mount-ararat-rises-above-the-clouds-dawn-panoramic-view-vector-illustration-2174119547.jpg"

    // ⭐ EPG EKLENTİSİ: EPG (Program Rehberi) URL'si
    // LÜTFEN KENDİ EPG URL'NİZİ BURAYA EKLEYİN
    private val epgUrl = "EPG_XML_URL_NIZI_BURAYA_EKLEYIN" 
    
    // ⭐ EPG EKLENTİSİ: Program verilerini bellekte tutmak için önbellek
    private var cachedEpgData: EpgData? = null

    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true 
    override val supportedTypes = setOf(TvType.Live)

    // --- YENİ VERİ MODELİ ---
    data class GroupedChannelData(
        val title: String,
        val posterUrl: String,
        val description: String? = null,
        val nation: String? = null,
        val items: List<PlaylistItem>
    )

    // Yardımcı Fonksiyon: EPG verisini çeker ve önbelleğe alır.
    private suspend fun loadEpgData(): EpgData? {
        if (cachedEpgData != null) return cachedEpgData
        
        if (epgUrl.contains("EPG_XML_URL_NIZI_BURAYA_EKLEYIN")) {
            Log.w("Xmltv", "EPG URL'si tanımlanmadı. Lütfen eklenti koduna EPG URL'sini ekleyin.")
            return null
        }
        
        return try {
            val epgResponse = app.get(epgUrl).text
            val epgData = EpgXmlParser().parseEPG(epgResponse)
            cachedEpgData = epgData
            Log.d("Xmltv", "EPG verisi başarıyla yüklendi. ${epgData.programs.size} kanal için program bulundu.")
            epgData
        } catch (e: Exception) {
            Log.e("Xmltv", "EPG verisi yüklenemedi: ${e.message}")
            null
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
        val logoUrl = firstItem.attributes["tvg-logo"]?.takeIf { it.isNotBlank() }
                ?: defaultPosterUrl

        val description = firstItem.description
        val nation = firstItem.nation

            val groupedData = GroupedChannelData(
                title = title,
                posterUrl = logoUrl,
                items = items,
                description = description,
                nation = nation
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
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)
            val primaryItems = createGroupedChannelItems(primaryPlaylist)
            
            if (primaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(primaryGroupName, primaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Birincil URL yüklenemedi: ${e.message}")
        }
        
        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            val secondaryPlaylist = XmlPlaylistParser().parseXML(secondaryResponse)
            val secondaryItems = createGroupedChannelItems(secondaryPlaylist)

            if (secondaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(secondaryGroupName, secondaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "İkincil URL yüklenemedi: ${e.message}")
        }
        
        try {
            val tertiaryResponse = app.get(tertiaryXmlUrl).text
            val tertiaryPlaylist = XmlPlaylistParser().parseXML(tertiaryResponse)
            val tertiaryItems = createGroupedChannelItems(tertiaryPlaylist)

            if (tertiaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(tertiaryGroupName, tertiaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Üçüncü URL yüklenemedi: ${e.message}")
        }
        return newHomePageResponse(homepageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allResults = mutableListOf<SearchResponse>()
        val lowerCaseQuery = query.lowercase()

        try {
            val primaryResponse = app.get(mainUrl).text
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)
            allResults.addAll(createGroupedChannelItems(primaryPlaylist, lowerCaseQuery))
        } catch (e: Exception) {
            Log.e("Xmltv", "Arama için Birincil URL yüklenemedi: ${e.message}")
        }
        
        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            val secondaryPlaylist = XmlPlaylistParser().parseXML(secondaryResponse)
            allResults.addAll(createGroupedChannelItems(secondaryPlaylist, lowerCaseQuery))
        } catch (e: Exception) {
            Log.e("Xmltv", "Arama için İkincil URL yüklenemedi: ${e.message}")
        }
        
        try {
            val tertiaryResponse = app.get(tertiaryXmlUrl).text
            val tertiaryPlaylist = XmlPlaylistParser().parseXML(tertiaryResponse)
            allResults.addAll(createGroupedChannelItems(tertiaryPlaylist, lowerCaseQuery))
        } catch (e: Exception) {
            Log.e("Xmltv", "Arama için Üçüncü URL yüklenemedi: ${e.message}")
        }
        
        return allResults.distinctBy { it.name }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // --- EPG GÜNCELLEMESİ YAPILAN LOAD FONKSİYONU ---
    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)
        
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
        
        // ⭐ EPG EKLENTİSİ: EPG verisini yükle
        val epgData = loadEpgData()

        // Kanalın tvg-id bilgisini alın
        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")

        // ⭐ DÜZELTME: Programları güvenli bir şekilde filtrele ve dönüştür
        val programs = if (channelTvgId != null && epgData != null) {
            // Hata veren map ve sortedBy ifadeleri düzeltildi.
            epgData.programs[channelTvgId]
                ?.map { epgProgram ->
                    Program(
                        name = epgProgram.title,
                        description = epgProgram.description,
                        start = epgProgram.start,
                        end = epgProgram.stop
                    )
                }
                ?.sortedBy { it.start } ?: emptyList()
        } else {
            emptyList()
        }


        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.items.firstOrNull()?.url ?: "",
            dataUrl = groupedData.toJson(),
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = groupedData.description
            this.type = TvType.Live
            
            val tagsList = mutableListOf<String>()
            tagsList.add("${groupedData.items.size} adet yayın kaynağı bulundu")
            
            groupedData.nation?.let { tagsList.add(it) }
            
            this.tags = tagsList
            this.actors = actorsList
            
            // ⭐ EPG EKLENTİSİ: Program akışını LoadResponse'a ekleyin
            this.program = programs 
     }
    }

    // --- LOADLINKS FONKSİYONU ---
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
                videoUrl.endsWith(".mp4", ignoreCase = true) || 
                videoUrl.endsWith(".ts", ignoreCase = true) || 
                videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
                
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
// -------------------------------------------------------------
// --- YENİ EPG VERİ MODELLERİ VE AYRIŞTIRICI ---
// -------------------------------------------------------------

// EPG Verisi için Program Modeli
data class EpgProgram(
    val title: String,
    val description: String?,
    val start: Long, // Başlangıç zamanı (milisaniye)
    val stop: Long,   // Bitiş zamanı (milisaniye)
    val channel: String // Hangi kanala ait olduğu (tvg-id ile eşleşir)
)

// EPG Verisini tüm kanallar için tutan ana model
data class EpgData(
    val programs: Map<String, List<EpgProgram>> // Key: Kanal ID'si (tvg-id)
)

class EpgXmlParser {
    
    // Basit bir tarih ayrıştırıcı fonksiyonu
    private fun parseXmlTvDateTime(dateTimeStr: String?): Long? {
        if (dateTimeStr.isNullOrBlank() || dateTimeStr.length < 14) return null
        
        return try {
            val year = dateTimeStr.substring(0, 4).toInt()
            val month = dateTimeStr.substring(4, 6).toInt() - 1 
            val day = dateTimeStr.substring(6, 8).toInt()
            val hour = dateTimeStr.substring(8, 10).toInt()
            val minute = dateTimeStr.substring(10, 12).toInt()
            
            Calendar.getInstance().apply {
                set(year, month, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (e: Exception) {
            Log.e("EpgXmlParser", "Tarih ayrıştırma hatası ($dateTimeStr): ${e.message}")
            null
        }
    }

    fun parseEPG(content: String): EpgData {
        val programsByChannel = mutableMapOf<String, MutableList<EpgProgram>>()
        
        // programme etiketlerini yakala. groupValues sırasıyla: 1=start, 2=stop, 3=channel, 4=içerik
        val programmeRegex = Regex("<programme\\s+start=\"(.*?)\"\\s+stop=\"(.*?)\"\\s+channel=\"(.*?)\">(.*?)</programme>", RegexOption.DOT_MATCHES_ALL)
        
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val descRegex = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)

        for (match in programmeRegex.findAll(content)) {
            val startStr = match.groupValues.getOrNull(1)
            val stopStr = match.groupValues.getOrNull(2)
            val channelId = match.groupValues.getOrNull(3)
            val contentBlock = match.groupValues.getOrNull(4)
            
            if (channelId.isNullOrBlank() || contentBlock.isNullOrBlank()) continue
            
            val start = parseXmlTvDateTime(startStr) ?: continue
            val stop = parseXmlTvDateTime(stopStr) ?: continue

            val title = titleRegex.find(contentBlock)?.groupValues?.getOrNull(1)?.trim() ?: "Bilinmeyen Program"
            val description = descRegex.find(contentBlock)?.groupValues?.getOrNull(1)?.trim()

            val program = EpgProgram(title, description, start, stop, channelId)
            
            programsByChannel.getOrPut(channelId) { mutableListOf() }.add(program)
        }
        
        return EpgData(programsByChannel.toMap())
    }
}


// -------------------------------------------------------------
// --- ORİJİNAL VERİ MODELLERİ ---
// -------------------------------------------------------------

data class Playlist(val items: List<PlaylistItem>)

data class PlaylistItem(
    val title: String,
    val url: String,
    val description: String? = null,
    val nation: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)


class XmlPlaylistParser {
    
    private val nationRegex = Regex(
        "nation\\s*:\\s*(.*)",
        RegexOption.IGNORE_CASE
    )

    fun parseXML(content: String): Playlist {
        val playlistItems: MutableList<PlaylistItem> = mutableListOf()

        val channelRegex = Regex(
            "<channel>(.*?)</channel>",
            RegexOption.DOT_MATCHES_ALL
        )

        val titleRegex = Regex(
            "<title>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</title>",
            RegexOption.DOT_MATCHES_ALL
        )
        val logoRegex = Regex(
            "<logo_30x30>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</logo_30x30>",
            RegexOption.DOT_MATCHES_ALL
        )
        val streamUrlRegex = Regex(
            "<stream_url>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</stream_url>",
            RegexOption.DOT_MATCHES_ALL
        )
        val descriptionRegex = Regex(
            "<description>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</description>",
            RegexOption.DOT_MATCHES_ALL
        )
        val tvgIdRegex = Regex("<tvg-id>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</tvg-id>", RegexOption.DOT_MATCHES_ALL)


        for (channelMatch in channelRegex.findAll(content)) {
            val channelBlock = channelMatch.groupValues.getOrNull(1) ?: continue

            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = streamUrlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val description = descriptionRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val tvgId = tvgIdRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            
            val nationMatch = description?.let { nationRegex.find(it) }
            val nation = nationMatch?.groupValues?.getOrNull(1)?.trim()
            
            if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                val attributesMap = mutableMapOf<String, String>()
                attributesMap["tvg-logo"] = logo ?: ""
                attributesMap["group-title"] = "XML Kanalları"
                // tvg-id'yi attribute olarak kaydet
                if (!tvgId.isNullOrBlank()) {
                    attributesMap["tvg-id"] = tvgId
                } else if (title != null) {
                    // Eğer tvg-id yoksa, EPG eşleşmesi için başlığın bir versiyonunu kullanmayı düşünebilirsiniz.
                    // (Ancak bu nadiren güvenilir bir eşleşme sağlar).
                    // attributesMap["tvg-id"] = title.replace("\\s".toRegex(), "").lowercase()
                }

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

        if (playlistItems.isEmpty()) {
            Log.e("Xmltv", "XML ayrıştırma tamamlandı ancak geçerli kanal bulunamadı.")
        } else {
            Log.d("Xmltv", "XML ayrıştırma başarılı: ${playlistItems.size} kanal bulundu.")
        }

        return Playlist(playlistItems)
    }
}
