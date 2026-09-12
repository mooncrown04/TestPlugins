package com.mooncrown.fontplugin


import android.content.Context
import android.graphics.Typeface
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FontPlugin : Plugin() {
    override fun load(context: Context) {
        // Eklenti içindeki assets klasöründen fontu yükle
        val customFont = Typeface.createFromAsset(context.assets, "custom_font.ttf")
        
        // Uygulamanın varsayılan Roboto / Sans-Serif fontunu sistem düzeyinde ez
        try {
            val defaultFontField = Typeface::class.java.getDeclaredField("SERIF")
            defaultFontField.isAccessible = true
            defaultFontField.set(null, customFont)
            
            val sansFontField = Typeface::class.java.getDeclaredField("SANS_SERIF")
            sansFontField.isAccessible = true
            sansFontField.set(null, customFont)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun unload() {
        // Eklenti kaldırıldığında yapılacak işlemler
    }
}