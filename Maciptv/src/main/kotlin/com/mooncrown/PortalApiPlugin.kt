package com.mooncrown

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugin.Plugin
import com.lagradost.cloudstream3.plugin.PluginManager

@Plugin
class PortalApiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PortalApiProvider())
    }
}
