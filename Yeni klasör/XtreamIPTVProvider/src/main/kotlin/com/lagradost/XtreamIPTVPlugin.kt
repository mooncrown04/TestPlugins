package com.anhdaden

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.plugins.*
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey

@CloudstreamPlugin
class XtreamIPTVPlugin : Plugin() {
    override fun load(context: Context) {
        reload()
    }

    init {
        this.openSettings = {
            try {
                val activity = it as? AppCompatActivity
                if (activity != null) {
                    val frag = XtreamIPTVSettingsFragment(this)
                    frag.show(activity.supportFragmentManager, "XtreamIPTV")
                }
            } catch (e: Exception) {
            }
        }
    }

    fun reload() {
        try {
            val savedLinks = getKey<Array<Link>>("xtream_iptv_links") ?: emptyArray()
            savedLinks.forEach { link ->
                val pluginData = PluginManager.getPluginsOnline().find { it.internalName.contains(link.name) }
                if (pluginData != null) {
                    PluginManager.unloadPlugin(pluginData.filePath)
                } else {
                    registerMainAPI(XtreamIPTVProvider(link.mainUrl, link.name, link.username, link.password))
                }
            }
            MainActivity.afterPluginsLoadedEvent.invoke(true)
        } catch (e: Exception) {
        }
    }
}