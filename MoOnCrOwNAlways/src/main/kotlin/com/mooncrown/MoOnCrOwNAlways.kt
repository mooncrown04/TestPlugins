package com.mooncrown

import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

// Bu sınıf, ana API'nin işlevselliğini tanımlar.
class MoOnCrOwNAlways : MainAPI() {

    // MainAPI arayüzünden gelen zorunlu özellikleri tanımlayın.
    override var name = "MoOnCrOwN Always"
    override var mainUrl = "https://example.com"

    // 'start' metodu 'override' anahtar kelimesi olmadan tanımlandı.
    suspend fun start() {
        // Plugin başladığında yapılacak ek işlemler buraya eklenebilir.
    }

    // @Serializable ile nesnemizi JSON'a çevrilebilir hale getiriyoruz.
    // Bu nesne, her bir ses parçacığının (kanalın) etiketlerini içerir.
    @Serializable
    data class ChannelData(
        val tvgName: String, // tvg-name etiketi, genellikle kanal adını içerir.
        val tvgId: String,   // tvg-id etiketi, kanalın benzersiz kimliğidir.
        val channelName: String, // Kanalın görüntülenen adı.
        val streamUrl: String // Kanalın yayın akışı URL'si.
    )

    companion object {
        // Bu önbellek (cache), daha önce çekilen verileri saklayarak gereksiz internet trafiğini önler.
        private val cache = ConcurrentHashMap<String, List<ChannelData>>()
        private val lastUpdated = ConcurrentHashMap<String, Long>()
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 saatlik önbellek süresi.

        // Kotlinx.serialization için JSON nesnesi.
        // ignoreUnknownKeys = true, bilmediğimiz anahtarlar olsa bile hatayı engeller.
        private val json = Json { ignoreUnknownKeys = true }

        // Veriyi internetten çekip metin olarak okur.
        private suspend fun fetchAndParseM3u(url: String): List<ChannelData> = withContext(Dispatchers.IO) {
            val m3uContent = URL(url).readText()
            parseM3u(m3uContent)
        }

        // Bu fonksiyon, M3U dosyasının içeriğini satır satır ayrıştırır ve etiketleri bulur.
        private fun parseM3u(m3uContent: String): List<ChannelData> {
            val channels = mutableListOf<ChannelData>()
            val lines = m3uContent.split('\n')
            var i = 0
            while (i < lines.size) {
                if (lines[i].startsWith("#EXTINF")) {
                    val extInfLine = lines[i]
                    val streamUrl = lines.getOrNull(i + 1)?.trim() ?: ""

                    // regex kullanarak tvg-name, tvg-id ve kanal adını buluyoruz.
                    val tvgName = Regex("tvg-name=\"(.*?)\"").find(extInfLine)?.groupValues?.get(1) ?: ""
                    val tvgId = Regex("tvg-id=\"(.*?)\"").find(extInfLine)?.groupValues?.get(1) ?: ""
                    val channelName = Regex(",(.*)").find(extInfLine)?.groupValues?.get(1)?.trim() ?: ""

                    if (streamUrl.isNotEmpty()) {
                        // Bulunan tüm etiketleri ChannelData nesnesine ekliyoruz.
                        channels.add(ChannelData(tvgName, tvgId, channelName, streamUrl))
                    }
                    i += 2
                } else {
                    i++
                }
            }
            return channels
        }

        // Ön belleği kullanarak kanal listesini çeker.
        suspend fun getChannels(url: String): List<ChannelData> {
            val currentTime = System.currentTimeMillis()
            // Önbellekte veri varsa ve süresi dolmadıysa, önbellekten veriyi döndürür.
            if (cache.containsKey(url) && (currentTime - (lastUpdated[url] ?: 0) < CACHE_EXPIRY_MS)) {
                return cache[url] ?: emptyList()
            }
            // Aksi takdirde, veriyi yeniden çeker, önbelleğe kaydeder ve döndürür.
            val channels = fetchAndParseM3u(url)
            cache[url] = channels
            lastUpdated[url] = currentTime
            return channels
        }
    }
}
