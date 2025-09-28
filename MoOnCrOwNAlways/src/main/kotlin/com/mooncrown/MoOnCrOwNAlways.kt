package com.mooncrown

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus
import java.io.InputStream
import java.util.Locale
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Score
import java.io.BufferedReader

import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder



import org.json.JSONArray
import java.text.NumberFormat
import java.util.*
import kotlin.math.min


// --- Ana Eklenti Sınıfı ---
class MoOnCrOwNAlways(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/piul7441pe1l41qcgq62y/powerdizi.m3u?rlkey=zwfgmuql18m09a9wqxe3irbbr"
    override var name = "35 mooncrown always FULL yeni"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL =
        "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    private var cachedPlaylist: Playlist? = null
    private val CACHE_KEY = "iptv_playlist_cache"


    private suspend fun checkPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        return try {
            val response = app.head(url)
            if (response.isSuccessful) {
                url
            } else {
                Log.e(name, "Resim URL'si geçersiz: $url, Hata Kodu: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(name, "Resim URL'si kontrol edilirken hata: $url", e)
            null
        }
    }

    private suspend fun checkContentType(url: String?, headers: Map<String, String>): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        return try {
            val response = withContext(Dispatchers.IO) {
                app.head(url, headers = headers)
            }
            if (response.isSuccessful) {
                val contentType = response.headers["Content-Type"]?.lowercase(Locale.getDefault())
                when {
                    contentType?.contains("video/mp4") == true -> "mp4"
                    contentType?.contains("video/mkv") == true -> "mkv"
                    contentType?.contains("video/x-matroska") == true -> "mkv"
                    contentType?.contains("application/vnd.apple.mpegurl") == true ||
                    contentType?.contains("application/x-mpegurl") == true -> "m3u8"
                    else -> {
                        if (contentType?.startsWith("text/") == true ||
                            contentType?.contains("application/json") == true
                        ) {
                            "m3u8"
                        } else {
                            null
                        }
                    }
                }
            } else {
                Log.e(name, "URL türü kontrol edilemedi: $url, Hata Kodu: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(name, "URL türü kontrol edilirken hata: $url", e)
            null
        }
    }


// --- Yardımcı Sınıflar ---
data class Playlist(val items: List<PlaylistItem> = emptyList())
data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val score: Double? = null
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
        if (!reader.readLine().isExtendedM3u()) throw PlaylistParserException.InvalidHeader()

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var line: String? = reader.readLine()
        var currentItem: PlaylistItem? = null

        while (line != null) {
            if (line.isNotEmpty()) {
                when {
                    line.startsWith(PlaylistItem.EXT_INF) -> {
                        currentItem = PlaylistItem(
                            title = line.getTitle(),
                            attributes = line.getAttributes(),
                            score = line.getAttributes()["tvg-score"]?.toDoubleOrNull()
                        )
                    }
                    line.startsWith(PlaylistItem.EXT_VLC_OPT) -> {
                        if (currentItem != null) {
                            val userAgent = line.getVlcOptUserAgent()
                            currentItem = currentItem.copy(userAgent = userAgent)
                        }
                    }
                    !line.startsWith("#") -> {
                        if (currentItem != null) {
                            val url = line.getUrl()
                            currentItem = currentItem.copy(url = url)
                            playlistItems.add(currentItem)
                            currentItem = null
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)
    private fun String.getTitle(): String? = split(",").lastOrNull()?.trim()

    private fun String.getAttributes(): Map<String, String> {
        val attributesString = substringAfter("#EXTINF:-1 ")
        val attributes = mutableMapOf<String, String>()
        val quotedRegex = Regex("""([a-zA-Z0-9-]+)="(.*?)"""")
        val unquotedRegex = Regex("""([a-zA-Z0-9-]+)=([^"\s]+)""")

        quotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value.trim()
        }

        unquotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            if (!attributes.containsKey(key)) {
                attributes[key] = value.trim()
            }
        }
        return attributes
    }

    private fun String.getUrl(): String? = split("|").firstOrNull()?.trim()
    private fun String.getVlcOptUserAgent(): String? =
        substringAfter("http-user-agent=").trim()
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    val textWithCleanedChars = text.replace(Regex("[\\u200E\\u200F]"), "")
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""", RegexOption.IGNORE_CASE)
    val format4Regex = Regex("""(.*?)\s*(\d+)\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format5Regex = Regex("""(.*?)\s*S(\d+)E(\d+).*""", RegexOption.IGNORE_CASE)

    val matchResult1 = format1Regex.find(textWithCleanedChars)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult2 = format2Regex.find(textWithCleanedChars)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult3 = format3Regex.find(textWithCleanedChars)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }
    val matchResult4 = format4Regex.find(textWithCleanedChars)
    if (matchResult4 != null) {
        val (title, episodeStr) = matchResult4.destructured
        return Triple(title.trim(), 1, episodeStr.toIntOrNull())
    }

    val matchResult5 = format5Regex.find(textWithCleanedChars)
    if (matchResult5 != null) {
        val (title, episodeStr) = matchResult5.destructured
        return Triple(title.trim(), 1, episodeStr.toIntOrNull())
    }

    return Triple(textWithCleanedChars.trim(), null, null)
}

data class LoadData(
    val items: List<PlaylistItem>,
    val title: String,
    val poster: String,
    val group: String,
    val nation: String,
    val season: Int = 1,
    val episode: Int = 0,
    val isDubbed: Boolean,
    val isSubbed: Boolean,
    val score: Double? = null,
	val videoFormats: Set<String> = emptySet() 

)

private suspend fun getOrFetchPlaylist(): Playlist {
    Log.d(name, "Playlist verisi ağdan indiriliyor.")
    val content = app.get(mainUrl).text
    val newPlaylist = IptvPlaylistParser().parseM3U(content)
    cachedPlaylist = newPlaylist
    sharedPref?.edit()?.putString(CACHE_KEY, newPlaylist.toJson())?.apply()
    return newPlaylist
}



// isDubbed ve isSubbed fonksiyonları
private fun isDubbed(item: PlaylistItem): Boolean {
    val dubbedKeywords = listOf("dublaj", "türkçe", "turkish")
    val language = item.attributes["tvg-language"]?.lowercase(Locale.getDefault())
    val titleLower = item.title.toString().lowercase(Locale.getDefault())

    // Başlıkta veya dil bilgisinde "dublaj", "türkçe" gibi kelimeler var mı kontrol eder.
    return dubbedKeywords.any { keyword -> titleLower.contains(keyword) } || language?.contains("dublaj") == true || language?.contains("tr") == true || language?.contains("turkish") == true
}

private fun isSubbed(item: PlaylistItem): Boolean {
    val subbedKeywords = listOf("altyazılı", "altyazi")
    val language = item.attributes["tvg-language"]?.lowercase(Locale.getDefault())
    val titleLower = item.title.toString().lowercase(Locale.getDefault())

    // Başlıkta veya dil bilgisinde "altyazılı" veya "eng" kelimeleri var mı kontrol eder.
    return subbedKeywords.any { keyword -> titleLower.contains(keyword) } || language?.contains("en") == true || language?.contains("eng") == true || language?.contains("altyazi") == true
}






// Yeni eklenen yardımcı fonksiyon
private suspend fun createSearchResponse(cleanTitle: String, shows: List<PlaylistItem>): SearchResponse? {
    val firstShow = shows.firstOrNull() ?: return null

    // POSTER ATAMASI:
    val rawPosterUrl = firstShow.attributes["tvg-logo"]
    val verifiedPosterUrl = checkPosterUrl(rawPosterUrl)
    val finalPosterUrl = verifiedPosterUrl ?: DEFAULT_POSTER_URL
    
    // Düzeltme: Tüm bölümlerin puanlarından en yükseğini al.
    val score = shows.mapNotNull { it.score }.maxOrNull()
    val isDubbed = isDubbed(firstShow)
    val isSubbed = isSubbed(firstShow)


    // YENİ: Video formatlarını toplamak için set kullanın
    val videoFormats = shows.mapNotNull { it.url?.let { url -> 
        when {
            url.endsWith(".mkv", ignoreCase = true) -> "MKV"
            url.endsWith(".mp4", ignoreCase = true) -> "MP4"
            else -> "M3U8"
        }
    } }.toSet() // Yinelenen formatları önlemek için Set kullanılır




    val loadData = LoadData(
        items = shows,
        title = cleanTitle,
        poster = finalPosterUrl,
        group = firstShow.attributes["group-title"] ?: "Bilinmeyen Grup",
        nation = firstShow.attributes["tvg-country"] ?: "TR",
        isDubbed = isDubbed,
        isSubbed = isSubbed,
        score = score,
        videoFormats = videoFormats 
    )

    return newAnimeSearchResponse(cleanTitle, loadData.toJson()).apply {
        posterUrl = loadData.poster
        type = TvType.Anime
        this.score = score?.let { Score.from10(it) }

        // tvg-quality'den gelen bilgiye göre SearchQuality ataması
        val qualityString = firstShow.attributes["tvg-quality"]
        this.quality = when (qualityString) {
            "P360", "P480" -> SearchQuality.SD
            "P720", "P1080" -> SearchQuality.HD
            "P2160" -> SearchQuality.UHD
            else -> null
        }

        if (isDubbed || isSubbed) {
            addDubStatus(dubExist = isDubbed, subExist = isSubbed)
        }
    }
}


override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val kanallar = getOrFetchPlaylist()
    val groupedByCleanTitle = kanallar.items.groupBy {
        val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
        cleanTitle
    }

    val alphabeticGroups = groupedByCleanTitle.toSortedMap().mapNotNull { (cleanTitle, shows) ->
        // Ortak fonksiyonu burada çağırıyoruz
        val searchResponse = createSearchResponse(cleanTitle, shows) ?: return@mapNotNull null

        val firstChar = cleanTitle.firstOrNull()?.uppercaseChar() ?: '#'
        val groupKey = when {
            firstChar.isLetter() -> firstChar.toString()
            firstChar.isDigit() -> "0-9"
            else -> "#"
        }
        Pair(groupKey, searchResponse)
    }.groupBy { it.first }.mapValues { it.value.map { it.second } }.toSortedMap()

    val finalHomePageLists = mutableListOf<HomePageList>()
    val turkishAlphabet = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUVYZ".split("").filter { it.isNotBlank() }
    val fullAlphabet = turkishAlphabet + listOf("Q", "W", "X")
    val allGroupsToProcess = mutableListOf<String>()
    if (alphabeticGroups.containsKey("0-9")) allGroupsToProcess.add("0-9")
    fullAlphabet.forEach { char ->
        if (alphabeticGroups.containsKey(char)) {
            allGroupsToProcess.add(char)
        }
    }
    if (alphabeticGroups.containsKey("#")) allGroupsToProcess.add("#")

    allGroupsToProcess.forEach { char ->
        val shows = alphabeticGroups[char]
        if (shows != null && shows.isNotEmpty()) {
            val infiniteList = shows
            
		    val listTitle = when (char) {
                "0-9" -> "🔢 0-9 ${fullAlphabet.joinToString(" ") { it.lowercase(Locale.getDefault()) }}"
                "#" -> "🔣 # ${fullAlphabet.joinToString(" ") { it.lowercase(Locale.getDefault()) }}"
                else -> {
                    val startIndex = fullAlphabet.indexOf(char)
                    if (startIndex != -1) {
                        val remainingAlphabet = fullAlphabet.subList(startIndex, fullAlphabet.size).joinToString(" ") { it }
                        "🎬 $char ${remainingAlphabet.substring(1).lowercase(Locale.getDefault())}"
                    } else {
                        "🎬 $char"
                    }
                }
            }
            finalHomePageLists.add(HomePageList(listTitle, infiniteList, isHorizontalImages = true))
        }
    }

    return newHomePageResponse(finalHomePageLists, hasNext = false)
}


override suspend fun search(query: String): List<SearchResponse> {
    val kanallar = getOrFetchPlaylist()
    val groupedByCleanTitle = kanallar.items.groupBy {
        val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
        cleanTitle
    }

    return groupedByCleanTitle.filter { (cleanTitle, _) ->
        cleanTitle.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
    }.mapNotNull { (cleanTitle, shows) ->
        // Ortak fonksiyonu burada çağırıyoruz
        createSearchResponse(cleanTitle, shows)
    }
}

override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

// TMDB'den dizi/film genel detaylarını çeken fonksiyon
private suspend fun fetchTMDBData(title: String): Pair<JSONObject?, TvType> {
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = "4032c1fd53e1b6fef5af1b406fccaa72"
            if (apiKey.isEmpty()) {
                Log.e("TMDB", "API anahtarı boş.")
                return@withContext Pair(null, TvType.TvSeries)
            }

            val encodedTitle = URLEncoder.encode(title.replace(Regex("\\([^)]*\\)"), "").trim(), "UTF-8")

            // Önce TV şovu olarak arama yap
            val searchTvUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"
            val tvResponse = JSONObject(URL(searchTvUrl).readText())
            val tvResults = tvResponse.optJSONArray("results")

            // Filmler için arama yap
            val searchMovieUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$encodedTitle&language=tr-TR"
            val movieResponse = JSONObject(URL(searchMovieUrl).readText())
            val movieResults = movieResponse.optJSONArray("results")

            if (tvResults != null && tvResults.length() > 0) {
                val tvId = tvResults.optJSONObject(0)?.optInt("id")
                if (tvId != null) {
                    val detailsUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits&language=tr-TR"
                    val detailsResponse = URL(detailsUrl).readText()
                    return@withContext Pair(JSONObject(detailsResponse), TvType.TvSeries)
                }
            }

            if (movieResults != null && movieResults.length() > 0) {
                val movieId = movieResults.optJSONObject(0)?.optInt("id")
                if (movieId != null) {
                    val detailsUrl = "https://api.themoviedb.org/3/movie/$movieId?api_key=$apiKey&append_to_response=credits&language=tr-TR"
                    val detailsResponse = URL(detailsUrl).readText()
                    return@withContext Pair(JSONObject(detailsResponse), TvType.Movie)
                }
            }

            Pair(null, TvType.TvSeries)

        } catch (e: Exception) {
            Log.e("TMDB", "TMDB verisi çekilirken hata oluştu: ${e.message}", e)
            Pair(null, TvType.TvSeries)
        }
    }
}


// ✨ YENİ: TMDB'den belirli bir bölümün detaylarını çeken fonksiyon
private suspend fun fetchEpisodeTMDBData(tvId: Int, season: Int, episode: Int): JSONObject? {
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = "4032c1fd53e1b6fef5af1b406fccaa72"
            val detailsUrl = "https://api.themoviedb.org/3/tv/$tvId/season/$season/episode/$episode?api_key=$apiKey&language=tr-TR"
            val detailsResponse = URL(detailsUrl).readText()
            JSONObject(detailsResponse)
        } catch (e: Exception) {
            Log.e("TMDB", "TMDB Bölüm verisi çekilirken hata oluştu: $tvId, S:$season E:$episode. Hata: ${e.message}", e)
            null
        }
    }
}


override suspend fun load(url: String): LoadResponse {
    val loadData = parseJson<LoadData>(url)
    val (tmdbData, tmdbType) = fetchTMDBData(loadData.title)
    
    // ✨ YENİ: TMDB ID'yi al
    val tmdbId = tmdbData?.optInt("id") 

    val plot = buildString {
        if (tmdbData != null) {
            val overview = tmdbData.optString("overview", "")
            val releaseDate = if (tmdbType == TvType.Movie) {
                tmdbData.optString("release_date", "").split("-").firstOrNull() ?: ""
            } else {
                tmdbData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
            }
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
                    for (i in 0 until min(castArray.length(), 10)) {
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

            val formatNumber = { num: Long ->
                try {
                    NumberFormat.getNumberInstance(Locale("tr", "TR")).format(num)
                } catch (e: Exception) {
                    Log.e("FormatError", "Formatlanırken hata oluştu: $num", e)
                    num.toString()
                }
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
            if (budget > 0) append("💰 <b>Bütçe:</b> $${formatNumber(budget)}<br>")
            if (revenue > 0) append("💵 <b>Hasılat:</b> $${formatNumber(revenue)}<br>")
            append("<br>")
        } else {
            append("<i>Film/Dizi detayları alınamadı.</i><br><br>")
        }
    }
    val allShows = loadData.items
    

    val finalPosterUrl = loadData.poster

    // loadData'dan gelen puanı kullan
    val scoreToUse = loadData.score
    val dubbedEpisodes = mutableListOf<Episode>()
    val subbedEpisodes = mutableListOf<Episode>()
    // Bölümleri sezon ve bölüme göre gruplandırıp, aynı bölümün tüm kaynaklarını bir arada tutar.
    val groupedEpisodes = allShows.groupBy {
        val (_, season, episode) = parseEpisodeInfo(it.title.toString())
        Pair(season, episode)
    }
    groupedEpisodes.forEach { (key, episodeItems) ->
        val (season, episode) = key
        val item = episodeItems.first()
        val (itemCleanTitle, _, _) = parseEpisodeInfo(item.title.toString())
        val finalSeason = season ?: 1
        val finalEpisode = episode ?: 1
        val isDubbed = isDubbed(item)
        val isSubbed = isSubbed(item)
        val episodePoster = item.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: finalPosterUrl

        // Bölüm özetini tutacak değişken
        var episodePlot: String? = null
        
        // ✨ YENİ: TMDB ID varsa ve bu bir TV şovuysa bölüm özetini çek
        if (tmdbId != null && tmdbType == TvType.TvSeries) {
            val tmdbEpisodeData = fetchEpisodeTMDBData(tmdbId, finalSeason, finalEpisode)
            // Bölüm özetini al
            episodePlot = tmdbEpisodeData?.optString("overview")?.takeIf { it.isNotBlank() }
        }


        val episodeLoadData = LoadData(
            items = episodeItems,
            title = itemCleanTitle,
            poster = finalPosterUrl,
            group = item.attributes["group-title"] ?: "Bilinmeyen Grup",
            nation = item.attributes["tvg-country"] ?: "TR",
            season = finalSeason,
            episode = finalEpisode,
            isDubbed = isDubbed,
            isSubbed = isSubbed,
            score = item.score
        )

        val episodeObj = newEpisode(episodeLoadData.toJson()) {
            this.name = if (season != null && episode != null) {
                "${itemCleanTitle} S$finalSeason E$finalEpisode"
            } else {
                itemCleanTitle
            }
            this.season = finalSeason
            this.episode = finalEpisode
            this.posterUrl = episodePoster
            // ✨ YENİ: Bölüm özetini ata
            this.plot = episodePlot
        }

        if (isDubbed) {
            dubbedEpisodes.add(episodeObj)
        } else {
            subbedEpisodes.add(episodeObj)
        }
    }
    
    dubbedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))
    subbedEpisodes.sortWith(compareBy({ it.season }, { it.episode }))

    val episodesMap = mutableMapOf<DubStatus, List<Episode>>()

    if (dubbedEpisodes.isNotEmpty()) {
        episodesMap[DubStatus.Dubbed] = dubbedEpisodes
    }
    if (subbedEpisodes.isNotEmpty()) {
        episodesMap[DubStatus.Subbed] = subbedEpisodes
    }
    val actorsList = mutableListOf<ActorData>()
    actorsList.add(
        ActorData(
            actor = Actor("MoOnCrOwN","https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"),
            roleString = "yazılım amalesi"
        )
    )
    val tags = mutableListOf<String>()
    tags.add(loadData.group)
    tags.add(loadData.nation)
    tags.addAll(loadData.videoFormats)

	 // Doğru bir şekilde tvg-language bilgisini ekle
    loadData.items.firstOrNull()?.attributes?.get("tvg-language")?.let {
        tags.add(it)
    }
    // LoadData içindeki bilgiyi kullanarak doğrudan etiket ekle
    if (loadData.isDubbed) {
        tags.add("Türkçe Dublaj")
    }
    if (loadData.isSubbed) {
        tags.add("Türkçe Altyazılı")
    }

    val recommendedList = (dubbedEpisodes + subbedEpisodes)
    // .shuffled()
    .take(24)
    .mapNotNull { episode ->
        val episodeLoadData = parseJson<LoadData>(episode.data)
        val episodeTitleWithNumber = if (episodeLoadData.episode > 0) {
            "${episodeLoadData.title} S${episodeLoadData.season} E${episodeLoadData.episode}"
        } else {
            episodeLoadData.title
        }
        
        newAnimeSearchResponse(episodeTitleWithNumber, episode.data).apply {
            posterUrl = episodeLoadData.poster
            type = TvType.Anime
            // HER DİSİ İÇİN KENDİ SKORUNU EKLEME KISMI
            this.score = episodeLoadData.score?.let { Score.from10(it) }

            
            if (episodeLoadData.isDubbed || episodeLoadData.isSubbed) {
                addDubStatus(dubExist = episodeLoadData.isDubbed, subExist = episodeLoadData.isSubbed)
            }
        }
    }

    return newAnimeLoadResponse(
        loadData.title,
        url,
		tmdbType
    ) {
        this.posterUrl = finalPosterUrl
        this.plot = plot
        this.score = scoreToUse?.let { Score.from10(it) }
        this.tags = tags
        this.episodes = episodesMap
        this.recommendations = recommendedList
        this.actors = listOf(
            ActorData(
                Actor(loadData.title, finalPosterUrl),
                roleString = "KANAL İSMİ"
            )
        ) + actorsList
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val loadData = parseJson<LoadData>(data)
    // loadData'nın içindeki tüm kaynakları döngüye al
    loadData.items.forEachIndexed { index, item ->
        
        val linkName = loadData.title + " Kaynak ${index + 1}"
        
        val qualityString = item.attributes["tvg-quality"]
        val linkQuality = when (qualityString) {
            "P360" -> Qualities.P360.value
            "P480" -> Qualities.P480.value
            "P720" -> Qualities.P720.value
            "P1080" -> Qualities.P1080.value
            "P2160" -> Qualities.P2160.value
            else -> Qualities.Unknown.value
        }
        
        val videoUrl = item.url.toString()
        val headersMap = mutableMapOf<String, String>()
        headersMap["Referer"] = mainUrl
        item.userAgent?.let {
            headersMap["User-Agent"] = it
        }

        // Yeni fonksiyonu kullanarak video tipini belirle
        val detectedType = checkContentType(videoUrl, headersMap)
        val videoType = when {
            detectedType == "mkv" -> ExtractorLinkType.VIDEO
            detectedType == "mp4" -> ExtractorLinkType.VIDEO
            detectedType == "m3u8" -> ExtractorLinkType.M3U8
            // Eğer Content-Type başlığından tip belirlenemezse, uzantıya bak.
            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
            videoUrl.endsWith(".mp4", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> ExtractorLinkType.M3U8 // Varsayılan olarak M3U8
        }
        
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = linkName,
                url = videoUrl,
                type = videoType
            ) {
                quality = linkQuality
                headers = headersMap
            }
        )
    }
    return true
}

private data class ParsedEpisode(
    val item: PlaylistItem,
    val itemCleanTitle: String,
    val season: Int?,
    val episode: Int?
)
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
