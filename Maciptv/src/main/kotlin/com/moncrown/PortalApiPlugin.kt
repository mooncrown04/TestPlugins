package com.mooncrown

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PortalApiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PortalApiProvider())
    }
}