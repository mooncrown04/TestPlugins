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

    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)
    
    @Volatile 
    private var allChannelsCache: List<SearchResponse> = emptyList()

    // Aynı isme sahip tüm kaynakları ve ortak meta veriyi tutar.
    data class GroupedChannelData(
        val title: String,
        val posterUrl: String,
        val description: String? = null,
        val nation: String? = null, 
        val items: List<PlaylistItem> 
    )

    // Helper fonksiyon: Kanal listesini oluşturur ve aynı isme sahip kanalları gruplar.
    private fun createGroupedChannelItems(playlist: Playlist): List<SearchResponse> {
        val groupedByTitle = playlist.items.groupBy { it.title }

        return groupedByTitle.mapNotNull { (title, items) ->
            if (items.isEmpty()) return@mapNotNull null

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

            // ⭐ DÜZELTME 1: newLiveStreamSearchResponse (veya LiveStreamSearchResponse) 
            // kullanılmak yerine, Live TV için daha stabil olan newSearchResponse yapısı 
            // (title, url, type) ve posterUrl extension'ı kullanıldı. Eğer bu da hata verirse, 
            // o zaman LiveStreamSearchResponse'un doğrudan constructor'ı kullanılacaktır.
            // Önceki denemede newSearchResponse'un çözümlenememe hatasını aşmak için,
            // bunu doğrudan LiveStreamSearchResponse sınıfının örneğiyle değiştireceğiz.
            
            // ⭐ DÜZELTME 1.5: Hatanın devam etmesi üzerine, LiveStreamSearchResponse sınıfının doğrudan constructor'ı kullanılıyor.
            LiveStreamSearchResponse(
                name = title,
                url = dataUrl,
                apiName = name, // MainAPI'nin adını kullan
                type = TvType.Live,
                posterUrl = logoUrl,
                // data ve horizontalImages bu constructor'da zorunlu değil
            )
        }
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homepageLists = mutableListOf<HomePageList>()
        val allItems = mutableListOf<SearchResponse>() 

        // 1. Birincil XML
        val primaryItems = try {
            val response = app.get(mainUrl).text
            val playlist = XmlPlaylistParser().parseXML(response)
            createGroupedChannelItems(playlist)
        } catch (e: Exception) {
            Log.e("Xmltv", "Birincil URL yüklenemedi.")
            emptyList()
        }
        if (primaryItems.isNotEmpty()) {
            homepageLists.add(HomePageList(primaryGroupName, primaryItems))
            allItems.addAll(primaryItems)
        }

        // 2. İkincil XML
        val secondaryItems = try {
            val response = app.get(secondaryXmlUrl).text
            val playlist = XmlPlaylistParser().parseXML(response)
            createGroupedChannelItems(playlist)
        } catch (e: Exception) {
            Log.e("Xmltv", "İkincil URL yüklenemedi.")
            emptyList()
        }
        if (secondaryItems.isNotEmpty()) {
            homepageLists.add(HomePageList(secondaryGroupName, secondaryItems))
            allItems.addAll(secondaryItems)
        }
        
        // 3. Üçüncü XML
        val tertiaryItems = try {
            val response = app.get(tertiaryXmlUrl).text
            val playlist = XmlPlaylistParser().parseXML(response)
            createGroupedChannelItems(playlist)
        } catch (e: Exception) {
            Log.e("Xmltv", "Üçüncü URL yüklenemedi.")
            emptyList()
        }
        if (tertiaryItems.isNotEmpty()) {
            homepageLists.add(HomePageList(tertiaryGroupName, tertiaryItems))
            allItems.addAll(tertiaryItems)
        }

        // Arama için cache'i doldur
        allChannelsCache = allItems.distinctBy { it.name }
        
        return newHomePageResponse(homepageLists)
    }

    // Arama fonksiyonu
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        if (page != 1) return SearchResponseList(emptyList(), false)
        if (query.isBlank()) return SearchResponseList(emptyList(), false)

        // Cache boşsa, kanalları yükle.
        if (allChannelsCache.isEmpty()) {
            getMainPage(1, MainPageRequest()) 
        }

        val searchResult = allChannelsCache.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        } 

        Log.d("Xmltv", "Arama sonuçlandı: ${searchResult.size} kanal bulundu.")
        
        // SearchResponseList çağrısı basitleştirildi.
        return SearchResponseList(
            list = searchResult,
            hasNext = false // Tek sayfada tüm sonuçlar döndürülüyor.
        )
    }
    
    // --- LOAD FONKSİYONU ---
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
        // newLiveStreamLoadResponse, isimlendirilmiş parametreleri ister.
        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.items.firstOrNull()?.url ?: "",
            dataUrl = groupedData.toJson(),
            horizontalImages = null // Nullable olmalı
        ) {
            this.posterUrl = groupedData.posterUrl
            this.plot = groupedData.description
            this.type = TvType.Live
            val tagsList = mutableListOf<String>()
            tagsList.add("${groupedData.items.size} adet yayın kaynağı bulundu")
            groupedData.nation?.let { tagsList.add(it) } 
            this.tags = tagsList
            this.actors = actorsList
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
// --- XML Ayrıştırıcı ve Veri Modeli ---
// -------------------------------------------------------------

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
        
        for (channelMatch in channelRegex.findAll(content)) {
            val channelBlock = channelMatch.groupValues.getOrNull(1) ?: continue

            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = streamUrlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val description = descriptionRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            
            val nationMatch = description?.let { nationRegex.find(it) }
            val nation = nationMatch?.groupValues?.getOrNull(1)?.trim()
            
            if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                val attributesMap = mutableMapOf<String, String>()
                attributesMap["tvg-logo"] = logo ?: ""
                attributesMap["group-title"] = "XML Kanalları"

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

// -------------------------------------------------------------
// --- Orijinal Veri Modelleri ---
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
