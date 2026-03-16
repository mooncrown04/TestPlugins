package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.ducktape.Duktape // Cloudstream'in içindeki JS motoru

class MooncrownLoader : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/TestPlugins/refs/heads/master"
    override var name    = "Mooncrown Dynamic"
    override var lang    = "tr"
    override val hasMainPage = true

    // JS Motorunu Hazırla
    private var duktape: Duktape? = null

    private suspend fun getJsEngine(): Duktape {
        if (duktape == null) {
            val code = app.get("$mainUrl/scraper.js").text // GitHub'dan JS'yi çek
            duktape = Duktape.create()
            duktape?.evaluate(code) // Kodu motora yükle
        }
        return duktape!!
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val engine = getJsEngine()
        // JS içindeki 'search' fonksiyonunu çağır ve sonucu al
        val jsonResponse = engine.proxy(JsInterface::class.java).search(query)
        return jsonResponse.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val engine = getJsEngine()
        val res = engine.proxy(JsInterface::class.java).load(url)
        return res.toLoadResponse(url)
    }

    // JS ile Kotlin arasındaki köprü arayüzü
    interface JsInterface {
        fun search(query: String): List<JsSearchResponse>
        fun load(url: String): JsLoadResponse
    }
}

// JS'den gelecek veriler için basit modeller
data class JsSearchResponse(val title: String, val url: String, val poster: String?)
data class JsLoadResponse(val title: String, val plot: String?, val year: Int?, val poster: String?)
