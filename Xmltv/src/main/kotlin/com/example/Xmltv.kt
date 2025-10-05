package com.example

import android.util.Log
// CLOUDSTREAM SINIFLARI İÇİN TEMEL İMPORTLAR
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.text.* import kotlin.collections.* /**
 * CloudStream için XMLTV tabanlı IPTV eklentisi
 */
class Xmltv : MainAPI() {
    // Birincil XML URL'si
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    
    // İkinci XML kaynağı için URL. ⭐ BURAYI KENDİ İKİNCİ URL'NİZLE DEĞİŞTİRİN
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/emegyd857cyocpk94w5lr/xmltv.xml?rlkey=kuyabjk4a8t65xvcob3cpidab"
    
    // Grup adları
    private val primaryGroupName = "Favori Listem"
    private val secondaryGroupName = "Diğer Kanallar"

    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homepageLists = mutableListOf<HomePageList>()

        // Helper fonksiyon: Kanal listesini oluşturur ve poster aktarımı için URL'yi birleştirir.
        fun createChannelItems(playlist: Playlist): List<SearchResponse> {
            return playlist.items.map {
                val logoUrl = it.attributes["tvg-logo"] ?: ""
                
                // POSTER AKTARIMI KRİTİK: Akış URL'si ve logo URL'sini '|' ile birleştir
                val combinedUrl = if (logoUrl.isBlank()) it.url else "${it.url}|logo=$logoUrl"

                newMovieSearchResponse(
                    name = it.title,
                    url = combinedUrl, // Birleştirilmiş URL'yi Load'a gönder
                ) {
                    this.posterUrl = it.attributes["tvg-logo"] // Listede logo görünümü
                    this.type = TvType.Live
                }
            }
        }

        // 1. Birincil XML'i Çek ve İşle
        try {
            val primaryResponse = app.get(mainUrl).text
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)
            val primaryItems = createChannelItems(primaryPlaylist)
            
            if (primaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(primaryGroupName, primaryItems))
            }
        } catch (e: Exception) {
            Log.e("Xmltv", "Birincil URL yüklenemedi veya ayrıştırılamadı: ${e.message}")
        }
        
        // 2. İkincil XML'i Çek ve İşle
        try {
            val secondaryResponse = app.get(secondaryXmlUrl).text
            val secondaryPlaylist = XmlPlaylistParser().parseXML(secondaryResponse)
            val secondaryItems = createChannelItems(secondaryPlaylist)

            if (secondaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(secondaryGroupName, secondaryItems))
            }
        } catch (e: Exception) {
             Log.e("Xmltv", "İkincil URL yüklenemedi veya ayrıştırılamadı: ${e.message}")
        }

        // newHomePageResponse artık sadece List<HomePageList> bekler
        return newHomePageResponse(
            homepageLists 
        )
    }

    // LOAD FONKSİYONU: Poster URL'sini geri alır.
    override suspend fun load(url: String): LoadResponse {
        // URL'yi parçala: [0] -> Akış URL'si, [1] -> Logo bilgisi (varsa)
        val parts = url.split('|') 
        val streamUrl = parts.firstOrNull() ?: url // Temiz akış URL'si

        // Logo URL'sini çek: "logo=http://..." kısmını bulup "logo=" kısmını siler
        val logoParam = parts.find { it.startsWith("logo=") }
        val logoUrl = logoParam?.substringAfter("logo=") 
        
        return newLiveStreamLoadResponse(
            name = "Canlı Yayın",
            url = streamUrl,       
            dataUrl = streamUrl,   // loadLinks'e temiz akış URL'si gitsin
        ) {
            this.posterUrl = logoUrl // Logo URL'si menüye atanır.
            this.plot = "Canlı yayın akışı"
            this.type = TvType.Live
        }
    }

    // LOADLINKS FONKSİYONU: Dinamik link türü belirleme
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Linkin sonunu kontrol ederek tipi dinamik olarak belirle
        val linkType = if (data.endsWith(".m3u8", ignoreCase = true)) {
            ExtractorLinkType.M3U8
        } else {
            // TS, MP4 gibi diğer direkt akışları desteklemek için
            ExtractorLinkType.LINK 
        }
        
        callback.invoke(
            newExtractorLink(
                source = "XMLTV",
                name = this.name,
                url = data,
                type = linkType 
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
// -------------------------------------------------------------
// --- XML Ayrıştırıcı Sınıfı (RegEx Tabanlı) ---
// -------------------------------------------------------------

class XmlPlaylistParser {
    fun parseXML(content: String): Playlist {
        val playlistItems: MutableList<PlaylistItem> = mutableListOf()

        val channelRegex = Regex(
            "<channel>(.*?)</channel>",
            RegexOption.DOT_MATCHES_ALL
        )

        // Tüm RegEx'ler, etiketler ve CDATA arasındaki yeni satırları/boşlukları tolere etmek için \s* kullanır.
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
        
        for (channelMatch in channelRegex.findAll(content)) {
            val channelBlock = channelMatch.groupValues.getOrNull(1) ?: continue

            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = streamUrlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()

            if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                val attributesMap = mutableMapOf<String, String>()
                attributesMap["tvg-logo"] = logo ?: "" 
                attributesMap["group-title"] = "XML Kanalları" 

                playlistItems.add(
                    PlaylistItem(
                        title = title,
                        url = url,
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
// --- Veri Modeli ---
// -------------------------------------------------------------

data class Playlist(val items: List<PlaylistItem>)

data class PlaylistItem(
    val title: String,
    val url: String,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)

