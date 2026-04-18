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

    // --- 1. ANA SAYFA: TAM KATALOG YAPISI ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeLists = mutableListOf<HomePageList>()
        
        // Gösterilecek Kategoriler
        val categories = listOf(
            Pair("Haftalık Trendler", "trending/all/week"),
            Pair("Sinemalarda", "movie/now_playing"),
            Pair("Popüler Diziler", "tv/popular"),
            Pair("En Çok Oy Alan Filmler", "movie/top_rated"),
            Pair("Yakında Gelecekler", "movie/upcoming")
        )

        categories.forEach { (title, endpoint) ->
            try {
                val url = "https://api.themoviedb.org/3/$endpoint?api_key=$tmdbKey&language=tr-TR&page=1"
                val res = app.get(url).parsedSafe<TmdbListResponse>()
                
                val items = res?.results?.mapNotNull {
                    // Sadece film ve dizileri al, kişileri (person) atla
                    val type = if (endpoint.contains("tv")) "tv" else (it.media_type ?: "movie")
                    if (type == "person") return@mapNotNull null
                    
                    newMovieSearchResponse(
                        it.title ?: it.name ?: return@mapNotNull null,
                        "tmdb|${it.id}|$type",
                        if (type == "tv") TvType.TvSeries else TvType.Movie
                    ) {
                        this.posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
                    }
                }
                
                if (!items.isNullOrEmpty()) {
                    homeLists.add(HomePageList(title, items, isHorizontalImages = false))
                }
            } catch (e: Exception) { /* Bir liste hata verirse diğerlerini bozma */ }
        }

        return newHomePageResponse(homeLists, hasNext = false)
    }

    // --- 2. ARAMA FONKSİYONU ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbKey&language=tr-TR&query=$query"
        val res = app.get(url).parsedSafe<TmdbListResponse>()
        
        return res?.results?.mapNotNull {
            val type = it.media_type ?: "movie"
            if (type == "person") return@mapNotNull null
            
            newMovieSearchResponse(
                it.title ?: it.name ?: return@mapNotNull null,
                "tmdb|${it.id}|$type",
                if (type == "tv") TvType.TvSeries else TvType.Movie
            ) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
            }
        } ?: emptyList()
    }

    // --- 3. DETAY YÜKLEME (ID SORUNU ÇÖZÜLDÜ) ---
    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|")
        if (parts.size < 3) throw ErrorLoadingException("Hatalı veri iletildi")
        
        val tmdbId = parts[1]
        val type = parts[2]

        val detailsUrl = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$tmdbKey&language=tr-TR&append_to_response=external_ids"
        val d = app.get(detailsUrl).parsedSafe<TmdbDetailResponse>() ?: throw ErrorLoadingException("TMDB detayları çekilemedi")
        
        val imdbId = d.external_ids?.imdb_id ?: throw ErrorLoadingException("İçeriğin IMDB ID'si (tt...) yok")

        return if (type == "movie") {
            newMovieLoadResponse(d.title ?: d.name ?: "Film", url, TvType.Movie, "vid|$imdbId") {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
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
                    })
                }
            }
            newTvSeriesLoadResponse(d.name ?: d.title ?: "Dizi", url, TvType.TvSeries, episodes) {
                this.posterUrl = "https://image.tmdb.org/t/p/w500${d.poster_path}"
                this.plot = d.overview
                addImdbId(imdbId)
            }
        }
    }

    // --- 4. LİNK OLUŞTURMA (VİDMODY MANTIĞI) ---
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.split("|")
        val imdbId = parts[1]
        
        val targetUrl = if (parts.size == 2) {
            "https://vidmody.com/vs/$imdbId"
        } else {
            val s = parts[2]
            val e = String.format("%02d", parts[3].toInt())
            "https://vidmody.com/vs/$imdbId/s$s/e$e"
        }

        callback.invoke(
            ExtractorLink(
                this.name, "Vidmody", targetUrl, "https://vidmody.com/",
                Qualities.P1080.value, ExtractorLinkType.M3U8
            )
        )
        return true
    }

    // DATA MODELLERİ
    data class TmdbListResponse(val results: List<TmdbResult>?)
    data class TmdbResult(val id: Int?, val title: String?, val name: String?, val poster_path: String?, val media_type: String?)
    data class TmdbDetailResponse(val title: String?, val name: String?, val overview: String?, val poster_path: String?, val external_ids: ExternalIds?, val seasons: List<TmdbSeason>?)
    data class ExternalIds(val imdb_id: String?)
    data class TmdbSeason(val season_number: Int?, val episode_count: Int?)
}
