package com.mooncrown

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.api.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URLEncoder

class PortalApiProvider : MainAPI() {
    override var mainUrl = "http://wavetv.pro:8000"
    override var name = "Maciptv"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    private fun getPrefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val context = request.context
        val prefs = getPrefs(context)
        val portalUrl = prefs.getString("portal_url", "") ?: ""
        val mac = prefs.getString("mac_address", "") ?: ""

        if (portalUrl.isEmpty() || mac.isEmpty()) {
            throw ErrorLoadingException("Ayarlar yapılmamış")
        }

        val url = "$portalUrl/player_api.php?username=live&password=live"
        val response = app.get(url).text

        val json = try {
            JSONArray(response)
        } catch (e: Exception) {
            throw ErrorLoadingException("Geçersiz JSON")
        }

        val channels = ArrayList<LiveSearchResponse>()

        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val name = obj.getString("name")
            val streamUrl = obj.getString("url")
            val poster = obj.optString("icon", "")

            channels.add(
                LiveSearchResponse(
                    name,
                    streamUrl,
                    TvType.Live,
                    streamUrl,
                    poster,
                    null,
                    null
                )
            )
        }

        return newHomePageResponse(name) {
            addPage("Canlı Yayınlar", channels)
        }
    }

    override fun loadPreferences(context: Context) {
        val prefs = getPrefs(context)

        val editTextUrl = EditText(context).apply {
            hint = "Portal URL (örnek: http://wavetv.pro:8000)"
            setText(prefs.getString("portal_url", "http://wavetv.pro:8000"))
        }

        val editTextMac = EditText(context).apply {
            hint = "MAC Address (örnek: 00:2a:01:90:2b:36)"
            setText(prefs.getString("mac_address", "00:2a:01:90:2b:36"))
        }

        AlertDialog.Builder(context)
            .setTitle("Xtream/Portal Ayarları")
            .setView(editTextUrl)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.edit().apply {
                    putString("portal_url", editTextUrl.text.toString())
                    putString("mac_address", editTextMac.text.toString())
                    apply()
                }
                showToast(context, "Ayarlar kaydedildi.")
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveLoadResponse("Canlı Yayın", url, TvType.Live, url) {
            this.posterUrl = null
            this.plot = "Canlı yayın bağlantısı"
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                data,
                referer = null,
                quality = Qualities.Unknown.value
            )
        )
        return true
    }
}
