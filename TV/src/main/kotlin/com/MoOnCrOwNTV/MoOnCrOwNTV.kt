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
                    newMovieSearchResponse(
                        name = "Örnek Film",
                        url = "$mainUrl/movie/123",
                        apiName = this.name,
                        type = TvType.Movie,
                        posterUrl = "https://example.com/image.jpg",
                        year = 2023
                    )
                )
            )
        )
        return HomePageResponse(home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf(
            newMovieSearchResponse(
                name = "Arama: $query",
                url = "$mainUrl/search?q=$query",
                apiName = this.name,
                type = TvType.Movie,
                posterUrl = "https://example.com/image.jpg",
                year = 2023
            )
        )
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse(
            name = "Örnek Film",
            url = url,
            apiName = this.name,
            type = TvType.Movie,
            dataUrl = url,
            posterUrl = "https://example.com/image.jpg",
            year = 2023,
            plot = "Bu bir örnek açıklamadır.",
            contentRating = "PG-13"
        )
    }
}
