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
// Mutex için gerekli importlar gereksiz hale geldi ama tutmak derlemeyi bozmaz.
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
data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap())

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
// EPG artık yüklenmediği için bu sınıflar teknik olarak kullanılmayacak ancak derleyiciye lazım olabilir.

class EpgXmlParser { 
    fun parseEPG(xml: String): EpgData {
        if (xml.isBlank()) return EpgData()
        val programs = mutableListOf<EpgProgram>()
        val programmeBlockRegex = Regex("<programme\\s+start=\"([^\"]+)\"\\s+stop=\"([^\"]+)\"\\s+channel=\"([^\"]+)\".*?</programme>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val descRegex = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)
        
        programmeBlockRegex.findAll(xml).forEach { match ->
            val startStr = match.groupValues.getOrNull(1)?.trim() ?: return@forEach
            val endStr = match.groupValues.getOrNull(2)?.trim() ?: return@forEach
            val channelId = match.groupValues.getOrNull(3)?.trim() ?: return@forEach
            val programmeBlock = match.groupValues.getOrNull(0) ?: return@forEach

            val title = titleRegex.find(programmeBlock)?.groupValues?.getOrNull(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "")
            val description = descRegex.find(programmeBlock)?.groupValues?.getOrNull(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "")

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
// 3. ANA API SINIFI (EPG YÜKLEMESİ DEVRE DIŞI)
// ***************************************************************

class Xmltv : MainAPI() {
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/vg40bpys8ym1jjrcuv1wp/XMLTvcs.xml?rlkey=7g2chxiol35z6kg6b36c4nyv8"
    private val tertiaryXmlUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b-2.xml"
    private val epgUrl = "https://raw.githubusercontent.com/braveheart1983/tvg-macther/refs/heads/main/tr-epg.xml"
    
    // EPG Önbellekleme değişkenleri
    @Volatile
    private var cachedEpgData: EpgData? = null 
    // Mutex tanımlaması (EPG devre dışı olduğu için artık kullanılmayacak)
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

    // ⭐ EPG YÜKLEMEYİ TAMAMEN DEVRE DIŞI BIRAKAN FONKSİYON
    private suspend fun loadEpgData(): EpgData? {
        Log.d("Xmltv", "EPG yükleme geçici olarak devre dışı bırakıldı. Çökme testi yapılıyor.")
        return null // Doğrudan null döndürerek EPG verisini çekmeyi ve parse etmeyi engeller.
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
        } catch (e: Exception) { Log.e("Xmltv", "İkincil URL yüklenem
