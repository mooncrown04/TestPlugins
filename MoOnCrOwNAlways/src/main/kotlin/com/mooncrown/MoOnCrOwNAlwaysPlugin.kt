package com.mooncrown

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class MoOnCrOwNAlwaysPlugin: Plugin() {
    override fun load(context: Context) {
    registerMainAPI(AnimeDizi(context.getSharedPreferences("AnimeDizi", 0)))
 
    }
}
