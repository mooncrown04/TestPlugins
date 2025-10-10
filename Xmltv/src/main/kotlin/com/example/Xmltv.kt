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

// EPG ve Playlist için temel modeller
data class Program(
    val name: String,
    val description: String?,
    val start: Long,
    val end: Long
)

data class EpgData(val programs: List<Program> = emptyList())

data class PlaylistItem(
    val title: String,
    val description: String? = null,
    val nation: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

class Playlist(val items: List<PlaylistItem> = emptyList())

class EpgXmlParser {
    fun parseEPG(xml: String): EpgData {
        // Gerçek EPG XML parse kodu eklenmeli
        return EpgData()
    }
}

class XmlPlaylistParser {
    fun parseXML(xml: String): Playlist {
        // Gerçek playlist XML parse kodu eklenmeli
        return Playlist()
    }
}

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
        return allResults
    }
}

