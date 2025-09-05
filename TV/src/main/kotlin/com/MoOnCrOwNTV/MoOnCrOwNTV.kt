package com.MoOnCrOwNTV

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope

// --- Ana Eklenti Sınıfı ---
class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/r4p9v7g76ikwt8zsyuhyn/sile.m3u?rlkey=esnalbpm4kblxgkvym51gjokm"
    override var name = "35 MoOnCrOwN TV"
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
                Log.e("MoOnCrOwNTV", "Failed to fetch or parse URL: $mainUrl", e)
                ""
            }

            val combinedList = IptvPlaylistParser().parseM3U(content).items
            val cleanedList = combinedList.filter { it.title != null && it.url != null }
            allGroupedChannelsCache = cleanedList.groupBy { it.title!! }
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
