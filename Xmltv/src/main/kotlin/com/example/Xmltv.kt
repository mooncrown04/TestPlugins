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
    // DropBox URL'si, ana URL olarak kullanılıyor
  //  override var mainUrl = "https://dl.dropbox.com/scl/fi/emegyd857cyocpk94w5lr/xmltv.xml?rlkey=kuyabjk4a8t65xvcob3cpidab"
 override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live) // TV tipi Live olarak belirtildi

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Doğrudan mainUrl'den veriyi çekiyoruz
        val response = app.get(mainUrl).text
        val playlist = XmlPlaylistParser().parseXML(response)

        val homeItems = playlist.items.map {
            newMovieSearchResponse(
                name = it.title,
                url = it.url, // Kanalın akış URL'si (Load'a gönderilecek)
               
            ) {
                this.posterUrl = it.attributes["tvg-logo"]
                this.type = TvType.Live                
            }
        }

        return newHomePageResponse(
            listOf(HomePageList("XML Kanalları", homeItems))
        )
    }

    // ⭐ LOAD FONKSİYONU EKLENDİ - Açıklama menüsüne gitmek için kritik
    override suspend fun load(url: String): LoadResponse {
        // Gelen 'url' (kanalın akış URL'si), dataUrl olarak loadLinks'e gönderilecek
        return newLiveStreamLoadResponse(
            name = "Canlı Yayın",
            url = url,       
            dataUrl = url,   // loadLinks'e aktarılacak olan URL
        ) {
            this.posterUrl = null
            this.plot = "Canlı yayın akışı"
            this.type = TvType.Live
        }
    }

    // ⭐ LOADLINKS FONKSİYONU - Oynatıcıya linki göndermek için kritik
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 'data' parametresi, Load fonksiyonundan gelen kanalın akış URL'sidir.
        callback.invoke(
            newExtractorLink(
                source = "XMLTV",
                name = this.name,
                url = data,        // data'yı (akış URL'si) kullan
                type = ExtractorLinkType.M3U8
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

        // Belirli alanları (CDATA dahil) yakalamak için regex'ler
       // ⭐ 1. TITLE REGEX GÜNCELLENDİ
        val titleRegex = Regex(
            // Etiketler ve CDATA arasındaki boşlukları (\s*) tolere et
            "<title>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</title>", 
            RegexOption.DOT_MATCHES_ALL
        )
        // ⭐ 2. LOGO REGEX GÜNCELLENDİ
        val logoRegex = Regex(
            // Etiketler ve CDATA arasındaki boşlukları (\s*) tolere et
            "<logo_30x30>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</logo_30x30>", 
            RegexOption.DOT_MATCHES_ALL
        )
        // ⭐ 3. STREAM_URL REGEX GÜNCELLENDİ
        val urlRegex = Regex(
            // stream_url etiketleri arasındaki boşlukları (\s*) tolere et
            "<stream_url>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</stream_url>", 
            RegexOption.DOT_MATCHES_ALL
        )
        for (channelMatch in channelRegex.findAll(content)) {
            val channelBlock = channelMatch.groupValues.getOrNull(1) ?: continue

            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = urlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()

            if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                val attributesMap = mutableMapOf<String, String>()
                // XML'den yakalanan logoyu CloudStream'in tvg-logo alanına atar
                attributesMap["tvg-logo"] = logo ?: "" 
                // Tüm kanallara zorunlu bir grup başlığı atar
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




