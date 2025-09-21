package com.anhdaden

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Interceptor

class IPTVProvider(mainUrl: String, name: String) : MainAPI() {
    override var mainUrl = mainUrl
    override var name = name
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Live
    )

    val items = mutableMapOf<String, Playlist?>()
    val headers = mapOf("User-Agent" to "Player (Linux; Android 14)")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        items[name] = IptvPlaylistParser().parseM3U(app.get(mainUrl, headers = headers).text)

        return newHomePageResponse(
            items[name]!!.items.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show = group.value.map { item ->
                    val streamurl = item.url.toString()
                    val channelname = item.title.toString()
                    val posterurl = item.attributes["tvg-logo"].toString()
                    val chGroup = item.attributes["group-title"].toString()
                    val key = item.attributes["key"].toString()
                    val keyid = item.attributes["keyid"].toString()

                    LiveSearchResponse(
                        name = channelname,
                        url = LoadData(streamurl, channelname, posterurl, chGroup, key, keyid).toJson(),
                        apiName = name,
                        type = TvType.Live,
                        posterUrl = posterurl,
                    )
                }

                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (items[name] == null) {
            items[name] = IptvPlaylistParser().parseM3U(app.get(mainUrl, headers = headers).text)
        }

        return items[name]!!.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { item ->
            val streamurl = item.url.toString()
            val channelname = item.title.toString()
            val posterurl = item.attributes["tvg-logo"].toString()
            val chGroup = item.attributes["group-title"].toString()
            val key = item.attributes["key"].toString()
            val keyid = item.attributes["keyid"].toString()

            LiveSearchResponse(
                name = channelname,
                url = LoadData(streamurl, channelname, posterurl, chGroup, key, keyid).toJson(),
                apiName = name,
                type = TvType.Live,
                posterUrl = posterurl,
            )
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        if (items[name] == null) {
            items[name] = IptvPlaylistParser().parseM3U(app.get(mainUrl, headers = headers).text)
        }
        val recommendations = mutableListOf<LiveSearchResponse>()
        for (item in items[name]!!.items) {
            if (recommendations.size >= 24) break
            if (item.attributes["group-title"].toString() == loadData.group) {
                val rcStreamUrl = item.url.toString()
                val rcChannelName = item.title.toString()
                if (rcChannelName == loadData.title) continue

                val rcPosterUrl = item.attributes["tvg-logo"].toString()
                val rcChGroup = item.attributes["group-title"].toString()
                val key = item.attributes["key"].toString()
                val keyid = item.attributes["keyid"].toString()

                recommendations.add(LiveSearchResponse(
                    name = rcChannelName,
                    url = LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, key, keyid).toJson(),
                    apiName = name,
                    type = TvType.Live,
                    posterUrl = rcPosterUrl,
                ))
            }
        }

        return LiveStreamLoadResponse(
            name = loadData.title,
            url = url,
            apiName = this.name,
            dataUrl = url,
            posterUrl = loadData.poster,
            recommendations = recommendations
        )
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = parseJson<LoadData>(data)
        val item = items[name]!!.items.first { it.url == loadData.url }
        val response = checkLinkType(loadData.url, item.headers)
        val isM3u8 = if (response == "m3u8") true else false
        if (loadData.url.contains(".mpd")) {
            callback.invoke(
                DrmExtractorLink(
                    name = this.name,
                    source = loadData.title,
                    url = loadData.url,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    type = INFER_TYPE,
                    kid = loadData.keyid.toString().trim(),
                    key = loadData.key.toString().trim(),
                )
            )
        } else {
            callback.invoke(
                ExtractorLink(
                    name = this.name,
                    source = loadData.title,
                    url = loadData.url,
                    headers = item.headers,
                    referer = item.headers["referrer"] ?: "",
                    quality = Qualities.Unknown.value,
                    isM3u8 = isM3u8,
                )
            )
        }
        return true
    }

    @Suppress("ObjectLiteralToLambda")
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()

                return chain.proceed(request)
            }
        }
    }
}
