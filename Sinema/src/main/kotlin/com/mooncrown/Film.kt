package com.mooncrown

import com.mooncrown.BuildConfig
import android.util.Log
import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus

class Film(private val context: android.content.Context, private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "35 Anime 📺"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return newHomePageResponse(
            kanallar.items.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show = group.value.map { kanal ->
                    val streamurl = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl = kanal.attributes["tvg-logo"].toString()
                    val chGroup = kanal.attributes["group-title"].toString()
                    val language = kanal.attributes["tvg-language"].toString()
                    val nation = kanal.attributes["tvg-country"].toString()

                    val watchKey = "watch_${streamurl.hashCode()}"
                    val progressKey = "progress_${streamurl.hashCode()}"
                    val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
                    val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

                    // Dil etiketine göre dublaj kontrolü yapıyoruz.
                    val isDubbed = language.lowercase() == "turkish"
                    val isSubbed = chGroup.contains("Altyazılı", ignoreCase = true) || channelname.contains("Altyazı", ignoreCase = true)

                    val newTitle = when {
                        isDubbed -> "$channelname (Türkçe Dublaj)"
                        isSubbed -> "$channelname (Altyazılı)"
                        else -> channelname
                    }

                    newAnimeSearchResponse(
                        name = newTitle,
                        url = LoadData(streamurl, channelname, posterurl, chGroup, language, nation, isWatched, watchProgress, isDubbed, isSubbed).toJson(),
                        type = TvType.Anime
                    ) {
                        this.posterUrl = posterurl
                        this.addDubStatus(dubExist = isDubbed, subExist = isSubbed)
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
            val normalizedTitle = it.title.toString().lowercase()
            val normalizedLanguage = it.attributes["tvg-language"]?.lowercase() ?: ""
            
            // Arama sorgusu hem başlıkta hem de tvg-language etiketinde aranır
            normalizedTitle.contains(normalizedQuery) || normalizedLanguage.contains(normalizedQuery)
        }.map { kanal ->
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString()
            val chGroup = kanal.attributes["group-title"].toString()
            val language = kanal.attributes["tvg-language"].toString()
            val nation = kanal.attributes["tvg-country"].toString()

            val watchKey = "watch_${streamurl.hashCode()}"
            val progressKey = "progress_${streamurl.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            val isDubbed = language.lowercase() == "turkish"
            val isSubbed = chGroup.contains("Altyazılı", ignoreCase = true) || channelname.contains("Altyazı", ignoreCase = true)

            val newTitle = when {
                isDubbed -> "$channelname (Türkçe Dublaj)"
                isSubbed -> "$channelname (Altyazılı)"
                else -> channelname
            }

            newAnimeSearchResponse(
                name = newTitle,
                url = LoadData(streamurl, channelname, posterurl, chGroup, language, nation, isWatched, watchProgress, isDubbed, isSubbed).toJson(),
                type = TvType.Anime
            ) {
                this.posterUrl = posterurl
                this.addDubStatus(dubExist = isDubbed, subExist = isSubbed)
            }

        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun fetchTMDBData(title: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.TMDB_SECRET_API.trim('"')
                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "API key is empty")
                    return@withContext null
                }

                val encodedTitle = URLEncoder.encode(title.replace(Regex("\\([^)]*\\)"), "").trim(), "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$encodedTitle&language=tr-TR"

                val response = withContext(Dispatchers.IO) {
                    URL(searchUrl).readText()
                }
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")

                if (results.length() > 0) {
                    val movieId = results.getJSONObject(0).getInt("id")
                    val detailsUrl = "https://api.themoviedb.org/3/movie/$movieId?api_key=$apiKey&append_to_response=credits&language=tr-TR"
                    val detailsResponse = withContext(Dispatchers.IO) {
                        URL(detailsUrl).readText()
                    }
                    return@withContext JSONObject(detailsResponse)
                }
                null
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching TMDB data: ${e.message}")
                null
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L
        val loadData = fetchDataFromUrlOrJson(url)

        val nation:String = if (loadData.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${loadData.group} | ${loadData.nation} «"
        }

        val tmdbData = fetchTMDBData(loadData.title)

        val plot = buildString {
            if (loadData.isDubbed) append("🔊 <b>Ses:</b> Türkçe Dublaj<br>")
            if (loadData.isSubbed) append("📖 <b>Altyazı:</b> Var<br>")
            if (tmdbData != null) {
                val overview = tmdbData.optString("overview", "")
                val releaseDate = tmdbData.optString("release_date", "").split("-").firstOrNull() ?: ""
                val ratingValue = tmdbData.optDouble("vote_average", -1.0)
                val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                val tagline = tmdbData.optString("tagline", "")
                val budget = tmdbData.optLong("budget", 0L)
                val revenue = tmdbData.optLong("revenue", 0L)
                val originalName = tmdbData.optString("original_name", "")
                val originalLanguage = tmdbData.optString("original_language", "")

                val genresArray = tmdbData.optJSONArray("genres")
                val genreList = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                    }
                }

                val creditsObject = tmdbData.optJSONObject("credits")
                val castList = mutableListOf<String>()
                var director = ""
                if (creditsObject != null) {
                    val castArray = creditsObject.optJSONArray("cast")
                    if (castArray != null) {
                        for (i in 0 until minOf(castArray.length(), 10)) {
                            castList.add(castArray.optJSONObject(i)?.optString("name") ?: "")
                        }
                    }
                    val crewArray = creditsObject.optJSONArray("crew")
                    if (crewArray != null) {
                        for (i in 0 until crewArray.length()) {
                            val member = crewArray.optJSONObject(i)
                            if (member?.optString("job") == "Director") {
                                director = member.optString("name", "")
                                break
                            }
                        }
                    }
                }

                val companiesArray = tmdbData.optJSONArray("production_companies")
                val companyList = mutableListOf<String>()
                if (companiesArray != null) {
                    for (i in 0 until companiesArray.length()) {
                        companyList.add(companiesArray.optJSONObject(i)?.optString("name") ?: "")
                    }
                }

                val numberFormat = try {
                    java.text.NumberFormat.getNumberInstance(java.util.Locale("tr", "TR"))
                } catch (e: Exception) {
                    Log.e("LocaleError", "TR Locale alınamadı, US kullanılıyor.", e)
                    java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
                }

                if (tagline.isNotEmpty()) append("💭 <b>Slogan:</b><br>${tagline}<br><br>")
                if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                if (releaseDate.isNotEmpty()) append("📅 <b>Yapım Yılı:</b> $releaseDate<br>")
                if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                if (originalLanguage.isNotEmpty()) {
                    val langCode = originalLanguage.lowercase()
                    val turkishName = languageMap[langCode] ?: originalLanguage
                    append("🌐 <b>Orijinal Dil:</b> $turkishName<br>")
                }
                if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>")
                if (director.isNotEmpty()) append("🎬 <b>Yönetmen:</b> $director<br>")
                if (genreList.isNotEmpty()) append("🎭 <b>Film Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                if (castList.isNotEmpty()) append("👥 <b>Oyuncular:</b> ${castList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                if (companyList.isNotEmpty()) append("🏢 <b>Yapım Şirketleri:</b> ${companyList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                if (budget > 0) {
                    try {
                        val formattedBudget = numberFormat.format(budget)
                        append("💰 <b>Bütçe:</b> $${formattedBudget}<br>")
                        Log.d("FormatDebug", "Bütçe formatlandı (TR): $formattedBudget")
                    } catch (e: Exception) {
                        Log.e("FormatError", "Bütçe formatlanırken hata (TR): $budget", e)
                        append("💰 <b>Bütçe:</b> $${budget} (Formatlama Hatası)<br>")
                    }
                }
                if (revenue > 0) {
                    try {
                        val formattedRevenue = numberFormat.format(revenue)
                        append("💵 <b>Hasılat:</b> $${formattedRevenue}<br>")
                        Log.d("FormatError", "Hasılat formatlanırken hata (TR): $revenue")
                    } catch (e: Exception) {
                        Log.e("FormatError", "Hasılat formatlanırken hata (TR): $revenue", e)
                        append("💵 <b>Hasılat:</b> $${revenue} (Formatlama Hatası)<br>")
                    }
                }
                append("<br>")
            } else {
                append("<i>Film detayları alınamadı.</i><br><br>")
            }
        }
        val displayTitle = when {
            loadData.isDubbed -> "${loadData.title} (Türkçe Dublaj)"
            loadData.isSubbed -> "${loadData.title} (Altyazılı)"
            else -> loadData.title
        }


        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val recommendations = mutableListOf<LiveSearchResponse>()

        for (kanal in kanallar.items) {
            if (kanal.attributes["group-title"].toString() == loadData.group) {
                val rcStreamUrl = kanal.url.toString()
                val rcChannelName = kanal.title.toString()
                if (rcChannelName == loadData.title) continue

                val rcPosterUrl = kanal.attributes["tvg-logo"].toString()
                val rcChGroup = kanal.attributes["group-title"].toString()
                val rcLanguage = kanal.attributes["tvg-language"].toString()
                val rcNation = kanal.attributes["tvg-country"].toString()
                val isDubbedRc = rcLanguage.lowercase() == "turkish"
                val isSubbedRc = rcChGroup.contains("Altyazılı", ignoreCase = true) || rcChannelName.contains("Altyazı", ignoreCase = true)
                val rcTitle = when {
                    isDubbedRc -> "$rcChannelName (Türkçe Dublaj)"
                    isSubbedRc -> "$rcChannelName (Altyazılı)"
                    else -> rcChannelName
                }

                val rcWatchKey = "watch_${rcStreamUrl.hashCode()}"
                val rcProgressKey = "progress_${rcStreamUrl.hashCode()}"
                val rcIsWatched = sharedPref?.getBoolean(rcWatchKey, false) ?: false
                val rcWatchProgress = sharedPref?.getLong(rcProgressKey, 0L) ?: 0L

                recommendations.add(newLiveSearchResponse(
                    rcTitle,
                    LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcLanguage, rcNation, rcIsWatched, rcWatchProgress, isDubbedRc, isSubbedRc).toJson(),
                    type = TvType.Anime
                ) {
                    posterUrl = rcPosterUrl
                })
            }
        }

        return newAnimeLoadResponse(displayTitle, url, TvType.Anime, false) {
            this.posterUrl = loadData.poster
            this.plot = plot
            this.recommendations = recommendations
            this.rating = (tmdbData?.optDouble("vote_average", 0.0)?.toFloat()?.times(2)?.toInt() ?: (if (isWatched) 5 else 0))
            this.duration = if (watchProgress > 0) (watchProgress / 1000).toInt() else tmdbData?.optInt("runtime", 0)
            this.comingSoon = false
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("IPTV", "loadLinks çağrıldı, veri: $data")
        try {
            val loadData = fetchDataFromUrlOrJson(data)
            Log.d("IPTV", "loadData oluşturuldu: $loadData")
    
            if (loadData.url.isNullOrEmpty()) {
                Log.e("IPTV", "loadData URL'si boş veya null, bağlantı sağlanamıyor.")
                return false
            }
            Log.d("IPTV", "Video URL'si bulundu: ${loadData.url}")
            
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.firstOrNull { it.url == loadData.url }
            if (kanal == null) {
                Log.e("IPTV", "Kanal M3U listesinde bulunamadı, bağlantı sağlanamıyor.")
                return false
            }

            val watchKey = "watch_${data.hashCode()}"
            val progressKey = "progress_${data.hashCode()}"
            sharedPref?.edit()?.putBoolean(watchKey, true)?.apply()

            val videoUrl = loadData.url
            val videoType = when {

                videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
                else -> ExtractorLinkType.M3U8

            }

            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = loadData.title,
                    url = videoUrl,
                    headers = kanal.headers + mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    ),
                    referer = kanal.headers["referrer"] ?: "",
                    quality = Qualities.Unknown.value,
                    type = videoType
                )
            )

            return true
        } catch (e: Exception) {
            Log.e("IPTV", "loadLinks'te hata oluştu: ${e.message}", e)
            return false
        }
    }

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val language: String,
        val nation: String,
        val isWatched: Boolean = false,
        val watchProgress: Long = 0L,
        val isDubbed: Boolean = false,
        val isSubbed: Boolean = false
    )

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.first { it.url == data }

            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString()
            val chGroup = kanal.attributes["group-title"].toString()
            val language = kanal.attributes["tvg-language"].toString()
            val nation = kanal.attributes["tvg-country"].toString()
            val watchKey = "watch_${data.hashCode()}"
            val progressKey = "progress_${data.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            val isDubbed = language.lowercase() == "turkish"
            val isSubbed = chGroup.contains("Altyazılı", ignoreCase = true) || channelname.contains("Altyazı", ignoreCase = true)

            return LoadData(streamurl, channelname, posterurl, chGroup, language, nation, isWatched, watchProgress, isDubbed, isSubbed)
        }
    }
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

class IptvPlaylistParser {

    fun parseM3U(content: String): Playlist {
        val lines = content.lines()
        val playlistItems = mutableListOf<PlaylistItem>()
        var lastAttributes: Map<String, String> = emptyMap()
        var lastTitle: String? = null

        for (line in lines) {
            if (line.startsWith("#EXTINF")) {
                val parts = line.split(",")
                if (parts.size > 1) {
                    val attributesString = parts[0]
                        .removePrefix("#EXTINF:")
                        .replace(Regex("\\s*-1\\s*,"), "") // -1, kaldırıldı
                        .trim()

                    lastTitle = parts[1].trim()
                    lastAttributes = parseAttributes(attributesString)

                }
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                val url = line.trim()
                if (lastTitle != null && lastAttributes.isNotEmpty() && url.isNotEmpty()) {
                    playlistItems.add(
                        PlaylistItem(
                            title = lastTitle,
                            attributes = lastAttributes,
                            url = url
                        )
                    )
                    lastTitle = null
                    lastAttributes = emptyMap()
                }
            }
        }
        return Playlist(playlistItems)
    }

    private fun parseAttributes(attributesString: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")
        regex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value
        }
        return attributes
    }
}


sealed class PlaylistParserException(message: String) : Exception(message) {

    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}

val languageMap = mapOf(
    "en" to "İngilizce",
    "tr" to "Türkçe",
    "ja" to "Japonca",
    "de" to "Almanca",
    "fr" to "Fransızca",
    "es" to "İspanyolca",
    "it" to "İtalyanca",
    "ru" to "Rusça",
    "pt" to "Portekizce",
    "ko" to "Korece",
    "zh" to "Çince",
    "hi" to "Hintçe",
    "ar" to "Arapça",
    "nl" to "Felemenkçe",
    "sv" to "İsveççe",
    "no" to "Norveççe",
    "da" to "Danca",
    "fi" to "Fince",
    "pl" to "Lehçe",
    "cs" to "Çekçe",
    "hu" to "Macarca",
    "ro" to "Rumence",
    "el" to "Yunanca",
    "uk" to "Ukraynaca",
    "bg" to "Bulgarca",
    "sr" to "Sırpça",
    "hr" to "Hırvatça",
    "sk" to "Slovakça",
    "sl" to "Slovence",
    "th" to "Tayca",
    "vi" to "Vietnamca",
    "id" to "Endonezce",
    "ms" to "Malayca",
    "tl" to "Tagalogca",
    "fa" to "Farsça",
    "he" to "İbranice",
    "la" to "Latince",
    "xx" to "Belirsiz",
    "mul" to "Çok Dilli"
)

fun getTurkishLanguageName(code: String?): String? {
    return languageMap[code?.lowercase()]
}
