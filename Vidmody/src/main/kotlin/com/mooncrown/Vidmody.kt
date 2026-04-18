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

    // Ana Sayfa: Eklenti açıldığı an posterleri burası basar
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$tmdbKey&language=tr-TR&page=$page"
        val res = app.get(url).parsed<TmdbListResponse>()
        
        val homeItems = res.results.mapNotNull {
            newMovieSearchResponse(
                it.title ?: it.name ?: return@mapNotNull null, 
                "tmdb_${it.id}_${it.media_type ?: "movie"}", 
                if (it.media_type == "tv") TvType.TvSeries else TvType.Movie
            ) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
            }
        }
        return newHomePageResponse(listOf(HomePageList("Popüler İçerikler", homeItems)), true)
    }

    // Arama: Manuel arama yapıldığında TMDB'yi kullanır
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbKey&language=tr-TR&query=$query"
        val res = app.get(url).parsed<TmdbListResponse>()
        
        return res.results.mapNotNull {
            newMovieSearchResponse(
                it.title ?: it.name ?: return@mapNotNull null, 
                "tmdb_${it.id}_${it.media_type ?: "movie"}", 
                if (it.media_type == "tv") TvType.TvSeries else TvType.Movie
            ) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
            }
        }
    }

    // Yükleme: Afişe tıklandığında detayları ve sezonları hazırlar
    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("_")
        val tmdbId = parts[1]
        val mediaType = parts[2]

        val detailsUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=$tmdbKey&language=tr-TR&append_to_response=external_ids"
        val d = app.get(detailsUrl).parsed<TmdbDetailResponse>()
        val imdbId = d.external_ids?.imdb_id ?: throw ErrorLoadingException("IMDB ID bulunamadı")

        return if (mediaType == "movie") {
            newMovieLoadResponse(d.title ?: "", url, TvType.Movie, "vidmody_$imdbId") {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                addImdbId(imdbId)
            }
        } else {
            val episodes = mutableListOf<Episode>()
            d.seasons?.filter { (it.season_number ?: 0) > 0 }?.forEach { season ->
                for (i in 1..(season.episode_count ?: 0)) {
                    episodes.add(newEpisode("vidmody_${imdbId}_${season.season_number}_$i") {
                        this.name = "Bölüm $i"
                        this.season = season.season_number
                        this.episode = i
                    })
                }
            }
            newTvSeriesLoadResponse(d.name ?: "", url, TvType.TvSeries, episodes) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                addImdbId(imdbId)
            }
        }
    }

    // Link: Oynat tuşuna basıldığında Vidmody URL'sini üretir
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.split("_")
        val imdbId = parts[1]
        
        val targetUrl = if (parts.size == 2) {
            "https://vidmody.com/vs/$imdbId"
        } else {
            val s = parts[2]
            val e = String.format("%02d", parts[3].toInt())
            "https://vidmody.com/vs/$imdbId/s$s/$e"
        }

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = "Vidmody",
                url = targetUrl,
                referer = "https://vidmody.com/",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }

    // Veri Sınıfları
    data class TmdbListResponse(val results: List<TmdbResult>)
    data class TmdbResult(val id: Int, val title: String?, val name: String?, val poster_path: String?, val media_type: String?)
    data class TmdbDetailResponse(val title: String?, val name: String?, val overview: String?, val poster_path: String?, val external_ids: ExternalIds?, val seasons: List<TmdbSeason>?)
    data class ExternalIds(val imdb_id: String?)
    data class TmdbSeason(val season_number: Int?, val episode_count: Int?, val name: String?)
}