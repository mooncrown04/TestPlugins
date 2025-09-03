package com.example

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.example.BuildConfig
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
    override var mainUrl = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name = "A-B Dizi 🎬"
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
                    this.posterUrl = posterurl
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
                this.posterUrl = posterurl
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
    val episode: Int =
