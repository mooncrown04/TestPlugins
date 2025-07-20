package com.mooncrown

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@Plugin(
    id = "com.mooncrown.PortalApi", // Eklentiniz için benzersiz bir kimlik
    version = "1.0.0", // Eklentinizin versiyonu
    displayName = "MoOnCrOwN" // Cloudstream'de görünecek adı
)
class PortalApiPlugin: Plugin() {
    override fun load(context: Context) {
        // PortalApiProvider'ı Cloudstream'e kaydeder
        registerMainAPI(PortalApiProvider())
    }
}
