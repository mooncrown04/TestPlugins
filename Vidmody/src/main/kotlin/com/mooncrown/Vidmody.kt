package com.mooncrown

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.Score

class Vidmody(private val plugin: VidmodyPlugin) : MainAPI() {
    override var name = "vixolity"
    override var mainUrl = "https://ha.vixolity.com"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbKey = "500330721680edb6d5f7f12ba7cd9023"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeLists = mutableListOf<HomePageList>()
        val categories = listOf(
            Pair("Haftalık Trendler", "trending/all/week"),
            Pair("Popüler Türk Yapımları", "discover/movie?with_original_language=tr&sort_by=popularity.desc"),
            Pair("Sinemalarda", "movie/now_playing"),
            Pair("Popüler Diziler", "tv/popular"),
            Pair("Korku ve Gerilim", "discover/movie?with_genres=27,53"),
            Pair("Netflix Dizileri", "discover/tv?with_networks=213"),
            Pair("Popüler Kore Dizileri", "discover/tv?with_original_language=ko"),
            Pair("Marvel Dünyası", "discover/movie?with_companies=420&sort_by=release_date.desc"),
            Pair("Disney+ Orijinalleri", "discover/tv?with_networks=2739")
        )

        categories.forEach { (title, endpoint) ->
            try {
                val sep = if (endpoint.contains("?")) "&" else "?"
                val url = "https://api.themoviedb.org/3/$endpoint${sep}api_key=$tmdbKey&language=tr-TR&page=$page"
                val res = app.get(url).parsedSafe<TmdbListResponse>()

                val items = res?.results?.mapNotNull { item ->
                    val id = item.id ?: return@mapNotNull null
                    val itemTitle = item.title ?: item.name ?: return@mapNotNull null
                    val isTv = endpoint.contains("tv") || item.media_type == "tv"
                    val typeStr = if (isTv) "tv" else "movie"
                    val poster = item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }

                    if (isTv) {
                        newTvSeriesSearchResponse(itemTitle, "tmdb|$id|$typeStr|$title", TvType.TvSeries) {
                            this.posterUrl = poster
                            this.year = (item.release_date ?: item.first_air_date)?.take(4)?.toIntOrNull()
                            this.score = item.vote_average?.let { v -> Score.from10(v) }
                        }
                    } else {
                        newMovieSearchResponse(itemTitle, "tmdb|$id|$typeStr|$title", TvType.Movie) {
                            this.posterUrl = poster
                            this.year = (item.release_date ?: item.first_air_date)?.take(4)?.toIntOrNull()
                            this.score = item.vote_average?.let { v -> Score.from10(v) }
                        }
                    }
                }
                if (!items.isNullOrEmpty()) homeLists.add(HomePageList(title, items))
            } catch (e: Exception) { }
        }
        return newHomePageResponse(homeLists, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        listOf("movie", "tv").forEach { type ->
            try {
                val url = "https://api.themoviedb.org/3/search/$type?api_key=$tmdbKey&query=$query&language=tr-TR"
                val res = app.get(url).parsedSafe<TmdbListResponse>()
                res?.results?.forEach { item ->
                    val id = item.id ?: return@forEach
                    val itemTitle = item.title ?: item.name ?: return@forEach
                    val poster = item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }

                    if (type == "tv") {
                        results.add(newTvSeriesSearchResponse(itemTitle, "tmdb|$id|$type", TvType.TvSeries) {
                            this.posterUrl = poster
                            this.year = (item.release_date ?: item.first_air_date)?.take(4)?.toIntOrNull()
                        })
                    } else {
                        results.add(newMovieSearchResponse(itemTitle, "tmdb|$id|$type", TvType.Movie) {
                            this.posterUrl = poster
                            this.year = (item.release_date ?: item.first_air_date)?.take(4)?.toIntOrNull()
                        })
                    }
                }
            } catch (e: Exception) { }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|")
        val tmdbId = parts.getOrNull(1) ?: throw ErrorLoadingException("Geçersiz TMDB ID")
        val type = parts.getOrNull(2) ?: "movie"
        val catName = parts.getOrNull(3) ?: "MoOnCrOwN"

        // append_to_response içerisine recommendations eklendi
        val detailsUrl = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$tmdbKey&language=tr-TR&append_to_response=external_ids,credits,videos,recommendations"
        val d = app.get(detailsUrl).parsedSafe<TmdbDetailResponse>() ?: throw ErrorLoadingException("Detay Hatası")
        val imdbId = d.external_ids?.imdb_id ?: throw ErrorLoadingException("IMDB ID Bulunamadı")

        val actorsList = mutableListOf<ActorData>()

        // 1. Geliştirici İmzası
        actorsList.add(ActorData(Actor("MoOnCrOwN", "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"), roleString = "Yazılım Amelesi"))

        // 2. Dinamik Yapım Kartı
        actorsList.add(ActorData(Actor(d.name ?: d.title ?: "Bilgi", "https://image.tmdb.org/t/p/w500${d.poster_path}"), roleString = d.genres?.firstOrNull()?.name ?: "Kategori"))

        // 3. Filtreli Oyuncular
        d.credits?.cast?.take(20)?.forEach { castItem ->
            if (!castItem.character.isNullOrBlank()) {
                actorsList.add(ActorData(Actor(castItem.name ?: "Oyuncu", castItem.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }), roleString = castItem.character))
            }
        }

        // Öneriler / Benzer Yapımlar (Recommendations) İşleme
        val recommendationList = d.recommendations?.results?.mapNotNull { rec ->
            val recId = rec.id ?: return@mapNotNull null
            val recTitle = rec.title ?: rec.name ?: return@mapNotNull null
            val recIsTv = rec.media_type == "tv" || type == "tv"
            val recTypeStr = if (recIsTv) "tv" else "movie"
            val recPoster = rec.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }

            if (recIsTv) {
                newTvSeriesSearchResponse(recTitle, "tmdb|$recId|$recTypeStr|Tavsiye", TvType.TvSeries) {
                    this.posterUrl = recPoster
                    this.year = (rec.release_date ?: rec.first_air_date)?.take(4)?.toIntOrNull()
                }
            } else {
                newMovieSearchResponse(recTitle, "tmdb|$recId|$recTypeStr|Tavsiye", TvType.Movie) {
                    this.posterUrl = recPoster
                    this.year = (rec.release_date ?: rec.first_air_date)?.take(4)?.toIntOrNull()
                }
            }
        }

        // Ülke, Türler ve Production Companies (Yapım Şirketleri) etiketlere ekleniyor
        val countryName = d.production_countries?.firstOrNull()?.name ?: d.production_countries?.firstOrNull()?.iso_3166_1
        val tags = mutableListOf("MoOnCrOwN", catName).apply {
            countryName?.let { add(it) }
            d.genres?.forEach { it.name?.let { g -> add(g) } }
            d.production_companies?.forEach { company -> company.name?.let { add(it) } }
        }
        val finalScore = d.vote_average?.let { Score.from10(it) }

        // Fragman Tanımlaması
        val trailerKey = d.videos?.results?.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }?.key
        val trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }

        return if (type == "movie") {
            newMovieLoadResponse(d.title ?: d.name ?: "Film", url, TvType.Movie, "vid|$imdbId") {
                this.posterUrl = d.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.backgroundPosterUrl = d.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                this.plot = d.overview
                this.year = (d.release_date ?: d.first_air_date)?.take(4)?.toIntOrNull()
                this.tags = tags
                this.score = finalScore
                this.duration = d.runtime
                this.actors = actorsList
                this.recommendations = recommendationList
                
                trailerUrl?.let { addTrailer(it) }
                addImdbId(imdbId)
            }
        } else {
            val epList = mutableListOf<Episode>()
            d.seasons?.filter { (it.season_number ?: 0) > 0 }?.forEach { s ->
                try {
                    val sNum = s.season_number ?: return@forEach
                    val sUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$sNum?api_key=$tmdbKey&language=tr-TR"
                    val sData = app.get(sUrl).parsedSafe<TmdbSeasonResponse>()
                    sData?.episodes?.forEach { ep ->
                        val epNum = ep.episode_number ?: return@forEach
                        epList.add(newEpisode("vid|$imdbId|$sNum|$epNum") {
                            this.name = ep.name ?: "Bölüm $epNum"
                            this.season = sNum
                            this.episode = epNum
                            this.description = ep.overview
                            this.posterUrl = ep.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                            this.addDate(ep.air_date)
                        })
                    }
                } catch (e: Exception) { }
            }
            newTvSeriesLoadResponse(d.name ?: d.title ?: "Dizi", url, TvType.TvSeries, epList) {
                this.posterUrl = d.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.backgroundPosterUrl = d.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                this.plot = d.overview
                this.year = (d.release_date ?: d.first_air_date)?.take(4)?.toIntOrNull()
                this.tags = tags
                this.score = finalScore
                this.actors = actorsList
                this.recommendations = recommendationList

                this.showStatus = when (d.status) {
                    "Returning Series" -> ShowStatus.Ongoing
                    "Ended", "Canceled" -> ShowStatus.Completed
                    else -> null
                }

                trailerUrl?.let { addTrailer(it) }
                addImdbId(imdbId)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.split("|")
        val imdbId = parts.getOrNull(1) ?: return false

        val link = if (parts.size <= 2) {
            "${this.mainUrl}/vs/$imdbId"
        } else {
            val season = parts.getOrNull(2)?.toIntOrNull() ?: 1
            val episode = parts.getOrNull(3)?.toIntOrNull() ?: 1
            val formattedEp = String.format("%02d", episode)
            "${this.mainUrl}/vs/$imdbId/s$season/e$formattedEp"
        }

        val subUrl = "${this.mainUrl}/subtitles/$imdbId/tr.vtt"
        subtitleCallback.invoke(
            SubtitleFile(
                lang = "Türkçe",
                url = subUrl
            )
        )

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "[ha.vixolity.com]",
                url = link,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "${this@Vidmody.mainUrl}/"
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    data class TmdbListResponse(val results: List<TmdbResult>?)
    data class TmdbResult(
        val id: Int?,
        val title: String?,
        val name: String?,
        val poster_path: String?,
        val media_type: String?,
        val release_date: String?,
        val first_air_date: String?,
        val vote_average: Double?
    )
    data class TmdbDetailResponse(
        val title: String?,
        val name: String?,
        val overview: String?,
        val poster_path: String?,
        val backdrop_path: String?,
        val external_ids: ExternalIds?,
        val seasons: List<TmdbSeason>?,
        val release_date: String?,
        val first_air_date: String?,
        val genres: List<Genre>?,
        val credits: Credits?,
        val vote_average: Double?,
        val runtime: Int?,
        val status: String?,
        val videos: TmdbVideos?,
        val production_countries: List<ProductionCountry>?,
        val production_companies: List<ProductionCompany>?,
        val recommendations: TmdbListResponse?
    )
    data class TmdbSeasonResponse(val episodes: List<TmdbEpisode>?)
    data class TmdbEpisode(
        val name: String?,
        val overview: String?,
        val episode_number: Int?,
        val still_path: String?,
        val air_date: String?
    )
    data class ExternalIds(val imdb_id: String?)
    data class TmdbSeason(val season_number: Int?, val episode_count: Int?)
    data class Genre(val name: String?)
    data class Credits(val cast: List<TmdbCast>?)
    data class TmdbCast(val name: String?, val character: String?, val profile_path: String?)
    data class TmdbVideos(val results: List<TmdbVideoResult>?)
    data class TmdbVideoResult(val key: String?, val site: String?, val type: String?)
    data class ProductionCountry(val iso_3166_1: String?, val name: String?)
    data class ProductionCompany(val id: Int?, val name: String?, val logo_path: String?, val origin_country: String?)
}
