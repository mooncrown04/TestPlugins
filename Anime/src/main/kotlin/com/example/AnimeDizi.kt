package com.mooncrown.anime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseM3u
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.ui.settings.SettingsProvider
import com.lagradost.cloudstream3.ui.settings.PreferenceScreen
import com.lagradost.cloudstream3.ui.settings.PreferenceCategory
import com.lagradost.cloudstream3.ui.settings.TextPreference
import com.lagradost.cloudstream3.ui.settings.ListPreference

class AnimeDizi(val plugin: CloudstreamPlugin) : MainAPI(), SettingsProvider {
    private val DEFAULT_M3U_URL =
        "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    private val DEFAULT_NAME = "35 Anime Diziler 🎬"

    override var mainUrl: String = DEFAULT_M3U_URL
    override var name: String = DEFAULT_NAME
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasMainPage = true
    override var lang = "tr"

    // Kullanıcı ayarlarını tanımlıyoruz
    override fun getPreferencesScreen(): PreferenceScreen {
        return PreferenceScreen {
            PreferenceCategory {
                TextPreference(
                    key = "plugin_name_key",
                    title = "Eklenti Adı",
                    summary = "Eklentinin görünen adını değiştirin.",
                    defaultValue = DEFAULT_NAME,
                )

                TextPreference(
                    key = "m3u_url_key",
                    title = "M3U URL",
                    summary = "Özel bir M3U listesi URL'si girin.",
                    defaultValue = DEFAULT_M3U_URL,
                )

                ListPreference(
                    key = "layout_preference_key",
                    title = "Liste Düzeni",
                    entries = listOf("Yatay", "Dikey"),
                    defaultValue = "Yatay"
                )
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val m3uUrl = plugin.getKey("m3u_url_key", DEFAULT_M3U_URL) ?: DEFAULT_M3U_URL
        val pluginName = plugin.getKey("plugin_name_key", DEFAULT_NAME) ?: DEFAULT_NAME

        val response = app.get(m3uUrl).text
        val channels = parseM3u(response)

        val items = channels.map {
            newTvSeriesSearchResponse(
                name = it.name,
                url = it.url,
                TvType.TvSeries
            ) {
                this.posterUrl = it.logo
            }
        }

        return newHomePageResponse(pluginName, listOf(HomePageList(pluginName, items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val m3uUrl = plugin.getKey("m3u_url_key", DEFAULT_M3U_URL) ?: DEFAULT_M3U_URL
        val response = app.get(m3uUrl).text
        val channels = parseM3u(response)

        return channels.filter { it.name.contains(query, ignoreCase = true) }.map {
            newTvSeriesSearchResponse(
                name = it.name,
                url = it.url,
                TvType.TvSeries
            ) {
                this.posterUrl = it.logo
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        return newTvSeriesLoadResponse(name, url, TvType.TvSeries) {
            addEpisodes(DubStatus.Dubbed, url)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                data,
                referer = "",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }
}
