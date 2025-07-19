package com.film

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.film.Film
@CloudstreamPlugin
class FilmPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the following list manually.
        registerMainAPI(Film(context, null))
    }
}
