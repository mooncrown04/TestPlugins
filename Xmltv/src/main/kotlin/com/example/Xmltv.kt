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

// --------------------------------------------------------------------------------------------------
// --- VERİ MODELLERİ ---
// --------------------------------------------------------------------------------------------------

data class ProgramInfo(
    val name: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val start: Long,
    val end: Long
)

data class EpgProgram(
    val name: String,
    val description: String?,
    val start: Long,
    val end: Long,
    val channel: String // Hangi kanala ait olduğu (tvg-id)
)

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


// --------------------------------------------------------------------------------------------------
// --- XML PARSER SINIFLARI (HATA DÜZELTME YAPILDI) ---
// --------------------------------------------------------------------------------------------------

class EpgXmlParser {
    fun parseEPG(xml: String): EpgData {
        if (xml.isBlank()) return EpgData()

        val programs = mutableListOf<EpgProgram>()

        // 1. Programme bloğunu yakala
        val programmeBlockRegex = Regex("<programme\\s+start=\"([^\"]+)\"\\s+stop=\"([^\"]+)\"\\s+channel=\"([^\"]+)\".*?</programme>", RegexOption.DOT_MATCHES_ALL)
        
        // 2. İçerik Regex'leri (Blok içinden çekilecek)
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val descRegex = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)

        programmeBlockRegex.findAll(xml).forEach { match ->
            // match.groupValues[0] tüm eşleşme. match.groupValues[1/2/3] yakalanan gruplar.
            val startStr = match.groupValues.getOrNull(1)?.trim() ?: return@forEach
            val endStr = match.groupValues.getOrNull(2)?.trim() ?: return@forEach
            val channelId = match.groupValues.getOrNull(3)?.trim() ?: return@forEach
            val programmeBlock = match.groupValues.getOrNull(0) ?: return@forEach

            val title = titleRegex.find(programmeBlock)?.groupValues?.getOrNull(1)?.trim()
            val description = descRegex.find(programmeBlock)?.groupValues?.getOrNull(1)?.trim()

            // Zaman damgalarını milisaniyeye çevir
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
        Log.d("EpgXmlParser", "EPG ayrıştırma tamamlandı. ${groupedPrograms.size} kanal için program bulundu.")
        return EpgData(groupedPrograms)
    }

    // YYYYMMDDhhmmss formatındaki tarihi milisaniyeye çevirir.
    private fun parseXmlTvDate(dateLong: Long): Long {
        return try {
            val year = (dateLong / 1000000000000L) % 10000 // İlk 4
            val month = (dateLong / 10000000000L) % 100 // Sonraki 2
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
    // Sizin "önceki çalışan XML yapınız" için Regex'ler
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
                tvgId?.let { attributesMap["tvg-id"] = it } // EPG eşleştirmesi için

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

// --------------------------------------------------------------------------------------------------
// --- ANA API SINIFI ---
// --------------------------------------------------------------------------------------------------

class Xmltv : MainAPI() {
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/emegyd857cyocpk94w5lr/xmltv.xml?rlkey=kuyabjk4a8t65xvcob3cpidab"
    private val tertiaryXmlUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b-2.xml"
    private val tertiaryGroupName = "Favori Listem 2"
    private val primaryGroupName = "Favori Listem"
    private val secondaryGroupName = "Diğer Kanallar"
    private val defaultPosterUrl = "https://www.shutterstock.com/shutterstock/photos/2174119547/display_1500/stock-vector-mount-ararat-rises-above-the-clouds-dawn-panoramic-view-vector-illustration-2174119547.jpg"

    private val epgUrl = "https://raw.githubusercontent.com/braveheart1983/tvg-macther/refs/heads/main/tr-epg.xml"
    private var cachedEpgData: EpgData? = null

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

    private suspend fun loadEpgData(): EpgData? {
        if (cachedEpgData != null) return cachedEpgData

        return try {
            val epgResponse = app.get(epgUrl).text
            val epgData = EpgXmlParser().parseEPG(epgResponse)
            cachedEpgData = epgData
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

        // Birincil URL
        try {
            val primaryResponse = app.get(mainUrl).text
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)
            val primaryItems = createGroupedChannelItems(primaryPlaylist)

            if (primaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(primaryGroupName, primaryItems))
            } else {
                Log.w("Xmltv", "Birincil URL'den geçerli içerik alınamadı veya boş playlist döndü.")
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Birincil URL yüklenemedi: ${e.message}")
        }

        // İkincil URL
        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            val secondaryPlaylist = XmlPlaylistParser().parseXML(secondaryResponse)
            val secondaryItems = createGroupedChannelItems(secondaryPlaylist)

            if (secondaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(secondaryGroupName, secondaryItems))
            } else {
                Log.w("Xmltv", "İkincil URL'den geçerli içerik alınamadı veya boş playlist döndü.")
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "İkincil URL yüklenemedi: ${e.message}")
        }

        // Üçüncül URL
        try {
            val tertiaryResponse = app.get(tertiaryXmlUrl).text
            val tertiaryPlaylist = XmlPlaylistParser().parseXML(tertiaryResponse)
            val tertiaryItems = createGroupedChannelItems(tertiaryPlaylist)

            if (tertiaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(tertiaryGroupName, tertiaryItems))
            } else {
                Log.w("Xmltv", "Üçüncül URL'den geçerli içerik alınamadı veya boş playlist döndü.")
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Üçüncül URL yüklenemedi: ${e.message}")
        }


        if (homepageLists.isEmpty()) {
            return newHomePageResponse(emptyList())
        }
        return newHomePageResponse(homepageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allResults = mutableListOf<SearchResponse>()
        
        // Birincil URL'den arama
        try {
            val primaryResponse = app.get(mainUrl).text
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)
            allResults.addAll(createGroupedChannelItems(primaryPlaylist, query))
        } catch (e: Exception) {
            Log.e("Xmltv", "Arama için birincil URL yüklenemedi: ${e.message}")
        }

        // İkincil URL'den arama
        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            val secondaryPlaylist = XmlPlaylistParser().parseXML(secondaryResponse)
            allResults.addAll(createGroupedChannelItems(secondaryPlaylist, query))
        } catch (e: Exception) {
            Log.e("Xmltv", "Arama için ikincil URL yüklenemedi: ${e.message}")
        }

        return allResults.distinctBy { it.name }
    }
    
    // load FONKSİYONU
    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)
        
        val epgData = loadEpgData()
        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")

        val programs: List<ProgramInfo> = if (channelTvgId != null && epgData != null) {
            epgData.programs[channelTvgId]
                ?.map { epgProgram: EpgProgram -> 
                    ProgramInfo( 
                        name = epgProgram.name,
                        description = epgProgram.description,
                        posterUrl = null, 
                        rating = null, 
                        start = epgProgram.start,
                        end = epgProgram.end
                    )
                }
                ?.sortedBy { it.start } 
                ?: emptyList()
        } else {
            emptyList()
        }
        
        // EPG'yi gösterebilmek için `programs` listesini eklemesi gereken kod parçası:
        val shouldIncludePrograms = programs.isNotEmpty() 

        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.toJson(), 
            dataUrl = groupedData.toJson(), 
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = groupedData.description
            this.type = TvType.Live
            
            // Eğer programs alanı destekleniyorsa (hata vermiyorsa), eklenmelidir.
            // CloudStream'in LiveStreamLoadResponse yapısında 'programs' alanı mevcuttur.
            if (shouldIncludePrograms) {
                this.programs = programs 
            }
       }
    }

    // loadLinks FONKSİYONU
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
