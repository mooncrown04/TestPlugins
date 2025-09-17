package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleFile

data class PlaylistItem(
    val title: String?,
    val url: String?,
    val attributes: Map<String, String> = emptyMap(),
    val score: Int? = null
)

data class LoadData(
    val items: List<PlaylistItem>,
    val title: String,
    val poster: String?,
    val group: String?,
    val nation: String?,
    val season: Int?,
    val episode: Int?,
    val isDubbed: Boolean,
    val isSubbed: Boolean,
    val score: Int?
)

class AnimeDizi : MainAPI() {
    override var mainUrl = "https://example.com"
    override var name = "AnimeDizi"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    private val dubbedKeywords = listOf("tr dublaj", "dublaj", "dub")
    private val subbedKeywords = listOf("altyazı", "sub")

    private fun parseEpisodeInfo(title: String): Triple<String, Int?, Int?> {
        val cleanTitle = title.replace(Regex("S\\d+E\\d+", RegexOption.IGNORE_CASE), "").trim()
        val season = Regex("S(\\d+)E\\d+", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
        val episode = Regex("S\\d+E(\\d+)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
        return Triple(cleanTitle, season, episode)
    }

    override suspend fun load(url: String): LoadResponse {
        // Örnek dummy liste
        val allShows = listOf(
            PlaylistItem("Naruto S01E01 TR Dublaj", "https://cdn1/naruto1dub.m3u8", mapOf("tvg-logo" to "poster1.jpg"), score = 80),
            PlaylistItem("Naruto S01E01 TR Altyazılı", "https://cdn2/naruto1sub.m3u8", mapOf("tvg-logo" to "poster1.jpg"), score = 75),
            PlaylistItem("Naruto S01E02 TR Dublaj", "https://cdn1/naruto2dub.m3u8", mapOf("tvg-logo" to "poster2.jpg"), score = 85)
        )

        val dubbedEpisodes = mutableListOf<Episode>()
        val subbedEpisodes = mutableListOf<Episode>()
        val unknownEpisodes = mutableListOf<Episode>()

        // --- Bölüm bazlı grupla
        val groupedByEpisode = allShows.groupBy { item ->
            val (cleanTitle, season, episode) = parseEpisodeInfo(item.title ?: "")
            Triple(cleanTitle, season ?: 1, episode ?: 1)
        }

        groupedByEpisode.forEach { (key, episodeItems) ->
            val (itemCleanTitle, season, episode) = key
            val posterFromItem = episodeItems.firstOrNull()?.attributes?.get("tvg-logo")
            val groupTitle = episodeItems.firstOrNull()?.attributes?.get("group-title") ?: "Bilinmeyen Grup"
            val nation = episodeItems.firstOrNull()?.attributes?.get("tvg-country") ?: "TR"

            val isDubbedGroup = episodeItems.any { it.title?.lowercase()?.let { t -> dubbedKeywords.any { kw -> t.contains(kw) } } == true } ||
                    episodeItems.any { it.attributes["tvg-language"]?.lowercase() == "tr" }
            val isSubbedGroup = episodeItems.any { it.title?.lowercase()?.let { t -> subbedKeywords.any { kw -> t.contains(kw) } } == true } ||
                    episodeItems.any { it.attributes["tvg-language"]?.lowercase() == "en" }

            val scoreGroup = episodeItems.mapNotNull { it.score }.maxOrNull()

            val episodeLoadDataJson = LoadData(
                items = episodeItems,
                title = itemCleanTitle,
                poster = posterFromItem,
                group = groupTitle,
                nation = nation,
                season = season,
                episode = episode,
                isDubbed = isDubbedGroup,
                isSubbed = isSubbedGroup,
                score = scoreGroup
            ).toJson()

            val episodeObj = newEpisode(episodeLoadDataJson) {
                name = if (episode > 0) "$itemCleanTitle S$season E$episode" else itemCleanTitle
                this.season = season
                this.episode = episode
                posterUrl = posterFromItem
            }

            when {
                isDubbedGroup -> dubbedEpisodes.add(episodeObj)
                isSubbedGroup -> subbedEpisodes.add(episodeObj)
                else -> unknownEpisodes.add(episodeObj)
            }
        }

        val posterUrl = allShows.firstOrNull()?.attributes?.get("tvg-logo")
        val description = "Anime/Dizi içerikleri"

        return newAnimeLoadResponse("AnimeDizi İçerikleri", url, TvType.Anime) {
            posterUrl?.let { poster = it }
            plot = description
            addEpisodes(DubStatus.Dubbed, dubbedEpisodes)
            addEpisodes(DubStatus.Subbed, subbedEpisodes)
            addEpisodes(DubStatus.Unknown, unknownEpisodes)
            tags = listOf("Anime", "Dizi")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)

        loadData.items.forEachIndexed { index, item ->
            val sourceName = "${loadData.title} Kaynak ${index + 1}"
            val link = ExtractorLink(
                source = name,
                name = sourceName,
                url = item.url ?: return@forEachIndexed,
                referer = "",
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
            callback(link)
        }
        return true
    }
}
