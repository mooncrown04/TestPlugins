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

// ⭐ KRİTİK DÜZELTME: CloudStream'in Program sınıfını kullanmak için içe aktarıyoruz.
import com.lagradost.cloudstream3.Program


// EPG ve Playlist için temel modeller (Kendi Program tanımınızı sildik)
// EpgProgram, kendi EPG parse'ınızın çıktısıdır.
data class EpgProgram(
    val name: String,
    val description: String?,
    val start: Long,
    val end: Long,
    val channel: String // Hangi kanala ait olduğu (tvg-id)
)

// EpgData'nın yapısını, Map<tvg-id, List<EpgProgram>> olarak değiştirdik.
// Bu, EPG eşleştirmesini kolaylaştırır.
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
    // Gerçek EPG XML parse kodu eklenmeli
    // Bu metod, XML'i okuyup tvg-id'ye göre gruplanmış EpgData döndürmelidir.
    fun parseEPG(xml: String): EpgData {
        // Örnek: Basit bir EPG parser eklenmeli (Gerçek kod için bu kısmı doldurun)
        return EpgData()
    }
}

class XmlPlaylistParser {
    // Gerçek playlist XML parse kodu eklenmeli
    fun parseXML(xml: String): Playlist {
        // Örnek: Basit bir Playlist parser eklenmeli (Gerçek kod için bu kısmı doldurun)
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

    // URL'yi güncelledik (kullanıcı tarafından sağlanmış)
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
        // EPG URL kontrolü gereksiz, kullanıcı tarafından zaten verilmiş.
        
        return try {
            val epgResponse = app.get(epgUrl).text
            val epgData = EpgXmlParser().parseEPG(epgResponse)
            cachedEpgData = epgData
            // programs artık Map olduğu için boyutu doğru almalıyız
            Log.d("Xmltv", "EPG verisi başarıyla yüklendi. ${epgData.programs.size} kanal için program bulundu.")
            epgData
        } catch (e: Exception) {
            Log.e("Xmltv", "EPG verisi yüklenemedi: ${e.message}")
            null
        }
    }

    private fun createGroupedChannelItems(playlist: Playlist, query: String? = null): List<SearchResponse> {
        // ... (Bu kısım sorunsuz)
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
        // ... (Bu kısım sorunsuz)
        val homepageLists = mutableListOf<HomePageList>()
        // ... (URL'lerden playlist okuma ve homepageList'e ekleme mantığı)
        
        // ÖNEMLİ: Playlist parser'ınız boş döndürüyorsa, buraya hiçbir şey eklenmez.
        // Bu yüzden "anasayfada hiçbir şey yok" hatası alırsınız.
        // parseXML metodunuzu doğru şekilde doldurduğunuzdan emin olun.
        
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
        
        // Diğer URL'ler için de benzer try-catch blokları
        
        return newHomePageResponse(homepageLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // ... (Bu kısım sorunsuz, Main Page mantığına benzer olmalı)
        val allResults = mutableListOf<SearchResponse>()
        val lowerCaseQuery = query.lowercase()
        // ... (URL'lerden playlist okuma ve allResults'a ekleme mantığı)
        return allResults.distinctBy { it.name }
    }

    // --- KRİTİK EKSİK: load FONKSİYONU ---
    // CloudStream, bir öğeye tıklandığında bu fonksiyonu çağırır. 
    // Bu fonksiyonda Program akışı (EPG) ayarlanır.
    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)
        
        val epgData = loadEpgData()
        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")

        // EPG eşleştirme ve dönüştürme
        val programs: List<Program> = if (channelTvgId != null && epgData != null) {
            epgData.programs[channelTvgId]
                ?.map { epgProgram: EpgProgram -> 
                    Program( // CloudStream'in Program sınıfını kullanıyoruz
                        name = epgProgram.name,
                        description = epgProgram.description,
                        start = epgProgram.start,
                        end = epgProgram.end
                    )
                }
              // it: Program ile sortedBy içindeki tipi netleştiriyoruz
            ?.sortedBy { it: Program -> it.start } 
            ?: emptyList()
        } else {
            emptyList()
        }

        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.toJson(), // loadLinks'e geçecek JSON data'yı URL olarak kullanıyoruz
            dataUrl = groupedData.toJson(), // Tekrar dataUrl olarak da geçebilir
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = groupedData.description
            this.type = TvType.Live
            this.program = programs // EPG verisini buraya ekliyoruz
     }
    }

    // --- KRİTİK EKSİK: loadLinks FONKSİYONU ---
    // CloudStream, bir yayını oynatmak için bu fonksiyonu çağırır.
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


