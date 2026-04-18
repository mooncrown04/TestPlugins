package com.mooncrown

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CinemaCityPlugin: Plugin() {
    override fun load(context: Context) {
        // Eklenti adını ve ana sınıfı burada belirtiyoruz
        registerMainAPI(CinemaCity(this))
    }
}