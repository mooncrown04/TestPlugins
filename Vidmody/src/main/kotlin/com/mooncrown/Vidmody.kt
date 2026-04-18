package com.mooncrown

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId

class Vidmody(private val plugin: VidmodyPlugin) : MainAPI() {
    override var name = "Vidmody"
    override var mainUrl = "https://vidmody.com"
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
            Pair("Netflix Dizileri", "discover/tv?with_networks=213"),
            Pair("Sinemalarda", "movie/now_playing")
        )

        categories.forEach { (title, endpoint) ->
            try {
                val sep = if (endpoint.contains("?")) "&" else "?"
                val url = "https://api.themoviedb.org/3/$endpoint${sep}api_key=$tmdbKey&language=tr-TR"
                val res = app.get(url).parsedSafe<TmdbListResponse>()
                val items = res?.results?.mapNotNull {
                    val type = if (endpoint.contains("tv")) "tv" else (it.media_type ?: "movie")
                    newMovieSearchResponse(it.title ?: it.name ?: return@mapNotNull null, "tmdb|${it.id}|$type|$title", if (type == "tv") TvType.TvSeries else TvType.Movie) {
                        this.posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
                        this.year = (it.release_date ?: it.first_air_date)?.take(4)?.toIntOrNull()
                    }
                }
                if (!items.isNullOrEmpty()) homeLists.add(HomePageList(title, items))
            } catch (e: Exception) { }
        }
        return newHomePageResponse(homeLists, false)
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|")
        val tmdbId = parts[1]
        val type = parts[2]
        val catName = if (parts.size > 3) parts[3] else "MoOnCrOwN"

        val d = app.get("https://api.themoviedb.org/3/$type/$tmdbId?api_key=$tmdbKey&language=tr-TR&append_to_response=external_ids,credits").parsedSafe<TmdbDetailResponse>() ?: throw ErrorLoadingException("Hata")
        val imdbId = d.external_ids?.imdb_id ?: throw ErrorLoadingException("No IMDB")

        // Actor: Hata vermemesi için sadece isim ve resim gönderiyoruz
        val actorList = mutableListOf<ActorData>()
        actorList.add(ActorData(Actor("MoOnCrOwN", "https://github.com/mooncrown04.png")))
        actorList.add(ActorData(Actor("Yazılım Amelesi", "https://github.com/yazilimamelesi.png")))

        d.credits?.cast?.take(10)?.forEach {
            actorList.add(ActorData(Actor(it.name ?: "Bilinmeyen", if (it.profile_path != null) "https://image.tmdb.org/t/p/w185${it.profile_path}" else null)))
        }

        val tags = mutableListOf("MoOnCrOwN", catName).apply { d.genres?.forEach { it.name?.let { add(it) } } }
        
        // SCORE DÜZELTMESİ: Hata mesajına göre Score sadece tek bir Int alıyor
        val ratingInt = d.vote_average?.times(10)?.toInt() ?: 0
        val tmdbScore = Score(ratingInt) 

        return if (type == "movie") {
            newMovieLoadResponse(d.title ?: d.name ?: "Film", url, TvType.Movie, "vid|$imdbId") {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                this.year = (d.release_date ?: d.first_air_date)?.take(4)?.toIntOrNull()
                this.tags = tags
                this.score = tmdbScore
                this.actors = actorList
                addImdbId(imdbId)
            }
        } else {
            val episodes = d.seasons?.filter { (it.season_number ?: 0) > 0 }?.flatMap { s ->
                (1..(s.episode_count ?: 0)).map { i ->
                    newEpisode("vid|$imdbId|${s.season_number}|$i") {
                        this.name = "Bölüm $i"
                        this.season = s.season_number
                        this.episode = i
                        this.description = "S${s.season_number} E$i | MoOnCrOwN & Yazılım Amelesi"
                    }
                }
            } ?: emptyList()
            newTvSeriesLoadResponse(d.name ?: d.title ?: "Dizi", url, TvType.TvSeries, episodes) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                this.year = (d.release_date ?: d.first_air_date)?.take(4)?.toIntOrNull()
                this.tags = tags
                this.score = tmdbScore
                this.actors = actorList
                addImdbId(imdbId)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.split("|")
        val link = if (parts.size == 2) "https://vidmody.com/vs/${parts[1]}" else "https://vidmody.com/vs/${parts[1]}/s${parts[2]}/e${String.format("%02d", parts[3].toInt())}"
        
        callback.invoke(
            newExtractorLink(this.name, "Vidmody", link, ExtractorLinkType.M3U8) {
                this.quality = Qualities.P1080.value
                this.referer = "https://vidmody.com/"
            }
        )
        return true
    }

    data class TmdbListResponse(val results: List<TmdbResult>?)
    data class TmdbResult(val id: Int?, val title: String?, val name: String?, val poster_path: String?, val media_type: String?, val release_date: String?, val first_air_date: String?)
    data class TmdbDetailResponse(val title: String?, val name: String?, val overview: String?, val poster_path: String?, val external_ids: ExternalIds?, val seasons: List<TmdbSeason>?, val release_date: String?, val first_air_date: String?, val genres: List<Genre>?, val credits: Credits?, val vote_average: Double?)
    data class ExternalIds(val imdb_id: String?)
    data class TmdbSeason(val season_number: Int?, val episode_count: Int?)
    data class Genre(val name: String?)
    data class Credits(val cast: List<TmdbCast>?)
    data class TmdbCast(val name: String?, val profile_path: String?)
}
