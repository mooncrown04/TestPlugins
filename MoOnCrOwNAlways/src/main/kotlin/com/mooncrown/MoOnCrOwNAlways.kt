package com.mooncrown

import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

// MoOnCrOwNAlways sınıfı, MainAPI'den doğru şekilde miras alınır.
class MoOnCrOwNAlways : MainAPI() {

    // MainAPI arayüzünden gelen zorunlu özellikleri tanımlayın.
    override var name = "MoOnCrOwN Always"
    override var mainUrl = "https://example.com"
    var tvgUrl = "https://example.com/epg/tvg.xml"

    // 'start' metodu 'override' anahtar kelimesi olmadan tanımlandı.
    suspend fun start() {
        // Plugin başladığında yapılacak işlemleri buraya ekleyin.
    }

    // @Serializable annotation'ı doğru şekilde kullanıldı.
    @Serializable
    data class ChannelData(
        val tvgName: String,
        val tvgId: String,
        val channelName: String,
        val streamUrl: String
    )

    companion object {
        private val cache = ConcurrentHashMap<String, List<ChannelData>>()
        private val lastUpdated = ConcurrentHashMap<String, Long>()
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 saat

        // Kotlinx.serialization Json nesnesi doğru şekilde yapılandırıldı.
        private val json = Json { ignoreUnknownKeys = true }

        private suspend fun fetchAndParseM3u(url: String): List<ChannelData> = withContext(Dispatchers.IO) {
            val m3uContent = URL(url).readText()
            parseM3u(m3uContent)
        }

        private fun parseM3u(m3uContent: String): List<ChannelData> {
            val channels = mutableListOf<ChannelData>()
            val lines = m3uContent.split('\n')
            var i = 0
            while (i < lines.size) {
                if (lines[i].startsWith("#EXTINF")) {
                    val extInfLine = lines[i]
                    val streamUrl = lines.getOrNull(i + 1)?.trim() ?: ""

                    val tvgName = Regex("tvg-name=\"(.*?)\"").find(extInfLine)?.groupValues?.get(1) ?: ""
                    val tvgId = Regex("tvg-id=\"(.*?)\"").find(extInfLine)?.groupValues?.get(1) ?: ""
                    val channelName = Regex(",(.*)").find(extInfLine)?.groupValues?.get(1)?.trim() ?: ""

                    if (streamUrl.isNotEmpty()) {
                        channels.add(ChannelData(tvgName, tvgId, channelName, streamUrl))
                    }
                    i += 2
                } else {
                    i++
                }
            }
            return channels
        }

        suspend fun getChannels(url: String): List<ChannelData> {
            val currentTime = System.currentTimeMillis()
            if (cache.containsKey(url) && (currentTime - (lastUpdated[url] ?: 0) < CACHE_EXPIRY_MS)) {
                return cache[url] ?: emptyList()
            }
            val channels = fetchAndParseM3u(url)
            cache[url] = channels
            lastUpdated[url] = currentTime
            return channels
        }
    }
}
