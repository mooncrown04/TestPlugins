package com.example

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeDiziPlugin: Plugin() {
    override fun load(context: CloudstreamPlugin) {
        registerMainAPI(AnimeDizi(context))
    }
}
