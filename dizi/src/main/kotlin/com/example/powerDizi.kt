package com.example

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream


// Yardımcı sınıflar
data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
) {
    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())
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
                if (line.startsWith(PlaylistItem.EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(PlaylistItem.EXT_VLC_OPT)) {
                    // VLC OPT işlemleri burada ele alınabilir
                } else if (!line.startsWith("#")) {
                    val item = playlistItems.getOrNull(currentIndex)
                    if (item != null) {
                        val url = line.getUrl()
                        playlistItems[currentIndex] = item.copy(url = url)
                        currentIndex++
                    } else {
                        // Eğer EXTINF satırı yoksa bu URL'yi atla
                        Log.w("IptvPlaylistParser", "URL'ye karşılık gelen EXTINF satırı bulunamadı, atlanıyor: $line")
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)
    private fun String.getTitle(): String? = split(",").lastOrNull()?.trim()
    private fun String.getUrl(): String? = split("|").firstOrNull()?.trim()

    // Bu fonksiyon güncellenmiştir.
    private fun String.getAttributes(): Map<String, String> {
        val attributesString = substringAfter("#EXTINF:-1 ")
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9-]+)="([^"]*)"""")
        regex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value
        }
        return attributes
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""")
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""")

    val matchResult1 = format1Regex.find(text)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult2 = format2Regex.find(text)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult3 = format3Regex.find(text)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    return Triple(text.trim(), null, null)
}

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "35 MoOnCrOwN Dizi 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)


// Poster yoksa kullanılacak varsayılan resim URL'si
    private val DEFAULT_POSTER_URL = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"
 //private val DEFAULT_POSTER_URL = "https://dizifun5.com/images/data/too-hot-to-handle.webp"



    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val processedItems = kanallar.items.map { item ->
            val (cleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
            item.copy(
                title = cleanTitle,
                attributes = item.attributes.toMutableMap().apply {
                    put("tvg-country", "TR/Altyazılı")
                    put("tvg-language", "TR;EN")
                }
            )
        }

        val alphabeticGroups = processedItems.groupBy { item ->
            val firstChar = item.title?.firstOrNull()?.uppercaseChar() ?: '#'
            when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#"
            }
        }.toSortedMap()

        val homePageLists = mutableListOf<HomePageList>()

        alphabeticGroups.forEach { (letter, shows) ->
            val searchResponses = shows.distinctBy { it.title }.map { kanal ->
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"]?.toString()?: DEFAULT_POSTER_URL
                val nation = kanal.attributes["tvg-country"].toString()

                val loadData = LoadData(kanal.url.toString(), channelname, posterurl, letter, nation)
                val jsonData = loadData.toJson()

                newLiveSearchResponse(
                    channelname,
                    jsonData,
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
            if (searchResponses.isNotEmpty()) {
                val listTitle = when (letter) {
                    "#" -> "# Özel Karakterle Başlayanlar"
                    "0-9" -> "0-9 rakam olarak başlayan DİZİLER"
                    else -> "$letter ile başlayanlar DİZİLER"
                }
                homePageLists.add(HomePageList(listTitle, searchResponses, isHorizontalImages = true))
            }
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"]?.toString()?: DEFAULT_POSTER_URL
            val chGroup = kanal.attributes["group-title"].toString()
            val nation = kanal.attributes["tvg-country"].toString()

            val (cleanTitle, season, episode) = parseEpisodeInfo(channelname)

            newLiveSearchResponse(
                cleanTitle,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, season ?: 1, episode ?: 0).toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = fetchDataFromUrlOrJson(url)
        val (cleanTitle, _, _) = parseEpisodeInfo(loadData.title)

        val finalPosterUrl = loadData.poster
        val plot = "TMDB'den özet alınamadı."

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val allShows = kanallar.items.groupBy { item ->
            val (itemCleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
            itemCleanTitle
        }

        val currentShowEpisodes = allShows[cleanTitle]?.mapNotNull { kanal ->
            val title = kanal.title.toString()
            val (episodeCleanTitle, season, episode) = parseEpisodeInfo(title)

            if (season != null && episode != null) {
                newEpisode(LoadData(kanal.url.toString(), title, kanal.attributes["tvg-logo"]?.toString()?: DEFAULT_POSTER_URL, kanal.attributes["group-title"].toString(), kanal.attributes["tvg-country"]?.toString() ?: "TR", season, episode).toJson()) {
                    this.name = episodeCleanTitle
                    this.season = season
                    this.episode = episode
                    this.posterUrl = kanal.attributes["tvg-logo"]?.toString()?: DEFAULT_POSTER_URL
                }
            } else null
        }?.sortedWith(compareBy({ it.season }, { it.episode })) ?: emptyList()

        return newTvSeriesLoadResponse(
            cleanTitle,
            url,
            TvType.TvSeries,
            currentShowEpisodes.map { episode ->
                episode.apply {
                    val episodeLoadData = parseJson<LoadData>(episode.data)
                    this.posterUrl = episodeLoadData.poster
                }
            }
        ) {
            this.posterUrl = finalPosterUrl
            this.plot = plot
            this.tags = listOf(loadData.group, loadData.nation)
        }
    }

override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        val videoUrl = loadData.url

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${loadData.title} (S${loadData.season}:E${loadData.episode})",
                url = videoUrl,
                type = ExtractorLinkType.M3U8
            ) {
                // Kalite parametresi bu bloğun içine yerleştirilmelidir.
                quality = Qualities.Unknown.value
            }
        )
        return true
    }
    

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 1,
        val episode: Int = 0
    )

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.first { it.url == data }
            val (cleanTitle, season, episode) = parseEpisodeInfo(kanal.title.toString())

            return LoadData(
                kanal.url.toString(),
                cleanTitle,
                kanal.attributes["tvg-logo"]?.toString()?: DEFAULT_POSTER_URL,
                kanal.attributes["group-title"].toString(),
                kanal.attributes["tvg-country"].toString(),
                season ?: 1,
                episode ?: 0
            )
        }
    }
}
