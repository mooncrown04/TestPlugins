package com.megix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import com.megix.CineStreamExtractors.invokeVegamovies
import com.megix.CineStreamExtractors.invokeMoviesmod
import com.megix.CineStreamExtractors.invokeTopMovies
import com.megix.CineStreamExtractors.invokeMoviesdrive
import com.megix.CineStreamExtractors.invokeW4U
import com.megix.CineStreamExtractors.invokeWYZIESubs
import com.megix.CineStreamExtractors.invokeAnizone
// import com.megix.CineStreamExtractors.invokeVidbinge
import com.megix.CineStreamExtractors.invokeUhdmovies
// import com.megix.CineStreamExtractors.invokeRar
import com.megix.CineStreamExtractors.invokeAnimes
import com.megix.CineStreamExtractors.invokeMultimovies
import com.megix.CineStreamExtractors.invokeStreamify
import com.megix.CineStreamExtractors.invokeCinemaluxe
import com.megix.CineStreamExtractors.invokeBollyflix
import com.megix.CineStreamExtractors.invokeTorrentio
import com.megix.CineStreamExtractors.invokeTokyoInsider
import com.megix.CineStreamExtractors.invokeTvStream
import com.megix.CineStreamExtractors.invokeAllanime
import com.megix.CineStreamExtractors.invokeStreamAsia
import com.megix.CineStreamExtractors.invokeNetflix
import com.megix.CineStreamExtractors.invokePrimeVideo
import com.megix.CineStreamExtractors.invokeFlixhq
import com.megix.CineStreamExtractors.invokeSkymovies
import com.megix.CineStreamExtractors.invokeMoviesflix
import com.megix.CineStreamExtractors.invokeHdmovie2
import com.megix.CineStreamExtractors.invokeHindmoviez
import com.megix.CineStreamExtractors.invokeMostraguarda
import com.megix.CineStreamExtractors.invokePlayer4U
import com.megix.CineStreamExtractors.invokePrimeWire
import com.megix.CineStreamExtractors.invokeProtonmovies
import com.megix.CineStreamExtractors.invokeThepiratebay
import com.megix.CineStreamExtractors.invokeTom
import com.megix.CineStreamExtractors.invokeAllmovieland
import com.megix.CineStreamExtractors.invoke4khdhub
// import com.megix.CineStreamExtractors.invokeVidJoy
import com.megix.CineStreamExtractors.invokeMovies4u
import com.megix.CineStreamExtractors.invokeSoaper
import com.megix.CineStreamExtractors.invokeAsiaflix
import com.megix.CineStreamExtractors.invoke2embed
import com.megix.CineStreamExtractors.invokePrimebox
import com.megix.CineStreamExtractors.invokePrimenet
import com.megix.CineStreamExtractors.invokeAnimeparadise
import com.megix.CineStreamExtractors.invokeGojo
import com.megix.CineStreamExtractors.invokeSudatchi
import com.megix.CineStreamExtractors.invokePhoenix
import com.megix.CineStreamExtractors.invokeFilmModu
class CineSimklProvider: MainAPI() {
    override var name = "CineSimkl"
    override var mainUrl = "https://simkl.com"
    override var supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
        TvType.Torrent
    )
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedSyncNames = setOf(SyncIdName.Simkl, SyncIdName.Anilist)
    private val apiUrl = "https://api.simkl.com"
    private final val mediaLimit = 20
    private val auth = BuildConfig.SIMKL_API
    private val headers = mapOf("Content-Type" to "application/json")
    private val api = AccountManager.simklApi
    private val kitsuAPI = "https://anime-kitsu.strem.fun"
    private val cinemetaAPI = "https://v3-cinemeta.strem.io"

    override val mainPage = mainPageOf(
        "/movies/genres/all/all-types/all-countries/this-year/popular-this-week?limit=$mediaLimit&page=" to "Trending Movies This Week",
        "/tv/genres/all/all-types/all-countries/this-year/popular-today?limit=$mediaLimit&page=" to "Trending Shows Today",
        "/anime/airing?date?sort=popularity" to "Airing Anime Today",
        "/anime/genres/all/this-year/popular-today?limit=$mediaLimit&page=" to "Trending Anime Today",
        "/tv/genres/all/all-types/kr/all-networks/this-year/popular-this-week?limit=$mediaLimit&page=" to "Trending Korean Shows This Week",
        "/tv/genres/all/all-types/all-countries/netflix/this-year/popular-today?limit=$mediaLimit&page=" to "Trending Netflix Shows",
        "/tv/genres/all/all-types/all-countries/disney/this-year/popular-today?limit=$mediaLimit&page=" to "Trending Disney Shows",
        "/tv/genres/all/all-types/all-countries/hbo/this-year/popular-today?limit=$mediaLimit&page=" to "Trending HBO Shows",
        "Personal" to "Personal",
    )

    private fun getSimklId(url: String): String {
        return url.split('/')
            .filter { part -> part.toIntOrNull() != null } // Keep only numeric parts
            .firstOrNull() ?: "" // Take the first numeric ID found
    }

    private suspend fun extractImdbId(kitsuId: Int? = null): String? {
        return try {
            if (kitsuId == null) return null

            val response = app.get("$kitsuAPI/meta/series/kitsu:$kitsuId.json")
            if (!response.isSuccessful) {
                return null
            }

            val jsonString = response.text
            val rootObject = JSONObject(jsonString)
            val metaObject = rootObject.optJSONObject("meta")
            metaObject?.optString("imdb_id")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun extractNameAndTMDBId(imdbId: String? = null): Pair<String?, Int?> {
        return try {
            if (imdbId.isNullOrBlank()) return Pair(null, null)

            val response = app.get("$cinemetaAPI/meta/series/$imdbId.json")
            if (!response.isSuccessful) {
                return Pair(null, null)
            }

            val jsonString = response.text
            val rootObject = JSONObject(jsonString)
            val metaObject = rootObject.optJSONObject("meta")

            val name = metaObject?.optString("name")?.takeIf { it.isNotBlank() }
            val moviedbId = metaObject?.optInt("moviedb_id", -1)?.takeIf { it != -1 }

            Pair(name, moviedbId)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun getPosterUrl(
        url: String? = null,
        type: String,
     ): String? {
        val baseUrl = "https://wsrv.nl/?url=https://simkl.in"
        if(url == null) {
            return null
        } else if(type == "episode") {
            return "$baseUrl/episodes/${url}_c.webp"
        } else if(type == "poster") {
            return "$baseUrl/posters/${url}_m.webp"
        }
        else {
            return "$baseUrl/fanart/${url}_medium.webp"
        }
    }

    override suspend fun search(query: String): List<SearchResponse> = coroutineScope {

        suspend fun fetchResults(type: String): List<SearchResponse> {
            val result = runCatching {
                val json = app.get("$apiUrl/search/$type?q=$query&page=1&limit=$mediaLimit&extended=full&client_id=$auth", headers = headers).text
                parseJson<Array<SimklResponse>>(json).map {
                    newMovieSearchResponse("${it.title_en ?: it.title}", "$mainUrl${it.url}") {
                        posterUrl = getPosterUrl(it.poster, "poster")
                    }
                }
            }.getOrDefault(emptyList())

            if (result.isNotEmpty()) return result
            return emptyList()
        }

        val types = listOf("movie", "tv", "anime")
        val resultsLists = types.map {
            async { fetchResults(it) }
        }.awaitAll()

        val maxSize = resultsLists.maxOfOrNull { it.size } ?: 0

        buildList {
            for (i in 0 until maxSize) {
                for (list in resultsLists) {
                    if (i < list.size) add(list[i])
                }
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.contains("Personal")) {
            // Reading and manipulating personal library
            api.loginInfo()
                    ?: return newHomePageResponse(
                            "Login required for personal content.",
                            emptyList<SearchResponse>(),
                            false
                    )
            var homePageList =
                    api.getPersonalLibrary()?.allLibraryLists?.mapNotNull {
                        if (it.items.isEmpty()) return@mapNotNull null
                        val libraryName =
                                it.name.asString(activity ?: return@mapNotNull null)
                        HomePageList("${request.name}: $libraryName", it.items)
                    }
                            ?: return null
            return newHomePageResponse(homePageList, false)
        } else {
            val jsonString = app.get(apiUrl + request.data + page, headers = headers).text
            val json = parseJson<Array<SimklResponse>>(jsonString)
            val data = json.map {
                newMovieSearchResponse("${it.title}", "$mainUrl${it.url}") {
                    this.posterUrl = getPosterUrl(it.poster, "poster")
                }
            }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = data,
                ),
                hasNext = if(request.data.contains("page=")) true else false
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val simklId = getSimklId(url)
        val jsonString = app.get("$apiUrl/tv/$simklId?extended=full", headers = headers).text
        val json = parseJson<SimklResponse>(jsonString)
        val genres = json.genres?.map { it.toString() }
        val tvType = json.type ?: ""
        val country = json.country ?: ""
        val isAnime = if(tvType == "anime") true else false
        val isBollywood = if(country == "IN") true else false
        val isAsian = if(!isAnime && (country == "JP" || country == "KR" || country == "CN")) true else false
        val en_title = json.en_title ?: json.title
        val recommendations = json.users_recommendations?.map {
            newMovieSearchResponse("${it.title}", "$mainUrl/${it.type}/${it.ids?.simkl}/${it.ids?.slug}") {
                this.posterUrl = getPosterUrl(it.poster, "poster")
            }
        }

        if (tvType == "movie" || (tvType == "anime" && json.anime_type?.equals("movie") == true)) {
            val data = LoadLinksData(
                json.title,
                en_title,
                tvType,
                simklId?.toIntOrNull(),
                json.ids?.imdb,
                json.ids?.tmdb?.toIntOrNull(),
                json.year,
                json.ids?.anilist?.toIntOrNull(),
                json.ids?.mal?.toIntOrNull(),
                json.ids?.kitsu?.toIntOrNull(),
                null,
                null,
                null,
                isAnime,
                isBollywood,
                isAsian
            ).toJson()
            return newMovieLoadResponse("${en_title}", url, if(isAnime) TvType.AnimeMovie  else TvType.Movie, data) {
                this.posterUrl = getPosterUrl(json.poster, "poster")
                this.backgroundPosterUrl = getPosterUrl(json.fanart, "fanart")
                this.plot = json.overview
                this.tags = genres
                this.duration = json.runtime?.toIntOrNull()
                this.rating = json.ratings?.simkl?.rating.toString().toRatingInt()
                this.year = json.year
                this.recommendations = recommendations
                this.contentRating = json.certification
                this.addSimklId(simklId.toInt())
                this.addAniListId(json.ids?.anilist?.toIntOrNull())
            }
        } else {
            val epsJson = app.get("$apiUrl/tv/episodes/$simklId?extended=full", headers = headers).text
            val eps = parseJson<Array<Episodes>>(epsJson)
            val episodes = eps.filter { it.type != "special" }.map {
                newEpisode(
                    LoadLinksData(
                        json.title,
                        en_title,
                        tvType,
                        simklId?.toIntOrNull(),
                        json.ids?.imdb,
                        json.ids?.tmdb?.toIntOrNull(),
                        json.year,
                        json.ids?.anilist?.toIntOrNull(),
                        json.ids?.mal?.toIntOrNull(),
                        json.ids?.kitsu?.toIntOrNull(),
                        json.season?.toIntOrNull() ?: it.season,
                        it.episode,
                        it.date.toString().substringBefore("-").toIntOrNull(),
                        isAnime,
                        isBollywood,
                        isAsian
                    ).toJson()
                ) {
                    this.name = it.title
                    this.season = it.season
                    this.episode = it.episode
                    this.description = it.description
                    this.posterUrl = getPosterUrl(it.img, "episode") ?: "https://wsrv.nl/?url=https://simkl.in/update_m_alert.jpg"
                    addDate(it.date)
                }
            }

            return newTvSeriesLoadResponse("${en_title}", url,if(isAnime) TvType.Anime else TvType.TvSeries, episodes) {
                this.posterUrl = getPosterUrl(json.poster, "poster")
                this.backgroundPosterUrl = getPosterUrl(json.fanart, "fanart")
                this.plot = json.overview
                this.tags = genres
                this.duration = json.runtime?.toIntOrNull()
                this.rating = json.ratings?.simkl?.rating.toString().toRatingInt()
                this.year = json.year
                this.recommendations = recommendations
                this.contentRating = json.certification
                this.addSimklId(simklId.toInt())
                this.addAniListId(json.ids?.anilist?.toIntOrNull())
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LoadLinksData>(data)

        if(res.isAnime) runAnimeInvokers(res, subtitleCallback, callback)
        else runGeneralInvokers(res, subtitleCallback, callback)

        return true
    }

    private suspend fun runGeneralInvokers(
        res: LoadLinksData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runAllAsync(
            { invokeFilmModu(
    title = res.title,            // film başlığı
    year = res.year,              // opsiyonel yıl kontrolü
    season = null,                // FilmModu diziler için null
    episode = null,               // FilmModu diziler için null
    subtitleCallback = subtitleCallback,
    callback = callback
) },
			
			{ invokeTorrentio(res.imdbId, res.season, res.episode, callback) },
            { if(!res.isBollywood) invokeVegamovies("VegaMovies", res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { if(res.isBollywood) invokeVegamovies("RogMovies", res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeNetflix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokePrimeVideo(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { if(res.season == null) invokeStreamify(res.imdbId, callback) },
            { invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback) },
            { if(res.isBollywood) invokeTopMovies(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { if(!res.isBollywood) invokeMoviesmod(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { if(res.isAsian && res.season != null) invokeStreamAsia(res.title, "kdhd", res.season, res.episode, subtitleCallback, callback) },
            { invokeMoviesdrive(res.title, res.imdbId ,res.season, res.episode, subtitleCallback, callback) },
            { if(!res.isAnime) invokeAsiaflix(res.title, res.season, res.episode, res.airedYear, subtitleCallback, callback) },
            { invokeCinemaluxe(res.title, res.year, res.season, res.episode, callback, subtitleCallback) },
            { invokeSkymovies(res.title, res.airedYear, res.episode, subtitleCallback, callback) },
            { invokeHdmovie2(res.title, res.airedYear, res.episode, subtitleCallback, callback) },
            { invokeFlixhq(res.title, res.season, res.episode, subtitleCallback, callback) },
            { invokeBollyflix(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeMovies4u(res.imdbId, res.en_title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { if(!res.isBollywood) invokeHindmoviez("HindMoviez", res.imdbId, res.season, res.episode, callback) },
            // { if (res.isBollywood) invokeHindmoviez("JaduMovies", res.imdbId, res.season, res.episode, callback) },
            { invokeW4U(res.en_title, res.year, res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeWYZIESubs(res.imdbId, res.season, res.episode, subtitleCallback) },
            { invokePrimebox(res.en_title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokePrimeWire(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invoke2embed(res.imdbId, res.season, res.episode, callback) },
            { invokeSoaper(res.imdbId, res.tmdbId, res.en_title, res.season, res.episode, subtitleCallback, callback) },
            { invokePhoenix(res.en_title, res.imdbId, res.tmdbId, res.year, res.season, res.episode, callback) },
            { invokeTom(res.tmdbId, res.season, res.episode, callback, subtitleCallback) },
            { invokePrimenet(res.tmdbId, res.season, res.episode, callback) },
            { invokePlayer4U(res.en_title, res.season, res.episode, res.airedYear, callback) },
            { invokeThepiratebay(res.imdbId, res.season, res.episode, callback) },
            // { if (!res.isAnime) invokeVidJoy(res.tmdbId, res.season, res.episode, callback) },
            { invokeProtonmovies(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeAllmovieland(res.imdbId, res.season, res.episode, callback) },
            { if(res.season == null) invokeMostraguarda(res.imdbId, subtitleCallback, callback) },
            { if(!res.isBollywood ) invokeMoviesflix("Moviesflix", res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { if(res.isBollywood) invokeMoviesflix("Hdmoviesflix", res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { if(!res.isBollywood) invokeUhdmovies(res.en_title, res.year, res.season, res.episode, callback, subtitleCallback) },
            { if(!res.isBollywood) invoke4khdhub(res.en_title, res.year, res.season, res.episode, subtitleCallback, callback) }
        )
    }

    private suspend fun runAnimeInvokers(
        res: LoadLinksData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val imdbId = try {
            res.imdbId ?: extractImdbId(res.kitsuId)
        } catch (e: Exception) {
            null
        }

        val (imdbTitle, tmdbId) = try {
            extractNameAndTMDBId(imdbId) ?: Pair(res.en_title, res.tmdbId)
        } catch (e: Exception) {
            Pair(res.en_title, res.tmdbId)
        }

        runAllAsync(
            { invokeFilmModu(
    title = imdbTitle,        // film başlığı
    year = res.year,          // opsiyonel yıl kontrolü
    season = null,            // FilmModu dizi mantığı yoksa null
    episode = null,           // FilmModu dizi mantığı yoksa null
    subtitleCallback = subtitleCallback,
    callback = callback
) },
			
			{ invokeAnimes(res.malId, res.anilistId, res.episode, res.year, "kitsu", subtitleCallback, callback) },
            { invokeSudatchi(res.anilistId, res.episode, subtitleCallback, callback) },
            { invokeGojo(res.anilistId, res.episode, callback) },
            { invokeAnimeparadise(res.title, res.malId, res.episode, subtitleCallback, callback) },
            { invokeAllanime(res.title, res.year, res.episode, subtitleCallback, callback) },
            { invokeAnizone(res.title, res.episode, subtitleCallback, callback) },
            { invokeTokyoInsider(res.title, res.episode, subtitleCallback, callback) },
            { invokeTorrentio(imdbId, res.season, res.episode, callback) },
            { invokeVegamovies("VegaMovies", imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeNetflix(imdbTitle, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokePrimeVideo(imdbTitle, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeMultimovies(imdbTitle, res.season, res.episode, subtitleCallback, callback) },
            { invokeMoviesmod(imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeBollyflix(imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeMovies4u(imdbId, imdbTitle, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeWYZIESubs(imdbId, res.season, res.episode, subtitleCallback) },
            { invokePrimebox(imdbTitle, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokePrimeWire(imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeSoaper(imdbId, tmdbId, imdbTitle, res.season, res.episode, subtitleCallback, callback) },
            { invokePhoenix(imdbTitle, imdbId, tmdbId, res.year, res.season, res.episode, callback) },
            { invokeTom(tmdbId, res.season, res.episode, callback, subtitleCallback) },
            { invokePrimenet(tmdbId, res.season, res.episode, callback) },
            { invokePlayer4U(imdbTitle, res.season, res.episode, res.airedYear, callback) },
            { invokeThepiratebay(imdbId, res.season, res.episode, callback) },
            { invokeProtonmovies(imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeAllmovieland(imdbId, res.season, res.episode, callback) },
            { invokeUhdmovies(imdbTitle, res.year, res.season, res.episode, callback, subtitleCallback) },
            { invoke4khdhub(imdbTitle, res.year, res.season, res.episode, subtitleCallback, callback) }
        )
    }

    data class SimklResponse (
        var title                 : String?                         = null,
        var en_title              : String?                         = null,
        var title_en              : String?                         = null,
        var year                  : Int?                            = null,
        var type                  : String?                         = null,
        var url                   : String?                         = null,
        var poster                : String?                         = null,
        var fanart                : String?                         = null,
        var ids                   : Ids?                            = Ids(),
        var release_date          : String?                         = null,
        var ratings               : Ratings                         = Ratings(),
        var country               : String?                         = null,
        var certification         : String?                         = null,
        var runtime               : String?                         = null,
        var status                : String?                         = null,
        var total_episodes        : Int?                            = null,
        var network               : String?                         = null,
        var overview              : String?                         = null,
        var anime_type            : String?                         = null,
        var season                : String?                         = null,
        var endpoint_type         : String?                         = null,
        var genres                : ArrayList<String>               = arrayListOf(),
        var users_recommendations : ArrayList<UsersRecommendations> = arrayListOf()
    )

    data class Ids (
        var simkl_id : Int?    = null,
        var tmdb     : String? = null,
        var imdb     : String? = null,
        var slug     : String? = null,
        var mal      : String? = null,
        var anilist  : String? = null,
        var kitsu    : String? = null,
        var anidb    : String? = null,
        var simkl    : Int?    = null
    )

    data class Ratings (
        var simkl : Simkl? = Simkl(),
    )

    data class Simkl (
        var rating : Double? = null,
        var votes  : Int?    = null
    )

    data class UsersRecommendations (
        var title  : String? = null,
        var year   : Int?    = null,
        var poster : String? = null,
        var type   : String? = null,
        var ids    : Ids     = Ids()
    )

    data class Episodes (
        var title       : String?  = null,
        var season      : Int?     = null,
        var episode     : Int?     = null,
        var type        : String?  = null,
        var description : String?  = null,
        var aired       : Boolean? = null,
        var img         : String?  = null,
        var date        : String?  = null,
    )
    data class LoadLinksData(
        val title       : String? = null,
        val en_title    : String? = null,
        val tvtype      : String? = null,
        val simklId     : Int?    = null,
        val imdbId      : String? = null,
        val tmdbId      : Int?    = null,
        val year        : Int?    = null,
        val anilistId   : Int?    = null,
        val malId       : Int?    = null,
        val kitsuId     : Int?    = null,
        val season      : Int?    = null,
        val episode     : Int?    = null,
        val airedYear   : Int?    = null,
        val isAnime     : Boolean = false,
        val isBollywood : Boolean = false,
        val isAsian     : Boolean = false
    )
}
