package com.example
import android.util.Log
// ⭐ 1. CloudStream Utils paketini ekledik (ExtractorLink, SubtitleFile, newExtractorLink burada)
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.Qualities // Qualities zaten vardı, yine de ekleyelim

import kotlin.text.* // ⭐ 2. RegEx bileşenlerini çözmek için kotlin.text.* ekledik



class Xmltv : MainAPI() {
    override var mainUrl = "https://example.com"
    override var name = "Xmltv"
    override var lang = "tr"
    override val hasMainPage = true

    private val xmlUrl = "https://raw.githubusercontent.com/mooncrown04/m3u/main/Xmltv.xml"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(xmlUrl).text
        val playlist = XmlPlaylistParser().parseXML(response)

        val homeItems = playlist.items.map {
            newMovieSearchResponse(
                name = it.title,
                url = it.url,
                type = TvType.Live
            ) {
                this.posterUrl = it.attributes["tvg-logo"]
            }
        }

        return newHomePageResponse(
            listOf(HomePageList("XML Kanalları", homeItems))
        )
    }

// Xmltv.kt içinde, loadLinks fonksiyonunun doğru hali
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    callback.invoke(
        // 'newExtractorLink' artık sadece 4 temel parametre alır
        newExtractorLink(
            source = "XMLTV", // Kaynak (source) adı
            name = this.name, // Link adı
            url = data,       // URL
            type = ExtractorLinkType.M3U8 // Veya ExtractorLinkType.LINK
        ) {
            // ⭐ REFERER, QUALITY, ve IS_M3U8 buradaki lambda içinde ayarlanır.
            this.referer = "" // referer artık direkt this.referer olarak ayarlanır
            this.quality = Qualities.Unknown.value
            // isM3u8 parametresi artık yok, çünkü ExtractorLinkType zaten M3U8 olduğunu belirtiyor.
            // Sadece HTTP başlıklarını eklemek isterseniz:
            // this.headers = emptyMap() 
        }
    )
    return true
}

// -------------------------------------------------------------
// --- XML Ayrıştırıcı Sınıfı (RegEx Tabanlı, HATASIZ) ---
// -------------------------------------------------------------

class XmlPlaylistParser {
    /**
     * XML içeriğini ayrıştırır ve Playlist nesnesine dönüştürür.
     */
    fun parseXML(content: String): Playlist {
        val playlistItems: MutableList<PlaylistItem> = mutableListOf()

        // Tüm <channel> bloklarını yakala (CDATA destekli)
        val channelRegex = Regex(
            "<channel>(.*?)</channel>",
            RegexOption.DOT_MATCHES_ALL
        )

        // Belirli alanları (CDATA dahil) yakalamak için regex'ler
        val titleRegex = Regex("<title><!\\[CDATA\\[(.*?)\\]\\]></title>", RegexOption.DOT_MATCHES_ALL)
        val logoRegex = Regex("<logo_30x30><!\\[CDATA\\[(.*?)\\]\\]></logo_30x30>", RegexOption.DOT_MATCHES_ALL)
        val urlRegex = Regex("<stream_url><!\\[CDATA\\[(.*?)\\]\\]></stream_url>", RegexOption.DOT_MATCHES_ALL)

        // Kanal bloklarını tara
        for (channelMatch in channelRegex.findAll(content)) {
            val channelBlock = channelMatch.groupValues.getOrNull(1) ?: continue

            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = urlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()

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


