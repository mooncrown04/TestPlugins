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
    private val tertiaryGroupName = "Favori Listem 2" //   
	private val primaryGroupName = "Favori Listem"
    private val secondaryGroupName = "Diğer Kanallar"
    // ⭐ YENİ: SABİT YEDEK POSTER
    private val defaultPosterUrl = "https://www.shutterstock.com/shutterstock/photos/2174119547/display_1500/stock-vector-mount-ararat-rises-above-the-clouds-dawn-panoramic-view-vector-illustration-2174119547.jpg" 


    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // --- YENİ VERİ MODELİ ---
    // Aynı isme sahip tüm kaynakları ve ortak meta veriyi tutar.
    data class GroupedChannelData(
        val title: String,
        val posterUrl: String,
        val description: String? = null,
        val items: List<PlaylistItem> // Bu gruptaki tüm kaynak PlaylistItem'ları
    )

    // Helper fonksiyon: Kanal listesini oluşturur ve aynı isme sahip kanalları gruplar.
    private fun createGroupedChannelItems(playlist: Playlist): List<SearchResponse> {
        // Kanalları başlığa göre grupla (örneğin "ATV" başlığı altındaki tüm ATV kaynakları)
        val groupedByTitle = playlist.items.groupBy { it.title }

        return groupedByTitle.mapNotNull { (title, items) ->
            if (items.isEmpty()) return@mapNotNull null

            // Grubun ilk öğesini kullanarak ortak meta veriyi (poster vb.) al.
            val firstItem = items.first()
            // Poster URL'sini al, eğer boşsa veya yoksa 'defaultPosterUrl'i kullan.
        val logoUrl = firstItem.attributes["tvg-logo"]?.takeIf { it.isNotBlank() }
            ?: defaultPosterUrl // ⭐ BURASI GÜNCELLENDİ

        val description = firstItem.description 

            // YENİ: Grup verisini modelle ve JSON'a dönüştür.
            val groupedData = GroupedChannelData(
                title = title,
                posterUrl = logoUrl,
                items = items,
				description = description 
            )
            val dataUrl = groupedData.toJson() // SearchResponse'un URL'si artık JSON data

            newMovieSearchResponse(
                name = title,
                url = dataUrl, // JSON verisini URL olarak sakla
            ) {
                this.posterUrl = logoUrl
                this.type = TvType.Live
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homepageLists = mutableListOf<HomePageList>()

        // 1. Birincil XML'i Çek ve İşle (Gruplayarak)
        try {
            val primaryResponse = app.get(mainUrl).text
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)
            val primaryItems = createGroupedChannelItems(primaryPlaylist)
            
            if (primaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(primaryGroupName, primaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Birincil URL yüklenemedi veya ayrıştırılamadı: ${e.message}")
        }
        
        // 2. İkincil XML'i Çek ve İşle (Gruplayarak)
        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            val secondaryPlaylist = XmlPlaylistParser().parseXML(secondaryResponse)
            val secondaryItems = createGroupedChannelItems(secondaryPlaylist)

            if (secondaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(secondaryGroupName, secondaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "İkincil URL yüklenemedi veya ayrıştırılamadı: ${e.message}")
        }
  // ⭐ 3. ÜÇÜNCÜ XML'i Çek ve İşle (Gruplayarak)
        try {
            val tertiaryResponse = app.get(tertiaryXmlUrl).text
            val tertiaryPlaylist = XmlPlaylistParser().parseXML(tertiaryResponse)
            val tertiaryItems = createGroupedChannelItems(tertiaryPlaylist)

            if (tertiaryItems.isNotEmpty()) {
                // Yeni grup adını kullanarak listeyi ekle
                homepageLists.add(HomePageList(tertiaryGroupName, tertiaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Üçüncü URL yüklenemedi veya ayrıştırılamadı: ${e.message}")
        }
        return newHomePageResponse(homepageLists)
    }

    // --- YENİ LOAD FONKSİYONU ---
    override suspend fun load(url: String): LoadResponse {
        // Gelen URL, artık bir kanal grubu verisini tutan JSON string'dir.
        val groupedData = parseJson<GroupedChannelData>(url)
   // ⭐ 1. DÜZELTME: actorsList TANIMLAMASI BURAYA TAŞINDI
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
        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.items.firstOrNull()?.url ?: "", // İlk linki kullan
            dataUrl = groupedData.toJson(), // loadLinks'in kullanması için tüm listeyi dataUrl'de tut
        ) {
            this.posterUrl = groupedData.posterUrl
          //  this.plot = "Bu kanal için ${groupedData.items.size} adet yayın kaynağı bulundu. En iyi kalite otomatik olarak seçilecektir."
              this.plot = groupedData.description
			  this.type = TvType.Live
              this.tags = listOf("${groupedData.items.size} adet yayın kaynağı bulundu") 
              this.actors = actorsList
	 }
    }

    // --- GÜNCELLENMİŞ LOADLINKS FONKSİYONU ---
override suspend fun loadLinks(
    data: String, // Bu, artık tüm PlaylistItem'ları içeren GroupedChannelData JSON'u
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // data'yı GroupedChannelData nesnesine geri çevir
    val groupedData = parseJson<GroupedChannelData>(data)

    var foundLink = false

    // ⭐ GÜNCELLEME: Diziyi index (sıra) ile birlikte döngüye alıyoruz.
    groupedData.items.forEachIndexed { index, item ->
        val videoUrl = item.url
        if (videoUrl.isNullOrBlank()) return@forEachIndexed
        
        // ⭐ YENİ EKLEME: Kaynak adını orijinal başlık ve sıra numarası ile oluşturuyoruz.
        val linkName = groupedData.title + " Kaynak ${index + 1}"
        
        // 1. URL uzantısına göre en uygun tip belirlenir.
        val linkType = when {
            // Not: VIDEO ve DOWNLOADABLE Cloudstream'in dahili tipleridir.
            videoUrl.endsWith(".mp4", ignoreCase = true) || 
            videoUrl.endsWith(".ts", ignoreCase = true) || 
            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO // İndirilebilir medya tipleri
            
            videoUrl.endsWith(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
            videoUrl.endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
            
            else -> ExtractorLinkType.M3U8 // Varsayılan olarak canlı yayın tipi
        }

        // ExtractorLink'i geriye çağır (Farklı kaynakları listelemek için)
        callback.invoke(
            newExtractorLink(
                // ⭐ Kaynak (Source) adı olarak benzersiz linkName kullanıldı
                source = linkName,
                name = groupedData.title,
                url = videoUrl,
                type = linkType
            ) {
                this.referer = ""
                // Kalite tahmini (Varsayılan olarak Unknown)
                this.quality = Qualities.Unknown.value
                // Eğer bir User-Agent varsa buraya eklenebilir.
                item.userAgent?.let { ua -> this.headers = mapOf("User-Agent" to ua) }
            }
        )
        foundLink = true
    }
    return foundLink
  }
}
// -------------------------------------------------------------
// --- XML Ayrıştırıcı ve Veri Modeli (Değişmedi) ---
// -------------------------------------------------------------

class XmlPlaylistParser {
    // ... (XmlPlaylistParser sınıfının içeriği aynı kalır)
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
            
			if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                val attributesMap = mutableMapOf<String, String>()
                attributesMap["tvg-logo"] = logo ?: ""
                attributesMap["group-title"] = "XML Kanalları"

                playlistItems.add(
                    PlaylistItem(
                        title = title,
                        url = url,
                        description = description,
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
// --- Orijinal Veri Modelleri (Uyum için tutuldu) ---
// -------------------------------------------------------------

data class Playlist(val items: List<PlaylistItem>)

data class PlaylistItem(
    val title: String,
    val url: String,
	val description: String? = null //
	val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)
