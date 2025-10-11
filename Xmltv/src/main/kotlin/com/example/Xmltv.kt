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

// ⭐ KRİTİK DÜZELTME: Program/ProgramInfo referansı çözümlenemediği için
// CloudStream'in beklediği data sınıfını yerel olarak tanımlıyoruz.
data class ProgramInfo(
    val name: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val start: Long,
    val end: Long
)

// EPG ve Playlist için temel modeller
data class EpgProgram(
    val name: String,
    val description: String?,
    val start: Long,
    val end: Long,
    val channel: String // Hangi kanala ait olduğu (tvg-id)
)

// EpgData'nın yapısını, Map<tvg-id, List<EpgProgram>> olarak değiştirdik.
data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap())

data class PlaylistItem(
    val title: String,
    val url: String, // URL zorunlu, ekledik.
    val description: String? = null,
    val nation: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(), // Link headers
    val userAgent: String? = null // Link userAgent
)

data class Playlist(val items: List<PlaylistItem> = emptyList())

class EpgXmlParser {
    fun parseEPG(xml: String): EpgData {
        // GERÇEK XMLTV PARSE KODUNUZ BURAYA GELECEKTİR!
        Log.w("XmltvParser", "parseEPG metodu boş döndürüyor. XML parse kodunuzu ekleyin.")
        return EpgData()
    }
}

class XmlPlaylistParser {
    fun parseXML(xml: String): Playlist {
        // GERÇEK M3U/XML PARSE KODUNUZ BURAYA GELECEKTİR!
        Log.w("XmltvParser", "parseXML metodu boş döndürüyor. Playlist parse kodunuzu ekleyin.")
        return Playlist()
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
            // Arama sorgusu varsa filtrele
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
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Üçüncül URL yüklenemedi: ${e.message}")
        }


        if (homepageLists.isEmpty()) {
            throw Exception("Tüm URL'ler yüklenemedi veya boş playlist döndürdü.")
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

    // --- load FONKSİYONU ---
    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)
        
        val epgData = loadEpgData()
        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")

        // EPG eşleştirme ve dönüştürme
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

        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.toJson(), // loadLinks'e geçecek JSON data'yı URL olarak kullanıyoruz
            dataUrl = groupedData.toJson(), 
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = groupedData.description
            this.type = TvType.Live
            
            // ⭐ KRİTİK DÜZELTME 2: Alan adı 'program' veya 'programInfo' değil, 'programs' olarak değiştirildi.
            this.programs = programs 
       }
    }

    // --- loadLinks FONKSİYONU ---
    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val groupedData = parseJson<GroupedChannelData>(data)
        var foundLink = false

        // Her bir kaynağı (item) döngüye al ve ExtractorLink olarak geri çağır.
        groupedData.items.forEachIndexed { index, item ->
            val videoUrl = item.url
            if (videoUrl.isNullOrBlank()) return@forEachIndexed
            
            val linkName = groupedData.title + " Kaynak ${index + 1}"
            
            val linkType = when {
                videoUrl.endsWith(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                videoUrl.endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.M3U8 // Varsayılan olarak M3U8 kabul et
            }

            callback.invoke(
                newExtractorLink(
                    source = linkName,
                    name = groupedData.title,
                    url = videoUrl,
                    type = linkType
                ) {
                    this.referer = "" // Referer gerekliyse buraya ekleyin
                    this.quality = Qualities.Unknown.value
                    item.userAgent?.let { ua -> this.headers = mapOf("User-Agent" to ua) }
                }
            )
            foundLink = true
        }
        return foundLink
    }
}
