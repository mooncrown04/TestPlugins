package com.example

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmModuPlugin: Plugin() {
    override fun load(context: Context) {
        // Burada MooncrownLoader'ı çağırırken paket ismi uyuşmalı
        registerMainAPI(MooncrownLoader()) 
    }
}
