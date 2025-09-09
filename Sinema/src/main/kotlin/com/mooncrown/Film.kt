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
    override val supportedTypes = setOf(TvType.Movie)

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
                        // Düzeltme: Header bilgisini LoadData'ya ekledik.
                        url = LoadData(streamurl, channelname, posterurl, chGroup, language, nation, isWatched, watchProgress, isDubbed, isSubbed, kanal.headers).toJson(),
                        type = TvType.Movie
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
                // Düzeltme: Header bilgisini LoadData'ya ekledik.
                url = LoadData(streamurl, channelname, posterurl, chGroup, language, nation, isWatched, watchProgress, isDubbed, isSubbed, kanal.headers).toJson(),
                type = TvType.Movie
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
                    // Düzeltme: Header bilgisini LoadData'ya ekledik.
                    LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcLanguage, rcNation, rcIsWatched, rcWatchProgress, isDubbedRc, isSubbedRc, kanal.headers).toJson(),
                    type = TvType.Movie
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
        try {
            val loadData = fetchDataFromUrlOrJson(data)
            Log.d("IPTV", "loadData » $loadData")

            val watchKey = "watch_${data.hashCode()}"
            sharedPref?.edit()?.putBoolean(watchKey, true)?.apply()

            val videoUrl = loadData.url
            val videoType = when {
                videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
                else -> ExtractorLinkType.M3U8
            }

            // Düzeltme: Başlıkları daha sağlam bir şekilde yönetiyoruz.
            val finalHeaders = loadData.headers.toMutableMap()

            // Eğer M3U dosyasında User-Agent tanımlı değilse, varsayılan bir değer ekleyin.
            if (finalHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                finalHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            }
            
            // Eğer M3U dosyasında Referer tanımlı değilse, referrer'ı kullanın.
            if (finalHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {
                finalHeaders["Referer"] = loadData.headers["referrer"] ?: ""
            }

            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = loadData.title,
                    url = videoUrl,
                    headers = finalHeaders,
                    referer = loadData.headers["referrer"] ?: "", // Yönlendiren URL'yi buradan alıyoruz.
                    quality = Qualities.Unknown.value,
                    type = videoType
                )
            )

            return true
        } catch (e: Exception) {
            Log.e("IPTV", "Error in loadLinks: ${e.message}", e)
            return false
        }
    }
    
    // Düzeltme: Headers bilgisini tutmak için LoadData'ya yeni bir parametre ekledik.
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
        val isSubbed: Boolean = false,
        val headers: Map<String, String> = emptyMap()
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

            // Düzeltme: Headers bilgisini burada da alıp LoadData'ya ekliyoruz.
            return LoadData(streamurl, channelname, posterurl, chGroup, language, nation, isWatched, watchProgress, isDubbed, isSubbed, kanal.headers)
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

    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
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
                        val urlHeaders = if (referrer != null) { item.headers + mapOf("referrer" to referrer) } else item.headers

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
        val commaIndex = lastIndexOf(",")
        return if (commaIndex >= 0) {
            substring(commaIndex + 1).trim().let { title ->
                val unquotedTitle = if (title.startsWith("\"") && title.endsWith("\"")) {
                    title.substring(1, title.length - 1)
                } else {
                    title
                }
                unquotedTitle.trim().takeIf { it.isNotEmpty() }?.let { rawTitle ->
                    rawTitle.replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                } ?: unquotedTitle
            }
        } else {
            null
        }
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=([^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()

        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").trim()

        val attributes = mutableMapOf<String, String>()
        var currentKey = ""
        var currentValue = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < attributesString.length) {
            val char = attributesString[i]
            when {
                char == '"' -> inQuotes = !inQuotes
                char == '=' && !inQuotes -> {
                    currentKey = currentValue.toString().trim()
                    currentValue.clear()
                }
                char == ' ' && !inQuotes && currentKey.isNotEmpty() && currentValue.isNotEmpty() -> {
                    val cleanValue = currentValue.toString().trim().removeSurrounding("\"").trim()
                    if (cleanValue.isNotEmpty()) {
                        attributes[currentKey] = cleanValue
                    }
                    currentKey = ""
                    currentValue.clear()
                }
                char == ',' && !inQuotes -> {
                    if (currentKey.isNotEmpty() && currentValue.isNotEmpty()) {
                        val cleanValue = currentValue.toString().trim().removeSurrounding("\"").trim()
                        if (cleanValue.isNotEmpty()) {
                            attributes[currentKey] = cleanValue
                        }
                    }
                    break
                }
                else -> currentValue.append(char)
            }
            i++
        }

        if (currentKey.isNotEmpty() && currentValue.isNotEmpty()) {
            val cleanValue = currentValue.toString().trim().removeSurrounding("\"").trim()
            if (cleanValue.isNotEmpty()) {
                attributes[currentKey] = cleanValue
            }
        }

        return attributes
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
