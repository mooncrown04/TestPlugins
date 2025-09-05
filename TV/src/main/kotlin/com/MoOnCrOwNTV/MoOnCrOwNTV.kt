package com.MoOnCrOwNTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/r4p9v7g76ikwt8zsyuhyn/sile.m3u?rlkey=esnalbpm4kblxgkvym51gjokm"
    override var name = "MoOnCrOwNTV"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val home = listOf(
            HomePageList(
                "Popüler",
                listOf(
                    newMovieSearchResponse("Örnek Film", "$mainUrl/movie/123", TvType.Movie) {
                        this.posterUrl = "https://example.com/image.jpg"
                        this.year = 2023
                    }
                )
            )
        )
        return HomePageResponse(home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf(
            newMovieSearchResponse("Arama: $query", "$mainUrl/search?q=$query", TvType.Movie) {
                this.posterUrl = "https://example.com/image.jpg"
                this.year = 2023
            }
        )
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse("Örnek Film", url, TvType.Movie, url) {
            this.posterUrl = "https://example.com/image.jpg"
            this.year = 2023
            this.plot = "Bu bir örnek açıklamadır."
            this.contentRating = "PG-13"
        }
    }
}
