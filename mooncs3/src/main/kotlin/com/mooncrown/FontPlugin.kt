package com.mooncrown

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FontPlugin : Plugin() {
    override fun load(context: Context) {
        try {
            // 1. Fontu yükle
            val customFont = Typeface.createFromAsset(context.assets, "custom_font.ttf")

            // 2. Sistem Font Map'ini al
            val fontFamilyMapField = Typeface::class.java.getDeclaredField("sSystemFontMap")
            fontFamilyMapField.isAccessible = true
            
            @Suppress("UNCHECKED_CAST")
            val newMap = (fontFamilyMapField.get(null) as? MutableMap<String, Typeface>) 
                ?: HashMap()

            // 3. Varsayılan font ailelerini yeni font ile ez
            newMap["sans-serif"] = customFont
            newMap["sans-serif-medium"] = customFont
            newMap["sans-serif-bold"] = customFont
            newMap["serif"] = customFont
            newMap["monospace"] = customFont
            newMap["DEFAULT"] = customFont
            newMap["DEFAULT-BOLD"] = customFont

            // 4. Güncellenmiş haritayı geri yükle
            fontFamilyMapField.set(null, newMap)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun unload() {
        // Eklenti devre dışı bırakıldığında yapılacak işlemler
    }
}
