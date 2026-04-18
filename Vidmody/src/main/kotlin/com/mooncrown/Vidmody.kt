package com.mooncrown

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorMetaData

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
            Pair("Sinemalarda", "movie/now_playing"),
            Pair("Popüler Diziler", "tv/popular"),     
            Pair("Korku ve Gerilim", "discover/movie?with_genres=27,53"), // Korku(27) ve Gerilim(53)
            Pair("Netflix Dizileri", "discover/tv?with_networks=213"),
            Pair("Popüler Kore Dizileri", "discover/tv?with_original_language=ko"),
            Pair("Bilim Kurgu Klasikleri", "discover/movie?with_genres=878&sort_by=vote_average.desc&vote_count.gte=500"),
            Pair("En Çok Oy Alan Filmler", "movie/top_rated")
        )

        categories.forEach { (title, endpoint) ->
            try {
                val separator = if (endpoint.contains("?")) "&" else "?"
                val url = "https://api.themoviedb.org/3/$endpoint${separator}api_key=$tmdbKey&language=tr-TR&page=1"
                val res = app.get(url).parsedSafe<TmdbListResponse>()
                
                val items = res?.results?.mapNotNull {
                    val type = if (endpoint.contains("tv")) "tv" else (it.media_type ?: "movie")
                    if (type == "person") return@mapNotNull null
                    
                    newMovieSearchResponse(
                        it.title ?: it.name ?: return@mapNotNull null,
                        "tmdb|${it.id}|$type|$title", // Başlığı tag için paslıyoruz
                        if (type == "tv") TvType.TvSeries else TvType.Movie
                    ) {
                        this.posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
                        this.year = (it.release_date ?: it.first_air_date)?.take(4)?.toIntOrNull()
                    }
                }
                if (!items.isNullOrEmpty()) homeLists.add(HomePageList(title, items))
            } catch (e: Exception) { }
        }
        return newHomePageResponse(homeLists, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|")
        val tmdbId = parts[1]
        val type = parts[2]
        val categoryName = if (parts.size > 3) parts[3] else "Genel"

        val detailsUrl = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$tmdbKey&language=tr-TR&append_to_response=external_ids,credits"
        val d = app.get(detailsUrl).parsedSafe<TmdbDetailResponse>() ?: throw ErrorLoadingException("Detaylar alınamadı")
        val imdbId = d.external_ids?.imdb_id ?: throw ErrorLoadingException("IMDB ID bulunamadı")

        // Özel Aktörler (En Başta Sen ve Yazılım Amelesi)
        val customActors = mutableListOf<Actor>()
        customActors.add(Actor("MoOnCrOwN", "Lead Developer", "https://github.com/mooncrown04.png"))
        customActors.add(Actor("Yazılım Amelesi", "Software Architect", "https://github.com/yazilimamelesi.png"))
        
        d.credits?.cast?.take(10)?.forEach {
            customActors.add(Actor(
                it.name ?: "Bilinmeyen",
                it.character ?: "Oyuncu",
                if (it.profile_path != null) "https://image.tmdb.org/t/p/w185${it.profile_path}" else "https://www.themoviedb.org/assets/2/v4/glyphicons/basic/glyphicons-basic-4-user-grey-d8fe57f12f2970e303494914c673410f92461944e554d3d3d63d898c8c519d55.svg"
            ))
        }

        // Taglar: İmza + Kategori + Türler
        val tags = mutableListOf("MoOnCrOwN", categoryName)
        d.genres?.forEach { it.name?.let { g -> tags.add(g) } }

        val rating = d.vote_average?.times(10)?.toInt()

        return if (type == "movie") {
            newMovieLoadResponse(d.title ?: d.name ?: "Film", url, TvType.Movie, "vid|$imdbId") {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                this.year = (d.release_date ?: d.first_air_date)?.take(4)?.toIntOrNull()
                this.tags = tags
                this.rating = rating
                this.actors = customActors
                addImdbId(imdbId)
            }
        } else {
            val episodes = mutableListOf<Episode>()
            d.seasons?.filter { (it.season_number ?: 0) > 0 }?.forEach { season ->
                for (i in 1..(season.episode_count ?: 0)) {
                    episodes.add(newEpisode("vid|$imdbId|${season.season_number}|$i") {
                        this.name = "Bölüm $i"
                        this.season = season.season_number
                        this.episode = i
                        this.description = "Sezon ${season.season_number} - Bölüm $i | MoOnCrOwN & Yazılım Amelesi"
                    })
                }
            }
            newTvSeriesLoadResponse(d.name ?: d.title ?: "Dizi", url, TvType.TvSeries, episodes) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                this.year = (d.release_date ?: d.first_air_date)?.take(4)?.toIntOrNull()
                this.tags = tags
                this.rating = rating
                this.actors = customActors
                addImdbId(imdbId)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.split("|")
        val imdbId = parts[1]
        val targetUrl = if (parts.size == 2) "https://vidmody.com/vs/$imdbId" 
                        else "https://vidmody.com/vs/$imdbId/s${parts[2]}/e${String.format("%02d", parts[3].toInt())}"

        callback.invoke(
            newExtractorLink(this.name, "Vidmody [TR]", targetUrl, ExtractorLinkType.M3U8) {
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
    data class TmdbCast(val name: String?, val character: String?, val profile_path: String?)
}
