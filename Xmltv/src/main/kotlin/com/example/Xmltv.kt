package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import kotlinx.coroutines.coroutineScope

// Yalnızca birer kez içe aktarılmalı
import kotlin.text.Regex 
import kotlin.text.RegexOption 


// --- Ana Eklenti Sınıfı ---
class Xmltv : MainAPI() {
    // mainUrl artık .m3u veya .xml ile bitebilir
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    override var name = "35 MoOnCrOwN d TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private var allGroupedChannelsCache: Map<String, List<PlaylistItem>>? = null

    private suspend fun getAllGroupedChannels(): Map<String, List<PlaylistItem>> {
        if (allGroupedChannelsCache == null) {
            val content = try {
                app.get(mainUrl).text
            } catch (e: Exception) {
                Log.e("MoOnCrOwNTV", "Failed to fetch URL: $mainUrl", e)
                ""
            }

            val isXml = mainUrl.endsWith(".xml", ignoreCase = true)

            val parsedItems = if (isXml) {
                // XML dosyası ise, yeni RegEx tabanlı parser'ı kullan
                try {
                    XmlPlaylistParser().parseXML(content).items
                } catch (e: Exception) {
                    Log.e("MoOnCrOwNTV", "Failed to parse XML: $mainUrl", e)
                    emptyList()
                }
            } else {
                // Varsayılan olarak M3U/M3U8 parser'ı kullan
                try {
                    IptvPlaylistParser().parseM3U(content).items
                } catch (e: Exception) {
                    Log.e("MoOnCrOwNTV", "Failed to parse M3U: $mainUrl", e)
                    emptyList()
                }
            }

            val cleanedList = parsedItems.filter { it.title != null && it.url != null }
            
            // XML'den gelen kanallara varsayılan bir grup başlığı ekle (getMainPage için gerekli)
            val finalCleanedList = cleanedList.map { item ->
                if (isXml && item.attributes["group-title"].isNullOrBlank()) {
                    item.copy(
                        attributes = item.attributes.toMutableMap().apply {
                            put("group-title", "XML Kanalları")
                        }.toMap()
                    )
                } else {
                    item
                }
            }

            allGroupedChannelsCache = finalCleanedList.groupBy { it.title!! }
        }
        return allGroupedChannelsCache!!
    }

    data class LoadData(
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val urls: List<String>,
        val headers: Map<String, Map<String, String>>
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val groupedChannels = getAllGroupedChannels()
        val uniqueChannelsByTitle = groupedChannels.values.mapNotNull { it.firstOrNull() }
        val groupedByCategories = uniqueChannelsByTitle.groupBy { it.attributes["group-title"] ?: "Diğer" }

        val homepageList = groupedByCategories.mapNotNull { (groupTitle, channelList) ->
            if (groupTitle.isNullOrBlank() || channelList.isEmpty()) {
                null
            } else {
                val show = channelList.mapNotNull { kanal ->
                    val channelname = kanal.title
                    val posterurl = kanal.attributes["tvg-logo"]
                    val chGroup = kanal.attributes["group-title"]
                    val nation = kanal.attributes["tvg-country"]

                    if (channelname.isNullOrBlank()) {
                        null
                    } else {
                        val channelsWithSameTitle = groupedChannels[channelname] ?: emptyList()
                        newLiveSearchResponse(
                            channelname,
                            LoadData(
                                title = channelname,
                                poster = posterurl ?: "",
                                group = chGroup ?: "",
                                nation = nation ?: "",
                                urls = channelsWithSameTitle.mapNotNull { it.url },
                                headers = channelsWithSameTitle.mapNotNull { it.url?.let { url -> url to it.headers } }?.toMap() ?: emptyMap()
                            ).toJson(),
                            type = TvType.Live
                        ) {
                            this.posterUrl = posterurl
                            this.lang = nation
                        }
                    }
                }
                if (show.isNotEmpty()) {
                    HomePageList(groupTitle, show, isHorizontalImages = true)
                } else {
                    null
                }
            }
        }
        return newHomePageResponse(homepageList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val groupedChannels = getAllGroupedChannels()

        return groupedChannels.filter { (title, _) ->
            title.lowercase().contains(query.lowercase())
        }.mapNotNull { (title, channels) ->
            val firstChannel = channels.firstOrNull() ?: return@mapNotNull null
            val channelname = firstChannel.title
            val posterurl = firstChannel.attributes["tvg-logo"]
            val chGroup = firstChannel.attributes["group-title"]
            val nation = firstChannel.attributes["tvg-country"]

            if (channelname.isNullOrBlank()) {
                null
            } else {
                newLiveSearchResponse(
                    channelname,
                    LoadData(
                        title = channelname,
                        poster = posterurl ?: "",
                        group = chGroup ?: "",
                        nation = nation ?: "",
                        urls = channels.mapNotNull { it.url },
                        headers = channels.mapNotNull { it.url?.let { url -> url to it.headers } }?.toMap() ?: emptyMap()
                    ).toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = fetchDataFromUrlOrJson(url)
        val nation: String = if (loadData.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${loadData.group} | ${loadData.nation} «"
        }

        val recommendations = mutableListOf<LiveSearchResponse>()
        val groupedChannels = getAllGroupedChannels()
        val allChannels = groupedChannels.values.flatten()

        for (kanal in allChannels) {
            if (kanal.attributes["group-title"].toString() == loadData.group) {
                val rcChannelName = kanal.title
                if (rcChannelName.isNullOrBlank() || rcChannelName == loadData.title) continue
                val rcPosterUrl = kanal.attributes["tvg-logo"]
                val rcChGroup = kanal.attributes["group-title"]
                val rcNation = kanal.attributes["tvg-country"]
                val channelsWithSameTitle = groupedChannels[rcChannelName] ?: emptyList()
                if (channelsWithSameTitle.isNotEmpty()) {
                    recommendations.add(
                        newLiveSearchResponse(
                            rcChannelName,
                            LoadData(
                                title = rcChannelName,
                                poster = rcPosterUrl ?: "",
                                group = rcChGroup ?: "",
                                nation = rcNation ?: "",
                                urls = channelsWithSameTitle.mapNotNull { it.url },
                                headers = channelsWithSameTitle.mapNotNull { it.url?.let { url -> url to it.headers } }?.toMap() ?: emptyMap()
                            ).toJson(),
                            type = TvType.Live
                        ) {
                            this.posterUrl = rcPosterUrl
                            this.lang = rcNation
                        }
                    )
                }
            }
        }

        val uniqueRecommendations = recommendations.distinctBy { it.name }
        val firstUrl = loadData.urls.firstOrNull() ?: ""

        return newLiveStreamLoadResponse(loadData.title, firstUrl, url) {
            this.posterUrl = loadData.poster
            this.plot = nation
            this.tags = listOf(loadData.group, loadData.nation)
            this.recommendations = uniqueRecommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        Log.d("IPTV", "loadData » $loadData")

        loadData.urls.forEachIndexed { index, url ->
            val headers = loadData.headers[url] ?: emptyMap()
            val name = if (loadData.urls.size > 1) "${this.name} Kaynak ${index + 1}" else this.name

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = headers["referrer"] ?: ""
                    this.headers = headers
                    quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val groupedChannels = getAllGroupedChannels()
            val allChannels = groupedChannels.values.flatten()
            val kanal = allChannels.firstOrNull { it.url == data }

            if (kanal == null || kanal.title == null || kanal.url == null) {
                return LoadData("", "", "", "", emptyList(), emptyMap())
            }

            val channelsWithSameTitle = groupedChannels[kanal.title] ?: emptyList()

            return LoadData(
                title = kanal.title,
                poster = kanal.attributes["tvg-logo"] ?: "",
                group = kanal.attributes["group-title"] ?: "",
                nation = kanal.attributes["tvg-country"] ?: "",
                urls = channelsWithSameTitle.mapNotNull { it.url },
                headers = channelsWithSameTitle.mapNotNull { it.url?.let { url -> url to it.headers } }?.toMap() ?: emptyMap()
            )
        }
    }
}

// -------------------------------------------------------------
// --- Yardımcı Sınıflar (M3U ve XML Ortak Veri Yapıları) ---
// -------------------------------------------------------------

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}

data class Playlist(
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
)

// -------------------------------------------------------------
// --- M3U Ayrıştırıcı Sınıfı ---
// -------------------------------------------------------------

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        val firstLine = reader.readLine()
        if (firstLine == null || !firstLine.isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = -1

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    playlistItems.add(PlaylistItem(title, attributes))
                    currentIndex++
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    if (currentIndex >= 0 && currentIndex < playlistItems.size) {
                        val item = playlistItems[currentIndex]
                        val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                        val referrer = line.getTagValue("http-referrer")
                        val headers = item.headers.toMutableMap()

                        if (userAgent != null) {
                            headers["user-agent"] = userAgent
                        }
                        if (referrer != null) {
                            headers["referrer"] = referrer
                        }
                        playlistItems[currentIndex] = item.copy(
                            userAgent = userAgent,
                            headers = headers
                        )
                    }
                } else if (!line.startsWith("#")) {
                    if (currentIndex >= 0 && currentIndex < playlistItems.size) {
                        val item = playlistItems[currentIndex]
                        val url = line.getUrl()
                        val userAgent = line.getUrlParameter("user-agent")
                        val referrer = line.getUrlParameter("referer")
                        val urlHeaders = item.headers.toMutableMap()

                        if (referrer != null) {
                            urlHeaders["referrer"] = referrer
                        }

                        playlistItems[currentIndex] = item.copy(
                            url = url,
                            headers = urlHeaders,
                            userAgent = userAgent ?: item.userAgent
                        )
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()

        return attributesString
            .split(Regex("\\s"))
            .mapNotNull {
                val pair = it.split("=")
                if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
            }
            .toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

// -------------------------------------------------------------
// --- YENİ XML Ayrıştırıcı Sınıfı (RegEx Tabanlı) ---
// -------------------------------------------------------------

class XmlPlaylistParser {
    /**
     * RegEx kullanarak XML içeriğini okur ve PlaylistItem listesine dönüştürür.
     * Bu yöntem, CDATA bloklarını ve basit XML yapısını güvenilir bir şekilde ayrıştırır.
     */
    fun parseXML(content: String): Playlist {
        val playlistItems: MutableList<PlaylistItem> = mutableListOf()

        // Her bir <channel> bloğunu yakalamak için genel regex.
        val channelRegex = Regex(
            "<channel>(.*?)</channel>", 
            RegexOption.DOT_ALL // Noktanın yeni satırları da kapsamasını sağlar
        )

        // Belirli alanları (CDATA dahil) yakalamak için yardımcı regex'ler.
        // \\s*? aradaki boşlukları (whitespace) da dikkate alır.
        val titleRegex = Regex("<title><!\\[CDATA\\[\\s*(.*?)\\s*\\]\\]></title>", RegexOption.DOT_ALL)
        val logoRegex = Regex("<logo_30x30><!\\[CDATA\\[\\s*(.*?)\\s*\\]\\]></logo_30x30>", RegexOption.DOT_ALL)
        val urlRegex = Regex("<stream_url><!\\[CDATA\\[\\s*(.*?)\\s*\\]\\]></stream_url>", RegexOption.DOT_ALL)
        
        // Tüm <channel> bloklarını bul ve döngüye al
        channelRegex.findAll(content).forEach { channelMatch ->
            // groupValues[1] yakalama grubundaki içeriği verir (yani <channel> ve </channel> arasındaki her şey)
            val channelBlock = channelMatch.groupValues[1]

            // 1. Yakalama grubundaki (.*?) değeri, CDATA içindeki içeriği döndürür.
            val title = titleRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val logo = logoRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()
            val url = urlRegex.find(channelBlock)?.groupValues?.getOrNull(1)?.trim()

            // Başlık veya URL boşsa veya sadece boşluk içeriyorsa bu kanalı atla
            if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                
                val attributesMap = mutableMapOf<String, String>()
                attributesMap["tvg-logo"] = logo ?: ""
                // XML'de grup bilgisi yoksa varsayılan bir grup ekle
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
             Log.e("MoOnCrOwNTV", "XML ayrıştırma (RegEx) başarılı, ancak hiç geçerli kanal bulunamadı.")
        } else {
             Log.d("MoOnCrOwNTV", "XML ayrıştırma başarılı: ${playlistItems.size} kanal bulundu.")
        }
        
        return Playlist(playlistItems)
    }
}




