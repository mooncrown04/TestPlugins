package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NeonSpor : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown/refs/heads/main/guncel_liste.m3u"
    private val epgUrl = "https://iptv-epg.org/files/epg-tr.xml"

    @Volatile
    private var cachedEpgData: EpgData? = null
    private val epgMutex = Mutex()

    override var name = "ANİME-TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    // --- EPG VERİSİNİ YÜKLE ---
    private suspend fun loadEpgData(): EpgData {
        if (cachedEpgData != null) return cachedEpgData!!
        return epgMutex.withLock {
            if (cachedEpgData != null) return cachedEpgData!!
            try {
                val response = app.get(epgUrl).text
                val parsed = EpgXmlParser().parseEPG(response)
                cachedEpgData = parsed
                parsed
            } catch (e: Exception) {
                Log.e("EPG", "EPG yüklenemedi: ${e.message}")
                EpgData()
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val playlistText = app.get(mainUrl).text
        val kanallar = IptvPlaylistParser().parseM3U(playlistText)

        val homePageLists = kanallar.items.groupBy { it.attributes["group-title"] ?: "Diğer" }.map { group ->
            val groupName = group.key
            val channelList = group
