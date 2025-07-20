package com.moncrown // Yeni paket adınız buraya yazıldı

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.ExtractorApi
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.mvvm.logError
import okhttp3.FormBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.json.JSONObject // JSONObject import edildi
import java.net.URL // URL import edildi
import android.content.Context // Context import edildi
import android.widget.EditText // EditText import edildi
import android.widget.LinearLayout // LinearLayout import edildi
import androidx.appcompat.app.AlertDialog // AlertDialog import edildi
import androidx.core.content.edit // androidx.core.content.edit import edildi
import com.lagradost.cloudstream3.CommonActivity.showToast // showToast import edildi
import com.lagradost.cloudstream3.CommonActivity.currentActivity // currentActivity import edildi
import com.lagradost.cloudstream3.R // R sınıfı import edildi

// Cloudstream3 uzantısının ana sınıfı
class PortalApiProvider : MainAPI() {
    override var name = "PortalAPI" // Eklentinin adı
    override var mainUrl = "" // Ana URL, kullanıcı ayarlarından alınacak
    override var lang = "en" // Dil
    override val hasMainPage = true // Ana sayfa desteği var
    override val hasQuickSearch = false // Hızlı arama desteği yok
    override val hasDownloadSupport = true // İndirme desteği var
    override val hasChromecastSupport = true // Chromecast desteği var
    override val supportedTypes = setOf(TvType.Live, TvType.Movie) // Desteklenen içerik türleri
    override val vpnStatus = VPNStatus.MightBeNeeded // VPN gerekebilir

    // Kullanıcı tarafından ayarlanacak portal URL'si ve MAC adresi
    private var portalUrl: String = ""
    private var macAddress: String = ""
    private var token: String = "" // API token'ı

    // Kullanıcının portal URL'si ve MAC adresini girmesi için ayarlar
    override fun load(context: Context) {
        // Ayarları yüklemek için SharedPreferences kullanın
        val preferences = context.getSharedPreferences("PortalApiSettings", Context.MODE_PRIVATE)
        portalUrl = preferences.getString("portal_url", "") ?: ""
        macAddress = preferences.getString("mac_address", "") ?: ""

        // Ayarlar menüsüne ekleme
        addSettings(context) // 'add settings' yerine 'addSettings' olarak düzeltildi
    }

    // Ayarlar menüsünü oluşturur
    private fun addSettings(context: Context) {
        addKey(R.string.portal_api_settings_key) { // R.string.portal_api_settings_key, strings.xml'de tanımlanmalı
            // Dialog içinde ayar seçeneklerini oluşturun
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Portal API Ayarları")

            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(50, 20, 50, 20)

            val urlInput = EditText(context)
            urlInput.hint = "Portal URL (örn: http://example.com:8080)"
            urlInput.setText(portalUrl)
            layout.addView(urlInput)

            val macInput = EditText(context)
            macInput.hint = "MAC Adresi (örn: 00:1A:79:XX:YY:ZZ)"
            macInput.setText(macAddress)
            layout.addView(macInput)

            builder.setView(layout)

            builder.setPositiveButton("Kaydet") { dialog, _ ->
                portalUrl = urlInput.text.toString().trim()
                macAddress = macInput.text.toString().trim()

                // Ayarları kaydet
                val preferences = context.getSharedPreferences("PortalApiSettings", Context.MODE_PRIVATE)
                preferences.edit {
                    putString("portal_url", portalUrl)
                    putString("mac_address", macAddress)
                }

                showToast(context, "Ayarlar kaydedildi.")
                dialog.dismiss()
            }
            builder.setNegativeButton("İptal") { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }
    }


    // HTTP istek başlıklarını ayarlar
    private fun setHeaders(): Map<String, String> {
        val ua = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
        val headers = mutableMapOf(
            "User-Agent" to ua,
            "X-User-Agent" to "Model: MAG250; Link: WiFi",
            "Connection" to "keep-Alive",
            "Cookie" to "mac=${URLEncoder.encode(macAddress, "UTF-8")}; stb_lang=en; timezone=Europe%2Amsterdam;",
            "Pragma" to "no-cache",
            "Accept" to "*/*",
            "Content-Type" to "application/x-www-form-urlencoded",
            "Accept-Encoding" to "gzip"
        )
        if (token.isNotEmpty()) {
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }

    // API isteği gönderir
    private suspend fun doRequest(path: String, isGet: Boolean = false): JSONObject? {
        if (portalUrl.isEmpty() || macAddress.isEmpty()) {
            logError("Portal URL veya MAC Adresi ayarlanmamış.")
            showToast(currentActivity, "Portal URL veya MAC Adresi ayarlanmamış. Lütfen ayarlardan girin.")
            return null
        }

        val requestUrl = "$portalUrl/portal.php?$path&JsHttpRequest=1-xml"
        val headers = setHeaders()

        return try {
            val response = if (isGet) {
                app.get(requestUrl, headers = headers, timeout = 5, verify = false) // 5 saniye timeout
            } else {
                // POST istekleri için payload'ı FormBody olarak oluştur
                val parsedUrl = URL(requestUrl)
                val queryParams = parsedUrl.query?.split("&")?.associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                } ?: emptyMap()

                val formBodyBuilder = FormBody.Builder()
                queryParams.forEach { (key, value) ->
                    formBodyBuilder.add(key, value)
                }
                val requestBody = formBodyBuilder.build()
                app.post(portalUrl + "/portal.php", headers = headers, requestBody = requestBody, timeout = 5, verify = false)
            }

            if (response.code >= 400) {
                logError("HTTP Hatası: ${response.code} - ${response.text}")
                return null
            }

            val jsonResponse = JSONObject(response.text)
            if (jsonResponse.has("js")) {
                jsonResponse.getJSONObject("js")
            } else {
                null
            }
        } catch (e: Exception) {
            logError("İstek Hatası: ${e.message}")
            // İtalyan yasalarıyla ilgili hata mesajı kontrolü
            if (e.message?.contains("AVVISO") == true && e.message?.contains("680/13/CONS") == true) {
                showToast(currentActivity, "La lista e' stata bloccata dalla magistratura. Prova ad usare una VPN")
            }
            null
        }
    }

    // Token alır
    private suspend fun getToken(): String? {
        if (token.isNotEmpty()) return token // Zaten token varsa tekrar alma
        val res = doRequest("type=stb&action=handshake")
        val newToken = res?.optString("token")
        if (newToken != null) {
            token = newToken
            getProfile() // Token alındıktan sonra profili çek
        }
        return newToken
    }

    // Profil bilgilerini çeker
    private suspend fun getProfile(): JSONObject? {
        val timestamp = Instant.now().epochSecond
        val p = "type=stb&action=get_profile&hd=1&ver=ImageDescription: 0.2.18-r23-254; ImageDate: Wed Oct 31 15:22:54 EEST 2018; PORTAL version: 5.5.0; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c&num_banks=2&sn=17F88C9910EF5&client_type=STB&image_version=218&video_out=hdmi&device_id=F48C7788EF17D24F661C5A1782DF0D2237D545BE12A5A4B4325D89A52A7DF186&device_id2=F48C7788EF17D24F661C5A1782DF0D2237D545BE12A5A4B4325D89A52A7DF186&signature=845519FCE3386C1847AD3469AAD6D2773080C6F94CB59CD0A9E82605FAEDE02F&auth_second_step=1&hw_version=2.6-IB-00&not_valid_token=0&metrics={\"mac\":\"$macAddress\",\"sn\":\"17F88C9910EF5\",\"type\":\"STB\",\"model\":\"MAG254\",\"uid\":\"F48C7788EF17D24F661C5A1782DF0D2237D545BE12A5A4B4325D89A52A7DF186\",\"random\":\"\"}&hw_version_2=aff4d6ab1ab4e0660f09f89809c6e9782fa43263&timestamp=$timestamp&api_signature=262"
        doRequest(p) // İlk çağrı sadece bilgiyi göndermek için
        return doRequest("type=stb&action=get_profile") // İkinci çağrı profili almak için
    }

    // Kategorileri alır (Canlı TV veya VOD)
    private suspend fun getCategories(ltype: String = "live"): List<Pair<String, String>> {
        getToken() ?: return emptyList()
        val url = if (ltype == "live") "type=itv&action=get_genres" else "type=vod&action=get_categories"
        val res = doRequest(url, true)
        val ret = mutableListOf<Pair<String, String>>()
        res?.optJSONArray("genres")?.let { genresArray -> // 'genres' anahtarını kontrol et
            for (i in 0 until genresArray.length()) {
                val genre = genresArray.getJSONObject(i)
                val id = genre.optString("id")
                val title = genre.optString("title")
                if (id.isNotEmpty() && title.isNotEmpty() && title.lowercase() != "all") {
                    ret.add(Pair(id, title))
                }
            }
        }
        res?.optJSONArray("categories")?.let { categoriesArray -> // 'categories' anahtarını kontrol et
            for (i in 0 until categoriesArray.length()) {
                val category = categoriesArray.getJSONObject(i)
                val id = category.optString("id")
                val title = category.optString("title")
                if (id.isNotEmpty() && title.isNotEmpty() && title.lowercase() != "all") {
                    ret.add(Pair(id, title))
                }
            }
        }
        return ret
    }

    // Canlı TV kategorilerini alır
    private suspend fun getItvGenres(): List<Pair<String, String>> = getCategories("live")

    // VOD kategorilerini alır
    private suspend fun getVodGenres(): List<Pair<String, String>> = getCategories("vod")

    // Tüm kanalları alır
    private suspend fun getAllChannels(): List<Triple<String, String, String>> {
        getToken() ?: return emptyList()
        val res = doRequest("type=itv&action=get_all_channels")
        val genres = getGenres() // ID'ye göre tür başlıklarını almak için
        val ret = mutableListOf<Triple<String, String, String>>()

        res?.optJSONArray("data")?.let { channelsArray ->
            for (i in 0 until channelsArray.length()) {
                val channel = channelsArray.getJSONObject(i)
                val id = channel.optString("id")
                val name = channel.optString("name")
                val cmd = channel.optString("cmd").substringAfter(" ") // "ffmpeg " kısmını kaldır
                val logo = channel.optString("logo")
                val genreId = channel.optString("tv_genre_id")
                val genreTitle = genres[genreId]?.optString("title") ?: "" // Tür başlığını al

                if (id.isNotEmpty() && name.isNotEmpty() && cmd.isNotEmpty()) {
                    ret.add(Triple(name, cmd, logo)) // Sadece adı, komutu ve logoyu döndürüyoruz
                }
            }
        }
        return ret
    }

    // Türleri ID'ye göre bir sözlük olarak alır
    private suspend fun getGenres(): Map<String, JSONObject> {
        getToken() ?: return emptyMap()
        val res = doRequest("type=itv&action=get_genres", true)
        val ret = mutableMapOf<String, JSONObject>()
        res?.optJSONArray("genres")?.let { genresArray ->
            for (i in 0 until genresArray.length()) {
                val genre = genresArray.getJSONObject(i)
                val id = genre.optString("id")
                if (id.isNotEmpty()) {
                    ret[id] = genre
                }
            }
        }
        return ret
    }

    // Sıralı listeyi alır (Canlı TV veya VOD)
    private suspend fun getOrderedList(idGenre: String, ltype: String = "live", check: Boolean = false): List<Pair<String, String>> {
        getToken() ?: return emptyList()
        val baseUrl = if (ltype == "live") "type=itv&action=get_ordered_list&genre=$idGenre" else "type=vod&action=get_ordered_list&genre=$idGenre"
        val fullUrl = "$baseUrl&force_ch_link_check=&fav=0&sortby=number&hd=0"

        var totalPages = 0
        var page = 1
        val ret = mutableListOf<Pair<String, String>>()

        while (true) {
            val res = doRequest("$fullUrl&p=$page", true)
            res?.optJSONArray("data")?.let { dataArray ->
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val name = item.optString("name")
                    val cmd = item.optString("cmd")
                    if (name.isNotEmpty() && cmd.isNotEmpty()) {
                        ret.add(Pair(name, cmd))
                    }
                }
            }

            if (totalPages == 0) {
                val totalItems = res?.optInt("total_items", 0) ?: 0
                val maxPageItems = res?.optInt("max_page_items", 1) ?: 1
                totalPages = Math.ceil(totalItems.toDouble() / maxPageItems.toDouble()).toInt()
            }

            page++
            if (page > totalPages || check) {
                break
            }
        }
        return ret
    }

    // Canlı TV listesini alır
    private suspend fun getItvList(idGenre: String): List<Pair<String, String>> = getOrderedList(idGenre, "live")

    // VOD listesini alır
    private suspend fun getVodList(idCateg: String): List<Pair<String, String>> = getOrderedList(idCateg, "vod")

    // Akış bağlantısını oluşturur
    private suspend fun getLink(cmd: String, ltype: String = "itv"): String? {
        getToken() ?: return null
        val url = "type=$ltype&action=create_link&cmd=$cmd&series=0&forced_storage=false&disable_ad=false&download=false&force_ch_link_check=false"
        val res = doRequest(url, true)
        var link = cmd

        res?.let {
            val id = it.optString("id")
            val cmdRes = it.optString("cmd")

            if (id.isNotEmpty() && id.matches(Regex("\\d+"))) { // id bir sayı ise
                link = cmdRes
            } else if (cmdRes.isNotEmpty()) {
                try {
                    val strmIdMatch = Pattern.compile(".*?/(\\d+(?:\\.ts|$))").matcher(id)
                    val baseMatch = Pattern.compile("(http.*?://.*?:\\d+(?:/live/|/).*?/.*?/).*?(\\?.*?)$").matcher(cmdRes)

                    if (strmIdMatch.find() && baseMatch.find()) {
                        val strmId = strmIdMatch.group(1)
                        val baseUrl = baseMatch.group(1)
                        val queryParams = baseMatch.group(2)
                        link = "$baseUrl$strmId$queryParams"
                    }
                } catch (e: Exception) {
                    logError("Link ayrıştırma hatası: ${e.message}")
                }
            }
        }

        // "ffmpeg " kısmını kaldır
        link = link.substringAfter(" ").trim()

        var params = ""
        // Python kodundaki self.params kısmını burada nasıl yöneteceğiniz,
        // bu parametrelerin nereden geldiğine bağlıdır.
        // Şimdilik boş bırakıyorum, gerekirse kullanıcı ayarlarından alınabilir.

        if (!params.contains("User-Agent", ignoreCase = true)) {
            params += "!User-Agent=Lavf53.32.100"
        }
        if (!params.contains("Icy-MetaData", ignoreCase = true)) {
            if (params.isNotEmpty()) params += "&"
            params += "Icy-MetaData=1"
        }

        return "$link|$params"
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categories = getItvGenres() // Canlı TV kategorilerini alıyoruz
        val homePageLists = mutableListOf<HomePageList>()

        for ((id, name) in categories) {
            val channels = getItvList(id)
            val searchResponses = channels.mapNotNull { (channelName, cmd) ->
                // Kanal bilgilerini kullanarak SearchResponse oluştur
                // Logo URL'si için getAllChannels'tan gelen bilgiyi veya varsayılanı kullanabiliriz
                val logoUrl = getAllChannels().firstOrNull { it.second == cmd }?.third
                newLiveSearchResponse(
                    channelName,
                    cmd, // Data olarak cmd'yi kullanıyoruz, loadLinks'te işlenecek
                    TvType.Live // Canlı yayın türü
                ) {
                    this.posterUrl = logoUrl // Kanal logosunu poster olarak ayarla
                }
            }
            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList(name, searchResponses, isHorizontalImages = false))
            }
        }
        return newHomePageResponse(homePageLists, hasNext = false)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val allChannels = getAllChannels() // Tüm kanalları al
        return allChannels.filter { it.first.lowercase().contains(query.lowercase()) }.mapNotNull { (channelName, cmd, logoUrl) ->
            newLiveSearchResponse(
                channelName,
                cmd, // Data olarak cmd'yi kullanıyoruz
                TvType.Live
            ) {
                this.posterUrl = logoUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // Bu fonksiyon genellikle bir LoadResponse döndürmek için kullanılır.
        // Canlı TV için doğrudan bir LoadResponse oluşturabiliriz.
        // URL burada cmd olarak kabul ediliyor.
        val channelName = search(url).firstOrNull()?.name ?: "Bilinmeyen Kanal"
        val posterUrl = search(url).firstOrNull()?.posterUrl

        return newLiveLoadResponse(channelName, url, TvType.Live, url) {
            this.posterUrl = posterUrl
            this.plot = "Canlı TV yayını: $channelName"
            // Diğer detaylar (yıl, etiketler, öneriler) canlı yayınlar için genellikle geçerli değildir.
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cmd = data // load fonksiyonundan gelen data, burada cmd olarak kullanılıyor
        val link = getLink(cmd, "itv") // Canlı TV için link al
        if (link != null) {
            val parts = link.split("|", limit = 2)
            val streamUrl = parts[0]
            val params = parts.getOrElse(1) { "" }

            // ExtractorLink oluştur ve geri çağır
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8, // Genellikle canlı yayınlar M3U8 olur
                    quality = Qualities.Unknown.value, // Kalite bilinmiyorsa Unknown
                    referer = portalUrl // Referer olarak portal URL'sini kullan
                ) {
                    // Parametreleri headers olarak ekle
                    // Python'daki format "Key=Value&Key2=Value2" veya "!Key=Value"
                    // Cloudstream'de headers map olarak beklenir.
                    val headerMap = mutableMapOf<String, String>()
                    params.split("&").forEach { param ->
                        val cleanParam = param.removePrefix("!")
                        val eqIndex = cleanParam.indexOf("=")
                        if (eqIndex != -1) {
                            val key = cleanParam.substring(0, eqIndex)
                            val value = cleanParam.substring(eqIndex + 1)
                            headerMap[key] = value
                        }
                    }
                    headers = headerMap
                }
            )
            return true
        }
        return false
    }
}
