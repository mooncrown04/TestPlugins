package com.mooncrown04

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.Locale

class CineStreamProvider : MainAPI() {
    override var mainUrl = "https://tinyurl.com"
    override var name = "CineStream"
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasMainPage = true
    override val lang = "tr"

    companion object {
        private const val DEFAULT_POSTER_URL =
            "https://static.vecteezy.com/system/resources/previews/005/337/799/non_2x/play-button-icon-in-flat-style-video-play-symbol-illustration-on-isolated-background-player-sign-business-concept-vector.jpg"
    }

    // Parser for M3U
    object IptvPlaylistParser {
        fun parse(playlistContent: String): List<PlaylistItem> {
            val lines = playlistContent.lines()
            val items = mutableListOf<PlaylistItem>()
            var currentAttributes = mutableMapOf<String, String>()
            var currentTitle = ""

            for (line in lines) {
                if (line.startsWith("#EXTINF:")) {
                    val parts = line.substringAfter("#EXTINF:").split(",", limit = 2)
                    currentAttributes = parseAttributes(parts[0])
                    currentTitle = parts.getOrNull(1)?.trim() ?: ""
                } else if (line.isNotBlank() && !line.startsWith("#")) {
                    items.add(PlaylistItem(currentTitle, line.trim(), currentAttributes))
                    currentAttributes = mutableMapOf()
                    currentTitle = ""
                }
            }
            return items
        }

        private fun parseAttributes(attributeString: String): MutableMap<String, String> {
            val attributes = mutableMapOf<String, String>()
            val regex = Regex("""(\w+)=["]([^"]+)["]""")
            regex.findAll(attributeString).forEach {
                attributes[it.groupValues[1]] = it.groupValues[2]
            }
            return attributes
        }
    }

    data class PlaylistItem(val title: String, val url: String?, val attributes: Map<String, String>)
    data class EpisodeInfo(val season: Int, val episode: Int, val cleanTitle: String)

    private fun parseEpisodeInfo(title: String): EpisodeInfo? {
        val regexList = listOf(
            Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""Sezon\s*(\d+)\s*Bölüm\s*(\d+)""", RegexOption.IGNORE_CASE)
        )
        for (regex in regexList) {
            val match = regex.find(title)
            if (match != null) {
                val season = match.groupValues[1].toIntOrNull() ?: 1
                val episode = match.groupValues[2].toIntOrNull() ?: 1
                val cleanTitle = title.substringBefore(match.value).trim()
                return EpisodeInfo(season, episode, cleanTitle)
            }
        }
        return null
    }

    data class LoadData(
        val urls: List<String>,
        val cleanTitle: String,
        val posterUrl: String,
        val group: String,
        val nation: String
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlistContent = app.get("$mainUrl/2ao2rans").text
        val items = IptvPlaylistParser.parse(playlistContent)
        val groupedByTitle = items.groupBy { it.title.substringBefore("-").trim() }

        val alphabet = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ"
        val skipMap = mutableMapOf<String, Int>()

        val groupedByCleanTitle = items.mapNotNull {
            val epInfo = parseEpisodeInfo(it.title)
            epInfo?.cleanTitle to it
        }.groupBy({ it.first }, { it.second })

        val alphabeticGroups = groupedByCleanTitle.toSortedMap().mapNotNull { (cleanTitle, shows) ->
            val firstShow = shows.firstOrNull() ?: return@mapNotNull null
            val allUrls = shows.mapNotNull { it.url } // 🔥 aynı diziye ait tüm linkleri topla
            val posterUrl = firstShow.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
            val nation = firstShow.attributes["tvg-country"]?.toString() ?: "TR"
            val group = firstShow.attributes["group-title"]?.toString() ?: cleanTitle

            val searchResponse = newLiveSearchResponse(
                cleanTitle,
                LoadData(allUrls, cleanTitle, posterUrl, group, nation).toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl = posterUrl
                this.lang = nation
            }

            val firstChar = cleanTitle.firstOrNull()?.uppercaseChar() ?: '#'
            val groupKey = when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#"
            }
            Pair(groupKey, searchResponse)
        }

        val homePages = alphabet.map { char ->
            val shows = alphabeticGroups.filter { it.first.equals(char.toString(), true) }
                .map { it.second }
                .sortedBy { it.name.lowercase(Locale("tr", "TR")) }

            if (shows.isNotEmpty()) {
                val remainingAlphabet = alphabet.substringAfter(char, "")
                HomePageList("🎬 **$char** ${remainingAlphabet.lowercase(Locale("tr", "TR"))}", shows)
            } else null
        }.filterNotNull()

        return newHomePageResponse(homePages)
    }

    private suspend fun fetchDataFromUrlOrJson(data: String, playlistContent: String): List<PlaylistItem> {
        val items = IptvPlaylistParser.parse(playlistContent)
        val loadData = parseJson<LoadData>(data)
        return items.filter { it.url != null && loadData.urls.contains(it.url) }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val playlistContent = app.get("$mainUrl/2ao2rans").text
        val items = IptvPlaylistParser.parse(playlistContent)
        return items.mapNotNull {
            val epInfo = parseEpisodeInfo(it.title)
            epInfo?.let { info ->
                if (info.cleanTitle.contains(query, true)) {
                    val allUrls = items.filter { x ->
                        parseEpisodeInfo(x.title)?.cleanTitle == info.cleanTitle
                    }.mapNotNull { x -> x.url }

                    val posterUrl = it.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
                    val nation = it.attributes["tvg-country"]?.toString() ?: "TR"
                    val group = it.attributes["group-title"]?.toString() ?: info.cleanTitle

                    newLiveSearchResponse(
                        info.cleanTitle,
                        LoadData(allUrls, info.cleanTitle, posterUrl, group, nation).toJson(),
                        type = TvType.TvSeries
                    ) {
                        this.posterUrl = posterUrl
                        this.lang = nation
                    }
                } else null
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val playlistContent = app.get("$mainUrl/2ao2rans").text
        val loadData = parseJson<LoadData>(url)

        val episodeItems = IptvPlaylistParser.parse(playlistContent)
            .mapNotNull { item ->
                val epInfo = parseEpisodeInfo(item.title)
                epInfo?.takeIf { it.cleanTitle == loadData.cleanTitle }?.let {
                    Episode(
                        data = LoadData(
                            listOf(item.url!!),
                            loadData.cleanTitle,
                            loadData.posterUrl,
                            loadData.group,
                            loadData.nation
                        ).toJson(),
                        name = item.title,
                        season = it.season,
                        episode = it.episode,
                        posterUrl = item.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: loadData.posterUrl
                    )
                }
            }

        return newTvSeriesLoadResponse(
            loadData.cleanTitle,
            url,
            TvType.TvSeries,
            episodeItems
        ) {
            posterUrl = loadData.posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        loadData.urls.forEachIndexed { index, url ->
            callback.invoke(
                ExtractorLink(
                    name,
                    "Kaynak ${index + 1}",
                    url,
                    mainUrl,
                    Qualities.Unknown.value
                )
            )
        }
        return true
    }
}
