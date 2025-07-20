package com.mooncrown

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin // Doğru Plugin import'u
import com.lagradost.cloudstream3.AcraApplication // AcraApplication import'u
import com.lagradost.cloudstream3.mvvm.logError // logError import'u
import androidx.appcompat.app.AppCompatActivity // AppCompatActivity import'u

// Bu sınıf, Cloudstream uygulamasının bir eklentisi olarak kaydedilir.
// Tüm sağlayıcılarınızı (MainAPI'lerinizi) buradan kaydedersiniz.
@Plugin(
    id = "com.mooncrown.Maciptv", // Eklentinizin benzersiz kimliği
    version = "1.0.0", // Eklentinizin versiyonu
    displayName = "Maciptv Plugin" // Eklentinizin görünen adı
)
class PortalApiPlugin : Plugin() { // Plugin sınıfından türetilmeli

    override fun load() {
        // PortalApiProvider'ı bir MainAPI olarak kaydediyoruz.
        // Bu, Cloudstream'in bu sağlayıcıyı tanımasını sağlar.
        registerMainAPI(PortalApiProvider())

        // Ayarlar veya diğer eklentiye özgü işlevler buraya eklenebilir.
        // Örneğin, eğer PortalApiProvider'da bir ayar menüsü açmak istiyorsanız:
        // addKey(R.string.portal_api_settings_key) { // R.string.portal_api_settings_key tanımlı olmalı
        //     val ctx = AcraApplication.context
        //     if (ctx is AppCompatActivity) {
        //         // Ayarlar fragment'ınızı burada gösterin
        //         // Örneğin: PortalApiSettingsFragment().show(ctx.supportFragmentManager, "PortalApiSettings")
        //     } else {
        //         logError(IllegalStateException("Context AppCompatActivity değil, ayarlar gösterilemiyor."))
        //     }
        // }
    }
}
