package com.example

import android.util.Log
// CLOUDSTREAM SINIFLARI İÇİN TEMEL İMPORTLAR
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.Qualities

// KOTLIN TEXT İMPORTLARI: RegEx sorunlarını (DOT_ALL, findAll, trim) çözmek için kritik
import kotlin.text.* import kotlin.collections.* /**
 * CloudStream için XMLTV tabanlı IPTV eklentisi
 */
class Xmltv : MainAPI() {
    // Birincil XML URL'si
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    
    // ⭐ YENİ: İkinci XML kaynağı için URL tanımlandı
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/emegyd857cyocpk94w5lr/xmltv.xml?rlkey=kuyabjk4a8t65xvcob3cpidab"// <<< BURAYI İKİNCİ URL İLE DEĞİŞTİRİN
    
    // ⭐ YENİ: Grup adları tanımlandı
    private val primaryGroupName = "Favori Listem"
    private val secondaryGroupName = "Diğer Kanallar"

    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Tüm listeleri tutacak liste
        val homepageLists = mutableListOf<HomePageList>()

        // 1. Birincil XML'i Çek ve İşle
        try {
            val primaryResponse = app.get(mainUrl).text
            val primaryPlaylist = XmlPlaylistParser().parseXML(primaryResponse)

            val primaryItems = primaryPlaylist.items.map {
                newMovieSearchResponse(
                    name = it.title,
                    url = it.url,
                ) {
                    this.posterUrl = it.attributes["tvg-logo"]
                    this.type = TvType.Live
                }
            }
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

            val secondaryItems = secondaryPlaylist.items.map {
                newMovieSearchResponse(
                    name = it.title,
                    url = it.url,
                ) {
                    this.posterUrl = it.attributes["tvg-logo"]
                    this.type = TvType.Live
                }
            }
            if (secondaryItems.isNotEmpty()) {
                homepageLists.add(HomePageList(secondaryGroupName, secondaryItems))
            }
        } catch (e: Exception) {
             Log.e("Xmltv", "İkincil URL yüklenemedi veya ayrıştırılamadı: ${e.message}")
        }

        return newHomePageResponse(homepageLists)
    }

    // ⭐ LOAD FONKSİYONU
    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(
            name = "Canlı Yayın",
            url = url,       
            dataUrl = url,   
        ) {
            this.posterUrl = null
            this.plot = "Canlı yayın akışı"
            this.type = TvType.Live
        }
    }

    // ⭐ LOADLINKS FONKSİYONU
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
      // ⭐ YENİ EKLEME: Linkin sonunu kontrol et
    val linkType = if (data.endsWith(".m3u8", ignoreCase = true)) {
        ExtractorLinkType.M3U8
    } else {
        // Eğer m3u8 değilse, genel bir direkt link olduğunu varsay
        ExtractorLinkType.LINK
    }
        
        callback.invoke(
            newExtractorLink(
                source = "XMLTV",
                name = this.name,
                url = data,
               // ⭐ Tipi dinamik olarak kullan
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

        // Etiketler ve CDATA arasındaki yeni satırları ve boşlukları (\s*) tolere edecek RegEx'ler
        val titleRegex = Regex(
            "<title>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</title>",
            RegexOption.DOT_MATCHES_ALL
        )
        val logoRegex = Regex(
            "<logo_30x30>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</logo_30x30>",
            RegexOption.DOT_MATCHES_ALL
        )
        // Eğer XML'de stream_url veya playlist_url varsa, aşağıdaki URL yakalama mantığını kullanın:
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
                
                // Gruplama getMainPage'de yapıldığı için, buradaki atamayı kaldırmadım
                // ancak CloudStream'e veri sağlamak için tutabiliriz.
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

