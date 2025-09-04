package com.example

import com.example.BuildConfig
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
   



// İki farklı formatı işleyebilen yardımcı fonksiyon
// Erişim belirleyici private'dan public'e değiştirildi
fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    // Birinci format için regex: "Dizi Adı-Sezon. Sezon Bölüm. Bölüm(Ek Bilgi)"
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")

    // İkinci format için regex: "Dizi Adı sXXeYY"
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""")

    // Üçüncü ve en önemli format için regex: "Dizi Adı Sezon X Bölüm Y"
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""")

    // Formatları sırayla deniyoruz
    val matchResult1 = format1Regex.find(text)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    val matchResult2 = format2Regex.find(text)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    val matchResult3 = format3Regex.find(text)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    // Hiçbir format eşleşmezse, orijinal başlığı ve null değerleri döndür.
    return Triple(text.trim(), null, null)
}

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
  //  override var mainUrl = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "35 MoOn Dizi 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        // Dizi listesi için tüm öğeleri işleyin
        val processedItems = kanallar.items.map { item ->
            val (cleanTitle, season, episode) = parseEpisodeInfo(item.title.toString())
            item.copy(
                title = cleanTitle,
                season = season ?: 1,
                episode = episode ?: 0,
                attributes = item.attributes.toMutableMap().apply {
                    if (!containsKey("tvg-country")) { put("tvg-country", "TR/Altyazılı") }
                    if (!containsKey("tvg-language")) { put("tvg-language", "TR;EN") }
                }
            )
        }

        val alphabeticGroups = processedItems.groupBy { item ->
            val firstChar = item.title.toString().firstOrNull()?.uppercaseChar() ?: '#'
            when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#"
            }
        }.toSortedMap()

        val homePageLists = mutableListOf<HomePageList>()

        alphabeticGroups.forEach { (letter, shows) ->
            val searchResponses = shows.distinctBy { it.title }.map { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val nation = kanal.attributes["tvg-country"].toString()

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, letter, nation, kanal.season, kanal.episode).toJson(),
                    type = TvType.TvSeries
                ) {
                    this.posterUrl =LoadData.posterurl   //LoadData
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

        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString()
            val chGroup = kanal.attributes["group-title"].toString()
            val nation = kanal.attributes["tvg-country"].toString()
            
            val (cleanTitle, season, episode) = parseEpisodeInfo(channelname)

            newLiveSearchResponse(
                cleanTitle,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, season ?: 1, episode ?: 0).toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl ==LoadData.posterurl    //=LoadData
                this.lang = nation
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun fetchTMDBData(title: String, season: Int, episode: Int): Pair<JSONObject?, JSONObject?> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.TMDB_SECRET_API.trim('"')
                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "API key is empty")
                    return@withContext Pair(null, null)
                }

                // Dizi adını temizle ve hazırla
                val cleanedTitle = title
                    .replace(Regex("\\([^)]*\\)"), "") // Parantez içindeki metinleri kaldır
                    .trim()
                
                Log.d("TMDB", "Searching for TV show: $cleanedTitle")
                val encodedTitle = URLEncoder.encode(cleanedTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"

                val response = withContext(Dispatchers.IO) {
                    URL(searchUrl).readText()
                }
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")
                
                Log.d("TMDB", "Search results count: ${results.length()}")
                
                if (results.length() > 0) {
                    // İlk sonucu al
                    val tvId = results.getJSONObject(0).getInt("id")
                    val foundTitle = results.getJSONObject(0).optString("name", "")
                    Log.d("TMDB", "Found TV show: $foundTitle with ID: $tvId")
                    
                    // Dizi detaylarını getir
                    val seriesUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits,images&language=tr-TR"
                    val seriesResponse = withContext(Dispatchers.IO) {
                        URL(seriesUrl).readText()
                    }
                    val seriesData = JSONObject(seriesResponse)
                    
                    // Bölüm detaylarını getir
                    try {
                        val episodeUrl = "https://api.themoviedb.org/3/tv/$tvId/season/$season/episode/$episode?api_key=$apiKey&append_to_response=credits,images&language=tr-TR"
                        val episodeResponse = withContext(Dispatchers.IO) {
                            URL(episodeUrl).readText()
                        }
                        val episodeData = JSONObject(episodeResponse)
                        
                        return@withContext Pair(seriesData, episodeData)
                    } catch (e: Exception) {
                        Log.e("TMDB", "Error fetching episode data: ${e.message}")
                        // Bölüm bilgisi alınamazsa sadece dizi bilgisini döndür
                        return@withContext Pair(seriesData, null)
                    }
                } else {
                    Log.d("TMDB", "No results found for: $cleanedTitle")
                }
                Pair(null, null)
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching TMDB data: ${e.message}")
                Pair(null, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L
        val loadData = fetchDataFromUrlOrJson(url)
        
        // Dizi adını temizle - hem "Dizi-1.Sezon" hem de "Dizi 1. Sezon" formatlarını destekler
        val (cleanTitle, loadDataSeason, loadDataEpisode) = parseEpisodeInfo(loadData.title)
        val (seriesData, episodeData) = fetchTMDBData(cleanTitle, loadData.season, loadData.episode)
        
        val plot = buildString {
            if (seriesData != null) {
                append("<b>📺<u> Dizi Bilgileri</u> (Genel)</b><br><br>")
                
                val overview = seriesData.optString("overview", "")
                val firstAirDate = seriesData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                val ratingValue = seriesData.optDouble("vote_average", -1.0)
                val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                val tagline = seriesData.optString("tagline", "")
                val originalName = seriesData.optString("original_name", "")
                val originalLanguage = seriesData.optString("original_language", "")
                val numberOfSeasons = seriesData.optInt("number_of_seasons", 1)
                val numberOfEpisodes = seriesData.optInt("number_of_episodes", 1)

                val genresArray = seriesData.optJSONArray("genres")
                val genreList = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                    }
                }
                
                if (tagline.isNotEmpty()) append("💭 <b>Dizi Sloganı:</b><br><i>${tagline}</i><br><br>")
                if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>")
                if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                if (originalLanguage.isNotEmpty()) {
                    val langCode = originalLanguage.lowercase()
                    val turkishName = languageMap[langCode] ?: originalLanguage
                    append("🌐 <b>Orijinal Dil:</b> $turkishName<br>")
                }
                if (numberOfSeasons > 0 && numberOfEpisodes > 0)
                    append("📅 <b>Toplam Sezon:</b> $numberOfSeasons ($numberOfEpisodes bölüm)<br>")

                if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                
                // Dizi oyuncuları fotoğraflarıyla
                val creditsObject = seriesData.optJSONObject("credits")
                if (creditsObject != null) {
                    val castArray = creditsObject.optJSONArray("cast")
                    if (castArray != null && castArray.length() > 0) {
                        val castList = mutableListOf<String>()
                        for (i in 0 until minOf(castArray.length(), 25)) {
                            val actor = castArray.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            if (actorName.isNotEmpty()) {
                                castList.add(if (character.isNotEmpty()) "$actorName (${character})" else actorName)
                            }
                        }
                        if (castList.isNotEmpty()) {
                            append("👥 <b>Tüm Oyuncular:</b> ${castList.joinToString(", ")}<br>")
                        }
                    }
                }
                
            }
            
            // Bölüm bilgileri
            if (episodeData != null) {
                append("<hr><br>")
                append("<b>🎬<u> Bölüm Bilgileri</u></b><br><br>")
                
                val episodeTitle = episodeData.optString("name", "")
                val episodeOverview = episodeData.optString("overview", "")
                val episodeAirDate = episodeData.optString("air_date", "").split("-").firstOrNull() ?: ""
                val episodeRating = episodeData.optDouble("vote_average", -1.0)
                
                if (episodeTitle.isNotEmpty()) append("📽️ <b>Bölüm Adı:</b> ${episodeTitle}<br>")
                if (episodeOverview.isNotEmpty()) append("✍🏻 <b>Bölüm Konusu:</b><br><i>${episodeOverview}</i><br><br>")
                if (episodeAirDate.isNotEmpty()) append("📅 <b>Yayın Tarihi:</b> $episodeAirDate<br>")
                if (episodeRating >= 0) append("⭐ <b>Bölüm Puanı:</b> ${String.format("%.1f", episodeRating)} / 10<br>")
                
                // Bölüm oyuncuları
                val episodeCredits = episodeData.optJSONObject("credits")
                if (episodeCredits != null) {
                    val episodeCast = episodeCredits.optJSONArray("cast")
                    if (episodeCast != null && episodeCast.length() > 0) {
                        append("<br>👥 <b>Bu Bölümdeki Oyuncular:</b><br>")
                        append("<div style='display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:5px 0'>")
                        for (i in 0 until minOf(episodeCast.length(), 25)) {
                            val actor = episodeCast.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            val gender = actor?.optInt("gender", 0) ?: 0
                            
                            if (actorName.isNotEmpty()) {
                                val genderIcon = when (gender) {
                                    1 -> "👱🏼‍♀" // Kadın
                                    2 -> "👱🏻" // Erkek
                                    else -> "👤" // Belirsiz
                                }
                                append("<div style='background:#f0f0f0;padding:5px 10px;border-radius:5px'>")
                                append("$genderIcon <b>$actorName</b>")
                                if (character.isNotEmpty()) append(" ($character rolünde)")
                                append("</div>")
                            }
                        }
                        append("</div><br>")
                    }
                }
                
            }
            
            // Eğer hiçbir TMDB verisi yoksa, en azından temel bilgileri göster
            if (seriesData == null && episodeData == null) {
                append("<b>📺 DİZİ BİLGİLERİ</b><br><br>")
                append("📝 <b>TMDB'den bilgi alınamadı.</b><br><br>")
            }
            
            val nation = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) {
                "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
            } else {
                "» ${loadData.group} | ${loadData.nation} «"
            }
            append(nation)
        }

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        
        val allShows = kanallar.items.groupBy { item ->
            val (itemCleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
            itemCleanTitle
        }

        val currentShowEpisodes = allShows[cleanTitle]?.mapNotNull { kanal ->
            val title = kanal.title.toString()
            val (episodeCleanTitle, season, episode) = parseEpisodeInfo(title)
            
            if (season != null && episode != null) {
                newEpisode(LoadData(kanal.url.toString(), title, kanal.attributes["tvg-logo"].toString(), kanal.attributes["group-title"].toString(), kanal.attributes["tvg-country"]?.toString() ?: "TR", season, episode).toJson()) {
                    this.name = episodeCleanTitle
                    this.season = season
                    this.episode = episode
                    this.posterUrl = kanal.attributes["tvg-logo"].toString()
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
            val tmdbPosterPath = seriesData?.optString("poster_path", "")
            val tmdbPosterUrl = if (tmdbPosterPath?.isNotEmpty() == true) {
                "https://image.tmdb.org/t/p/w500$tmdbPosterPath"
            } else {
                null
            }
            
            this.posterUrl = tmdbPosterUrl ?: loadData.poster
           
           this.plot = plot
            this.tags = listOf(loadData.group, loadData.nation)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        Log.d("IPTV", "loadData » $loadData")

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val kanal = kanallar.items.firstOrNull { it.url == loadData.url } ?: return false
        Log.d("IPTV", "kanal » $kanal")

        val videoUrl = loadData.url
        val videoType = when {
            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> ExtractorLinkType.M3U8
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${loadData.title} (S${loadData.season}:E${loadData.episode})",
                url = videoUrl,
                type = videoType
            ) {
                headers = kanal.headers
                referer = kanal.headers["referrer"] ?: ""
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
        val episode: Int = 0,
        val isWatched: Boolean = false,
        val watchProgress: Long = 0
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
            val nation = kanal.attributes["tvg-country"].toString()

            val (cleanTitle, season, episode) = parseEpisodeInfo(channelname)

            return LoadData(streamurl, cleanTitle, posterurl, chGroup, nation, season ?: 1, episode ?: 0)
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
    val userAgent: String? = null,
    val season: Int = 1,
    val episode: Int = 0
) {
    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

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

        val EXT_M3U = PlaylistItem.EXT_M3U
        val EXT_INF = PlaylistItem.EXT_INF
        val EXT_VLC_OPT = PlaylistItem.EXT_VLC_OPT

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
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")?.toString()
                    val referrer = line.getTagValue("http-referrer")?.toString()

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
                        val urlHeaders = if (referrer != null) {item.headers + mapOf("referrer" to referrer)} else item.headers

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

    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)

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

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim()
        val titleAndAttributes = attributesString.split(",", limit = 2)
        
        val attributes = mutableMapOf<String, String>()
        if (titleAndAttributes.size > 1) {
            val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"|([\\w-]+)=([^\"]+)")
            
            attrRegex.findAll(titleAndAttributes[0]).forEach { matchResult ->
                val (quotedKey, quotedValue, unquotedKey, unquotedValue) = matchResult.destructured
                val key = quotedKey.takeIf { it.isNotEmpty() } ?: unquotedKey
                val value = quotedValue.takeIf { it.isNotEmpty() } ?: unquotedValue
                attributes[key] = value.replaceQuotesAndTrim()
            }
        }

        if (!attributes.containsKey("tvg-country")) {
            attributes["tvg-country"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("tvg-language")) {
            attributes["tvg-language"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("group-title")) {
            val (cleanTitle, _, _) = parseEpisodeInfo(titleAndAttributes.last())
            attributes["group-title"] = cleanTitle
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



// İki farklı formatı işleyebilen yardımcı fonksiyon
// Erişim belirleyici private'dan public'e değiştirildi
private fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    // Birinci format için regex: "Dizi Adı-Sezon. Sezon Bölüm. Bölüm(Ek Bilgi)"
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")

    // İkinci format için regex: "Dizi Adı sXXeYY"
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""")

    // Üçüncü ve en önemli format için regex: "Dizi Adı Sezon X Bölüm Y"
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""")

    // Formatları sırayla deniyoruz
    val matchResult1 = format1Regex.find(text)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    val matchResult2 = format2Regex.find(text)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    val matchResult3 = format3Regex.find(text)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    // Hiçbir format eşleşmezse, orijinal başlığı ve null değerleri döndür.
    return Triple(text.trim(), null, null)
}



class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
  //  override var mainUrl = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "350444 MoOn Dizi 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)


        // Dizi listesi için tüm öğeleri işleyin
        val processedItems = kanallar.items.map { item ->
            val (cleanTitle, season, episode) = parseEpisodeInfo(item.title.toString())
            item.copy(
                title = cleanTitle,
                season = season ?: 1,
                episode = episode ?: 0,
                attributes = item.attributes.toMutableMap().apply {
                    if (!containsKey("tvg-country")) { put("tvg-country", "TR/Altyazılı") }
                    if (!containsKey("tvg-language")) { put("tvg-language", "TR;EN") }
                }
            )
        }

        val alphabeticGroups = processedItems.groupBy { item ->
            val firstChar = item.title.toString().firstOrNull()?.uppercaseChar() ?: '#'
            when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#"
            }
        }.toSortedMap()

        val homePageLists = mutableListOf<HomePageList>()

        alphabeticGroups.forEach { (letter, shows) ->
            val searchResponses = shows.distinctBy { it.title }.map { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val nation = kanal.attributes["tvg-country"].toString()



 // *** BURAYA LOG EKLEYİN ***
Log.e("POWERDIZI_DEBUG", "getMainPage - Dizi: $channelname, M3U Poster URL: $posterurl")

 val loadData = LoadData(streamurl, channelname, posterurl, letter, nation, kanal.season, kanal.episode)
        val jsonData = loadData.toJson()

                
                newLiveSearchResponse(
                    channelname,
                    jsonData, // Artık bu json'u kullanıyoruz
                 //  LoadData(streamurl, channelname, posterurl, letter, nation, kanal.season, kanal.episode).toJson(),
                    type = TvType.TvSeries
                ) {
    // İçerideki blokları temizle
    // this.posterUrl = posterurl
    // this.lang = nation
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

        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString().trim()
            val chGroup = kanal.attributes["group-title"].toString()
            val nation = kanal.attributes["tvg-country"].toString()
            
            val (cleanTitle, season, episode) = parseEpisodeInfo(channelname)



// Önce LoadData nesnesini oluşturun
val loadData = LoadData(streamurl, channelname, posterurl, letter, nation, kanal.season, kanal.episode)

// Yeni nesneyi JSON'a dönüştürürken poster'ı da ekleyin
val jsonData = mapOf(
    "url" to streamurl,
    "title" to channelname,
    "poster" to posterurl, // Poster URL'sini açıkça ekleyin
    "group" to letter,
    "nation" to nation,
    "season" to kanal.season,
    "episode" to kanal.episode
).toJson()

// newLiveSearchResponse'ta jsonData'yı kullanın
newLiveSearchResponse(
   cleanTitle,
//    LoadData(streamurl, channelname,posterurl, chGroup, nation, season ?: 1, episode ?: 0).toJson(), 
   jsonData, // Artık bu json'u kullanıyoruz
    type = TvType.TvSeries
) {
    this.posterUrl = posterurl // Burayı koruyun
    this.lang = nation
}

            
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun fetchTMDBData(title: String, season: Int, episode: Int): Pair<JSONObject?, JSONObject?> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.TMDB_SECRET_API.trim('"')
                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "API key is empty")
                    return@withContext Pair(null, null)
                }

                // Dizi adını temizle ve hazırla
                val cleanedTitle = title
                    .replace(Regex("\\([^)]*\\)"), "") // Parantez içindeki metinleri kaldır
                    .trim()
                
                Log.d("TMDB", "Searching for TV show: $cleanedTitle")
                val encodedTitle = URLEncoder.encode(cleanedTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"

                val response = withContext(Dispatchers.IO) {
                    URL(searchUrl).readText()
                }
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")
                
                Log.d("TMDB", "Search results count: ${results.length()}")
                
                if (results.length() > 0) {
                    // İlk sonucu al
                    val tvId = results.getJSONObject(0).getInt("id")
                    val foundTitle = results.getJSONObject(0).optString("name", "")
                    Log.d("TMDB", "Found TV show: $foundTitle with ID: $tvId")
                    
                    // Dizi detaylarını getir
                    val seriesUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits,images&language=tr-TR"
                    val seriesResponse = withContext(Dispatchers.IO) {
                        URL(seriesUrl).readText()
                    }
                    val seriesData = JSONObject(seriesResponse)
                    
                    // Bölüm detaylarını getir
                    try {
                        val episodeUrl = "https://api.themoviedb.org/3/tv/$tvId/season/$season/episode/$episode?api_key=$apiKey&append_to_response=credits,images&language=tr-TR"
                        val episodeResponse = withContext(Dispatchers.IO) {
                            URL(episodeUrl).readText()
                        }
                        val episodeData = JSONObject(episodeResponse)
                        
                        return@withContext Pair(seriesData, episodeData)
                    } catch (e: Exception) {
                        Log.e("TMDB", "Error fetching episode data: ${e.message}")
                        // Bölüm bilgisi alınamazsa sadece dizi bilgisini döndür
                        return@withContext Pair(seriesData, null)
                    }
                } else {
                    Log.d("TMDB", "No results found for: $cleanedTitle")
                }
                Pair(null, null)
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching TMDB data: ${e.message}")
                Pair(null, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L
        val loadData = fetchDataFromUrlOrJson(url)
      
           // *** LOG: M3U'DAN GELEN POSTERİ KONTROL EDELİM ***
    Log.e("POWERDIZI_DEBUG", "Load Fonksiyonu - M3U'dan gelen poster: ${loadData.poster}")


        
        // Dizi adını temizle - hem "Dizi-1.Sezon" hem de "Dizi 1. Sezon" formatlarını destekler
        val (cleanTitle, loadDataSeason, loadDataEpisode) = parseEpisodeInfo(loadData.title)
        
       
       
       val (seriesData, episodeData) = fetchTMDBData(cleanTitle, loadData.season, loadData.episode)













// TMDB'den poster alınamazsa doğrudan varsayılanı kullanın
    val finalPosterUrl = if (seriesData?.optString("poster_path")?.isNotEmpty() == true) {
        "https://image.tmdb.org/t/p/w500${seriesData.optString("poster_path")}"
    } else {
        // M3U'dan gelen poster URL'si geçerliyse onu kullanın
        if (loadData.poster.isNotEmpty() && loadData.poster.startsWith("http")) {
            loadData.poster
        } else {
            // Aksi halde varsayılan posteri kullanın
            "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"
        }
    }















        
        val plot = buildString {
            if (seriesData != null) {
                append("<b>📺<u> Dizi Bilgileri</u> (Genel)</b><br><br>")
                
                val overview = seriesData.optString("overview", "")
                val firstAirDate = seriesData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                val ratingValue = seriesData.optDouble("vote_average", -1.0)
                val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                val tagline = seriesData.optString("tagline", "")
                val originalName = seriesData.optString("original_name", "")
                val originalLanguage = seriesData.optString("original_language", "")
                val numberOfSeasons = seriesData.optInt("number_of_seasons", 1)
                val numberOfEpisodes = seriesData.optInt("number_of_episodes", 1)

                val genresArray = seriesData.optJSONArray("genres")
                val genreList = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                    }
                }
                
                if (tagline.isNotEmpty()) append("💭 <b>Dizi Sloganı:</b><br><i>${tagline}</i><br><br>")
                if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>")
                if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                if (originalLanguage.isNotEmpty()) {
                    val langCode = originalLanguage.lowercase()
                    val turkishName = languageMap[langCode] ?: originalLanguage
                    append("🌐 <b>Orijinal Dil:</b> $turkishName<br>")
                }
                if (numberOfSeasons > 0 && numberOfEpisodes > 0)
                    append("📅 <b>Toplam Sezon:</b> $numberOfSeasons ($numberOfEpisodes bölüm)<br>")

                if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                
                // Dizi oyuncuları fotoğraflarıyla
                val creditsObject = seriesData.optJSONObject("credits")
                if (creditsObject != null) {
                    val castArray = creditsObject.optJSONArray("cast")
                    if (castArray != null && castArray.length() > 0) {
                        val castList = mutableListOf<String>()
                        for (i in 0 until minOf(castArray.length(), 25)) {
                            val actor = castArray.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            if (actorName.isNotEmpty()) {
                                castList.add(if (character.isNotEmpty()) "$actorName (${character})" else actorName)
                            }
                        }
                        if (castList.isNotEmpty()) {
                            append("👥 <b>Tüm Oyuncular:</b> ${castList.joinToString(", ")}<br>")
                        }
                    }
                }
                
            }
            
            // Bölüm bilgileri
            if (episodeData != null) {
                append("<hr><br>")
                append("<b>🎬<u> Bölüm Bilgileri</u></b><br><br>")
                
                val episodeTitle = episodeData.optString("name", "")
                val episodeOverview = episodeData.optString("overview", "")
                val episodeAirDate = episodeData.optString("air_date", "").split("-").firstOrNull() ?: ""
                val episodeRating = episodeData.optDouble("vote_average", -1.0)
                
                if (episodeTitle.isNotEmpty()) append("📽️ <b>Bölüm Adı:</b> ${episodeTitle}<br>")
                if (episodeOverview.isNotEmpty()) append("✍🏻 <b>Bölüm Konusu:</b><br><i>${episodeOverview}</i><br><br>")
                if (episodeAirDate.isNotEmpty()) append("📅 <b>Yayın Tarihi:</b> $episodeAirDate<br>")
                if (episodeRating >= 0) append("⭐ <b>Bölüm Puanı:</b> ${String.format("%.1f", episodeRating)} / 10<br>")
                
                // Bölüm oyuncuları
                val episodeCredits = episodeData.optJSONObject("credits")
                if (episodeCredits != null) {
                    val episodeCast = episodeCredits.optJSONArray("cast")
                    if (episodeCast != null && episodeCast.length() > 0) {
                        append("<br>👥 <b>Bu Bölümdeki Oyuncular:</b><br>")
                        append("<div style='display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:5px 0'>")
                        for (i in 0 until minOf(episodeCast.length(), 25)) {
                            val actor = episodeCast.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            val gender = actor?.optInt("gender", 0) ?: 0
                            
                            if (actorName.isNotEmpty()) {
                                val genderIcon = when (gender) {
                                    1 -> "👱🏼‍♀" // Kadın
                                    2 -> "👱🏻" // Erkek
                                    else -> "👤" // Belirsiz
                                }
                                append("<div style='background:#f0f0f0;padding:5px 10px;border-radius:5px'>")
                                append("$genderIcon <b>$actorName</b>")
                                if (character.isNotEmpty()) append(" ($character rolünde)")
                                append("</div>")
                            }
                        }
                        append("</div><br>")
                    }
                }
                
            }
            
            // Eğer hiçbir TMDB verisi yoksa, en azından temel bilgileri göster
            if (seriesData == null && episodeData == null) {
                append("<b>📺 DİZİ BİLGİLERİ</b><br><br>")
                append("📝 <b>TMDB'den bilgi alınamadı.</b><br><br>")
            }
            
            val nation = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) {
                "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
            } else {
                "» ${loadData.group} | ${loadData.nation} «"
            }
            append(nation)
        }

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        
        val allShows = kanallar.items.groupBy { item ->
            val (itemCleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
            itemCleanTitle
        }

        val currentShowEpisodes = allShows[cleanTitle]?.mapNotNull { kanal ->
            val title = kanal.title.toString()
            val (episodeCleanTitle, season, episode) = parseEpisodeInfo(title)
            
            if (season != null && episode != null) {
                newEpisode(LoadData(kanal.url.toString(), title, kanal.attributes["tvg-logo"].toString(), kanal.attributes["group-title"].toString(), kanal.attributes["tvg-country"]?.toString() ?: "TR", season, episode).toJson()) {
                    this.name = episodeCleanTitle
                    this.season = season
                    this.episode = episode
                    this.posterUrl = kanal.attributes["tvg-logo"].toString()
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
            val tmdbPosterPath = seriesData?.optString("poster_path", "")
            val tmdbPosterUrl = if (tmdbPosterPath?.isNotEmpty() == true) {
                "https://image.tmdb.org/t/p/w500$tmdbPosterPath"
            } else {
                null
            }




        // *** LOG: TMDB'DEN GELEN POSTERİ KONTROL EDELİM ***
        Log.e("POWERDIZI_DEBUG", "Load Fonksiyonu - TMDB Poster URL: $tmdbPosterUrl")
    this.posterUrl = finalPosterUrl // Burada yeni değişkeni kullanın
       //    this.posterUrl = tmdbPosterUrl ?: loadData.poster    
           this.plot = plot
           this.tags = listOf(loadData.group, loadData.nation)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        Log.d("IPTV", "loadData » $loadData")

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val kanal = kanallar.items.firstOrNull { it.url == loadData.url } ?: return false
        Log.d("IPTV", "kanal » $kanal")

        val videoUrl = loadData.url
        val videoType = when {
            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> ExtractorLinkType.M3U8
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${loadData.title} (S${loadData.season}:E${loadData.episode})",
                url = videoUrl,
                type = videoType
            ) {
                headers = kanal.headers
                referer = kanal.headers["referrer"] ?: ""
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
        val episode: Int = 0,
        val isWatched: Boolean = false,
        val watchProgress: Long = 0
    )

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.first { it.url == data }            
            val streamurl = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl = kanal.attributes["tvg-logo"].toString().trim()
            val chGroup = kanal.attributes["group-title"].toString()
            val nation = kanal.attributes["tvg-country"].toString()

            val (cleanTitle, season, episode) = parseEpisodeInfo(channelname)

            return LoadData(streamurl, cleanTitle, posterurl, chGroup, nation, season ?: 1, episode ?: 0)
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
    val userAgent: String? = null,
    val season: Int = 1,
    val episode: Int = 0
) {
    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

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

        val EXT_M3U = PlaylistItem.EXT_M3U
        val EXT_INF = PlaylistItem.EXT_INF
        val EXT_VLC_OPT = PlaylistItem.EXT_VLC_OPT

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
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")?.toString()
                    val referrer = line.getTagValue("http-referrer")?.toString()

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
                        val urlHeaders = if (referrer != null) {item.headers + mapOf("referrer" to referrer)} else item.headers

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

    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)

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

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim()
        val titleAndAttributes = attributesString.split(",", limit = 2)
        
        val attributes = mutableMapOf<String, String>()
        if (titleAndAttributes.size > 1) {
            val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"|([\\w-]+)=([^\"]+)")
            
            attrRegex.findAll(titleAndAttributes[0]).forEach { matchResult ->
                val (quotedKey, quotedValue, unquotedKey, unquotedValue) = matchResult.destructured
                val key = quotedKey.takeIf { it.isNotEmpty() } ?: unquotedKey
                val value = quotedValue.takeIf { it.isNotEmpty() } ?: unquotedValue
                attributes[key] = value.replaceQuotesAndTrim()
            }
        }
  // if (!attributes.containsKey("tvg-logo")) {
    //    attributes["tvg-logo"] = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg" // Veya bir varsayılan resim URL'si atayın
  // }


  val tvgLogo = attributes["tvg-logo"]
    if (tvgLogo.isNullOrEmpty() || !tvgLogo.startsWith("http")) { // Geçerli bir URL değilse kontrolü
        attributes["tvg-logo"] = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"
    }

  

   
        
        if (!attributes.containsKey("tvg-country")) {
            attributes["tvg-country"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("tvg-language")) {
            attributes["tvg-language"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("group-title")) {
            val (cleanTitle, _, _) = parseEpisodeInfo(titleAndAttributes.last())
            attributes["group-title"] = cleanTitle
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
