package com.mooncrown

import java.io.Serializable
import java.net.URL
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

// hasEpg parametresi yapıcı metottan (constructor) kaldırıldı.
// Bu, "çok fazla argüman" hatasını çözecektir.
class MoOnCrOwNAlways(
    val tvgUrl: String
) {

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
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour

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
