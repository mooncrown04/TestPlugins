package com.mooncrown

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus

class Film(private val context: android.content.Context, private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "35 sinema 📺"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return newHomePageResponse(
            kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { group ->
                val title = group.key
                val show = group.value.map { kanal ->
                    val streamurl = kanal.url ?: ""
                    val channelname = kanal.title ?: "Bilinmiyor"
                    val posterurl = kanal.attributes["tvg-logo"] ?: ""
                    val chGroup = kanal.attributes["group-title"] ?: "Diğer"
                    val language = kanal.attributes["tvg-language"] ?: ""
                    val nation = kanal.attributes["tvg-country"] ?: ""

                    val watchKey = "watch_${streamurl.hashCode()}"
                    val progressKey = "progress_${streamurl.hashCode()}"
                    val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
                    val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

                    val isDubbed = language.lowercase() == "turkish"
                    val isSubbed = chGroup.contains("Altyazılı", ignoreCase = true) || channelname.contains("Altyazı", ignoreCase = true)

                    // Anime veya Movie için ayır
                    val type = if (chGroup.contains("Anime", ignoreCase = true)) TvType.Anime else TvType.Movie

                    if (type == TvType.Anime) {
                        // Anime response
                        newAnimeSearchResponse(
                            name = channelname,
                            url = LoadData(
                                streamurl,
                                channelname,
                                posterurl,
                                chGroup,
                                language,
                                nation,
                                isWatched,
                                watchProgress,
                                isDubbed,
                                isSubbed
                            ).toJson(),
                            type = type
                        ) {
                            this.posterUrl = posterurl
                            addDubStatus(
                                dubExist = isDubbed,
                                subExist = isSubbed
                            )
                        }
                    } else {
                        // Movie response
                        newMovieSearchResponse(
                            name = channelname,
                            url = LoadData(
                                streamurl,
                                channelname,
                                posterurl,
                                chGroup,
                                language,
                                nation,
                                isWatched,
                                watchProgress,
                                isDubbed,
                                isSubbed
                            ).toJson(),
                            type = type
                        ) {
                            this.posterUrl = posterurl
                            description = "Grup: $chGroup, Dil: $language"
                        }
                    }
                }

                HomePageList(title, show, isHorizontalImages = false)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return kanallar.items.filter {
            val normalizedQuery = query.lowercase()
            val normalizedTitle = it.title?.lowercase() ?: ""
            val normalizedLanguage = it.attributes["tvg-language"]?.lowercase() ?: ""

            normalizedTitle.contains(normalizedQuery) || normalizedLanguage.contains(normalizedQuery)
        }.map { kanal ->
            val streamurl = kanal.url ?: ""
            val channelname = kanal.title ?: "Bilinmiyor"
            val posterurl = kanal.attributes["tvg-logo"] ?: ""
            val chGroup = kanal.attributes["group-title"] ?: "Diğer"
            val language = kanal.attributes["tvg-language"] ?: ""
            val nation = kanal.attributes["tvg-country"] ?: ""

            val watchKey = "watch_${streamurl.hashCode()}"
            val progressKey = "progress_${streamurl.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            val isDubbed = language.lowercase() == "turkish"
            val isSubbed = chGroup.contains("Altyazılı", ignoreCase = true) || channelname.contains("Altyazı", ignoreCase = true)

            val type = if (chGroup.contains("Anime", ignoreCase = true)) TvType.Anime else TvType.Movie

            if (type == TvType.Anime) {
                newAnimeSearchResponse(
                    name = channelname,
                    url = LoadData(
                        streamurl,
                        channelname,
                        posterurl,
                        chGroup,
                        language,
                        nation,
                        isWatched,
                        watchProgress,
                        isDubbed,
                        isSubbed
                    ).toJson(),
                    type = type
                ) {
                    this.posterUrl = posterurl
                    addDubStatus(
                        dubExist = isDubbed,
                        subExist = isSubbed
                    )
                }
            } else {
                newMovieSearchResponse(
                    name = channelname,
                    url = LoadData(
                        streamurl,
                        channelname,
                        posterurl,
                        chGroup,
                        language,
                        nation,
                        isWatched,
                        watchProgress,
                        isDubbed,
                        isSubbed
                    ).toJson(),
                    type = type
                ) {
                    this.posterUrl = posterurl
                    description = "Grup: $chGroup, Dil: $language"
                }
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val loadData = parseJson<LoadData>(url) ?: return null

        val type = if (loadData.group.contains("Anime", ignoreCase = true)) TvType.Anime else TvType.Movie

        return if (type == TvType.Anime) {
            newAnimeLoadResponse(
                name = loadData.title,
                url = url,
                type = type
            ) {
                posterUrl = loadData.poster
                addDubStatus(dubExist = loadData.isDubbed, subExist = loadData.isSubbed)
            }
        } else {
            newMovieLoadResponse(
                name = loadData.title,
                url = url,
                type = type
            ) {
                posterUrl = loadData.poster
                description = "Grup: ${loadData.group}, Dil: ${loadData.language}"
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val loadData = fetchDataFromUrlOrJson(data)
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.firstOrNull { it.url == loadData.url } ?: return false

            val watchKey = "watch_${data.hashCode()}"
            sharedPref
