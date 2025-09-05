package com.MoOnCrOwNTV

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

// --- Yardımcı Veri Sınıfları ---
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

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }
        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = 0
        var line: String? = reader.readLine()
        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    val item = playlistItems[currentIndex]
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                    val referrer = line.getTagValue("http-referrer")
                    val headers = mutableMapOf<String, String>()
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
                } else {
                    if (!line.startsWith("#")) {
                        val item = playlistItems[currentIndex]
                        val url = line.getUrl()
                        val userAgent = line.getUrlParameter("user-agent")
                        val referrer = line.getUrlParameter("referer")
                        val urlHeaders = if (referrer != null) {
                            item.headers + mapOf("referrer" to referrer)
                        } else item.headers
                        playlistItems[currentIndex] = item.copy(
                            url = url,
                            headers = item.headers + urlHeaders,
                            userAgent = userAgent ?: item.userAgent
                        )
                        currentIndex++
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

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}

// --- Ana Eklenti Sınıfı ---
class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "placeholder"
    override var name = "35 MoOnCrOwN TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    // Yeni: Birden fazla M3U adresini içeren liste
    private val mainUrls = setOf(
        "https://dl.dropbox.com/scl/fi/r4p9v7g76ikwt8zsyuhyn/sile.m3u?rlkey=esnalbpm4kblxgkvym51gjokm",
        "https://raw.githubusercontent.com/iptv-org/iptv/master/channels/tr.m3u"
        // Buraya istediğiniz diğer M3U URL'lerini ekleyebilirsiniz
    )

    // Yeni: Tüm kanalları bir defa çekip bellekte tutan bir önbellek
    private var allChannelsCache: List<PlaylistItem>? = null
    private val lock = Any() // Ön bellek için senkronizasyon nesnesi

    // Yeni: Tüm M3U listelerini eş zamanlı olarak çekip birleştiren fonksiyon
    private suspend fun getAllChannels(): List<PlaylistItem> {
        if (allChannelsCache == null) {
            val allRequests = mainUrls.map { url ->
                async {
                    try {
                        val content = app.get(url).text
                        IptvPlaylistParser().parseM3U(content).items
                    } catch (e: Exception) {
                        Log.e("MoOnCrOwNTV", "Failed to fetch or parse URL: $url", e)
                        emptyList()
                    }
                }
            }
            // Tüm işlemlerin bitmesini bekler ve sonuçları tek bir listede birleştirir
            val combinedList = allRequests.awaitAll().flatten()
            
            // Kanalların benzersiz olmasını sağlamak için mükerrerleri eleme
            val seenTitles = ConcurrentHashMap<String, Boolean>()
            val uniqueChannels = combinedList.filter { channel ->
                // title boşsa ele
                val title = channel.title ?: return@filter false
                // aynı isimli kanalları ele
                seenTitles.putIfAbsent(title, true) == null
            }

            allChannelsCache = uniqueChannels
        }
        return allChannelsCache!!
    }

    // JSON verilerini kolayca taşımak için veri sınıfı
    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = getAllChannels()
        
        return newHomePageResponse(
            kanallar.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show = group.value.map { kanal ->
                    val streamurl = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl = kanal.attributes["tvg-logo"].toString()
                    val chGroup = kanal.attributes["group-title"].toString()
                    val nation = kanal.attributes["tvg-country"].toString()
                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }
                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = getAllChannels()
        
        return kanallar.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString()
            val chGroup = kanal.attributes["group-title"].toString()
            val nation = kanal.attributes["tvg-country"].toString()
            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                type = TvType.Live
            ) {
                this.posterUrl = posterurl
                this.lang = nation
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

        val kanallar = getAllChannels()
        val recommendations = mutableListOf<LiveSearchResponse>()

        for (kanal in kanallar) {
            if (kanal.attributes["group-title"].toString() == loadData.group) {
                val rcStreamUrl = kanal.url.toString()
                val rcChannelName = kanal.title.toString()
                if (rcChannelName == loadData.title) continue

                val rcPosterUrl = kanal.attributes["tvg-logo"].toString()
                val rcChGroup = kanal.attributes["group-title"].toString()
                val rcNation = kanal.attributes["tvg-country"].toString()

                recommendations.add(
                    newLiveSearchResponse(
                        rcChannelName,
                        LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcNation).toJson(),
                        type = TvType.Live
                    ) {
                        this.posterUrl = rcPosterUrl
                        this.lang = rcNation
                    })
            }
        }

        return newLiveStreamLoadResponse(loadData.title, loadData.url, url) {
            this.posterUrl = loadData.poster
            this.plot = nation
            this.tags = listOf(loadData.group, loadData.nation)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        Log.d("IPTV", "loadData » $loadData")

        val kanallar = getAllChannels()
        val kanal = kanallar.first { it.url == loadData.url }
        Log.d("IPTV", "kanal » $kanal")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = loadData.url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = kanal.headers["referrer"] ?: ""
                this.headers = kanal.headers
                quality = Qualities.Unknown.value
            }
        )
        return true
    }

    // Yardımcı fonksiyon
    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = getAllChannels()
            val kanal = kanallar.items.first { it.url == data }
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString()
            val chGroup = kanal.attributes["group-title"].toString()
            val nation = kanal.attributes["tvg-country"].toString()
            return LoadData(streamurl, channelname, posterurl, chGroup, nation)
        }
    }
}
