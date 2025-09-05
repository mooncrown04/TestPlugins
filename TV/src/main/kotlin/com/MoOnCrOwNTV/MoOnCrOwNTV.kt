package com.MoOnCrOwNTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MoOnCrOwNTV : MainAPI() {
    override var mainUrl = "https://dl.dropbox.com/scl/fi/r4p9v7g76ikwt8zsyuhyn/sile.m3u?rlkey=esnalbpm4kblxgkvym51gjokm"
    override var name = "MoOnCrOwNTV"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override fun getMainPage(): HomePageResponse {
        val home = listOf(
            HomePageList(
                "Popüler",
                listOf(
                    MovieSearchResponse(
                        "Örnek Film",
                        "$mainUrl/movie/123",
                        this.name,
                        TvType.Movie,
                        2023,
                        "https://example.com/image.jpg"
                    )
                )
            )
        )
        return HomePageResponse(home)
    }

    override suspend fun load(url: String): LoadResponse {
        return MovieLoadResponse(
            "
