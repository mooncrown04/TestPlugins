package com.example

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern


import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.addDubStatus

// Bu dosya, Cloudstream için bir dizi eklentisi (provider) oluşturmak amacıyla yazılmıştır.
// Ana amaç, bir M3U dosyasını parse etmek ve içindeki dizileri düzenli bir şekilde listelemektir.

// --- Yardımcı Sınıflar ---

// M3U dosyasını temsil eden ana veri sınıfı.
data class Playlist(val items: List<PlaylistItem> = emptyList())

// M3U dosyasındaki her bir video akışını (kanal veya bölüm) temsil eder.
// title: Video başlığı.
// attributes: `tvg-logo`, `group-title` gibi ekstra bilgiler.
// url: Videonun oynatılabilir linki.
data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
) {
    companion object {
        const val EXT_M3U = "#EXTM3U" // M3U dosyasının başlık etiketi.
        const val EXT_INF = "#EXTINF" // Kanal veya video bilgisinin başladığı etiket.
        const val EXT_VLC_OPT = "#EXTVLCOPT" // VLC player için ek seçenekleri belirten etiket.
    }
}

// M3U dosyasını satır satır okuyarak Playlist ve PlaylistItem nesnelerine dönüştürür.
class IptvPlaylistParser {

    // String içeriği parse eder.
    fun parseM3U(content: String): Playlist = parseM3U(content.byteInputStream())

    // InputStream'i (dosya akışı) parse eder.
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        // Dosyanın #EXTM3U ile başlayıp başlamadığını kontrol eder.
        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = 0
        var line: String? = reader.readLine()

        // Dosyayı satır satır okur ve ilgili bilgileri yakalar.
        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(PlaylistItem.EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (!line.startsWith("#")) {
                    val item = playlistItems.getOrNull(currentIndex)
                    if (item != null) {
                        val url = line.getUrl()
                        playlistItems[currentIndex] = item.copy(url = url)
                        currentIndex++
                    } else {
                        Log.w("IptvPlaylistParser", "URL'ye karşılık gelen EXTINF satırı bulunamadı, atlanıyor: $line")
                    }
                }
            }
            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    // String uzantı fonksiyonları
    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)
    private fun String.getTitle(): String? = split(",").lastOrNull()?.trim()

    // EXTINF etiketindeki öznitelikleri (group-title, tvg-logo vb.) ayrıştırır.
    private fun String.getAttributes(): Map<String, String> {
        val attributesString = substringAfter("#EXTINF:-1 ")
        val attributes = mutableMapOf<String, String>()
        val quotedRegex = Regex("""([a-zA-Z0-9-]+)="(.*?)"""")
        val unquotedRegex = Regex("""([a-zA-Z0-9-]+)=([^"\s]+)""")

        quotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value.trim()
        }

        unquotedRegex.findAll(attributesString).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            if (!attributes.containsKey(key)) {
                attributes[key] = value.trim()
            }
        }
        return attributes
    }

    private fun String.getUrl(): String? = split("|").firstOrNull()?.trim()
}

// Hata yönetimi için özel bir istisna sınıfı
sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader : PlaylistParserException("Invalid file header.")
}

// Dizi başlıklarını "Dizi Adı", sezon ve bölüm bilgisi olarak ayrıştıran fonksiyon.
fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    // Başlıktaki görünmez özel karakterleri (sol-sağ işaretçi) temizle.
    val textWithCleanedChars = text.replace(Regex("[\\u200E\\u200F]"), "")

    // Regex ifadelerini büyük/küçük harf duyarsız hale getirir.
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""", RegexOption.IGNORE_CASE)
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""", RegexOption.IGNORE_CASE)

    val matchResult1 = format1Regex.find(textWithCleanedChars)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult2 = format2Regex.find(textWithCleanedChars)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    val matchResult3 = format3Regex.find(textWithCleanedChars)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        return Triple(title.trim(), seasonStr.toIntOrNull(), episodeStr.toIntOrNull())
    }

    return Triple(textWithCleanedChars.trim(), null, null)
}

// --- Ana Eklenti Sınıfı ---

class AnimeDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://raw.githubusercontent.com/mooncrown04/mooncrown34/refs/heads/master/dizi.m3u"
    override var name = "35 MoOnCrOwN Dizi 🎬"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    private val DEFAULT_POSTER_URL = "https://st5.depositphotos.com/1041725/67731/v/380/depositphotos_677319750-stock-illustration-ararat-mountain-illustration-vector-white.jpg"

    // JSON verilerini kolayca taşımak için veri sınıfı.
    // Aynı bölümden gelen tüm URL'leri bir liste olarak tutmak için güncellendi.
    data class LoadData(
        val urls: List<String>,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 1,
        val episode: Int = 0
    )

    // Ana sayfa düzenini oluşturur.
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        
        // Dizi başlıklarına göre dizileri gruplar.
        val groupedByCleanTitle = kanallar.items.groupBy {
            val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
            cleanTitle
        }

        // Gruplanmış dizileri, harflere veya özel gruplara göre ayırır.
        val alphabeticGroups = groupedByCleanTitle.toSortedMap().mapNotNull { (cleanTitle, shows) ->
            val firstChar = cleanTitle.firstOrNull()?.uppercaseChar() ?: '#'
            val firstShow = shows.firstOrNull() ?: return@mapNotNull null
            
            val searchResponse = newAnimeSearchResponse(
                cleanTitle,
                LoadData(listOf(firstShow.url.toString()), cleanTitle, firstShow.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL, cleanTitle, firstShow.attributes["tvg-country"]?.toString() ?: "TR").toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl = firstShow.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
               // this.lang = firstShow.attributes["tvg-country"]?.toString() ?: "TR"
           addDubStatus(isDub = true)
            
            }
            
            // Gruplama anahtarını belirler: harf, sayı veya özel karakter.
            val groupKey = when {
                firstChar.isLetter() -> firstChar.toString()
                firstChar.isDigit() -> "0-9"
                else -> "#" // Harf veya sayı değilse, özel karaktere atar.
            }
            Pair(groupKey, searchResponse)
        }.groupBy { it.first }.mapValues { it.value.map { it.second } }.toSortedMap()

        val finalHomePageLists = mutableListOf<HomePageList>()
        
        // Türkçe alfabenin doğru sıralaması
        val turkishAlphabet = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUVYZ".split("").filter { it.isNotBlank() }
        
        // Diğer İngilizce harfleri kendi yerlerine yerleştirir
        val fullAlphabet = mutableListOf<String>()
        fullAlphabet.addAll(turkishAlphabet)
        fullAlphabet.add(fullAlphabet.indexOf("S"), "Q")
        fullAlphabet.add(fullAlphabet.indexOf("V"), "W")
        fullAlphabet.add(fullAlphabet.indexOf("Z"), "X")
        fullAlphabet.add(fullAlphabet.indexOf("Z") + 1, "Y") // Y'yi Z'den önce getiririz

        // Grupları işleme listesine ekler.
        val allGroupsToProcess = mutableListOf<String>()
        if (alphabeticGroups.containsKey("0-9")) allGroupsToProcess.add("0-9")
        
        // Alfabetik grupları sırayla ekler.
        fullAlphabet.forEach { char ->
            if (alphabeticGroups.containsKey(char)) {
                allGroupsToProcess.add(char)
            }
        }
        
        // `#` grubunu en sona ekler.
        if (alphabeticGroups.containsKey("#")) allGroupsToProcess.add("#")

        // Her harf grubunu dolaşır ve ana sayfa listelerini oluşturur.
        allGroupsToProcess.forEach { char ->
            val shows = alphabeticGroups[char]
            if (shows != null && shows.isNotEmpty()) {
                val listTitle = when (char) {
                    "0-9" -> "🔢 **0-9** A B C..."
                    "#" -> "🔣 **#** A B C..."
                    else -> {
                        val startIndex = fullAlphabet.indexOf(char)
                        if (startIndex != -1) {
                            val remainingAlphabet = fullAlphabet.subList(startIndex, fullAlphabet.size).joinToString(" ") { it }
                            "🎬 **$char** ${remainingAlphabet.substring(1).lowercase(Locale.getDefault())}"
                        } else {
                            // Alfabe içinde olmayan, ancak harf olan karakterler için yedek başlık
                            "🎬 **$char** ile Başlayan Diziler"
                        }
                    }
                }
                finalHomePageLists.add(HomePageList(listTitle, shows, isHorizontalImages = true))
            }
        }

        return newHomePageResponse(finalHomePageLists, hasNext = false)
    }

    // Arama fonksiyonu.
    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        
        val groupedByCleanTitle = kanallar.items.groupBy {
            val (cleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
            cleanTitle
        }

        return groupedByCleanTitle.filter { (cleanTitle, _) ->
            cleanTitle.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
        }.map { (cleanTitle, shows) ->
            val firstShow = shows.firstOrNull() ?: return@map newLiveSearchResponse("", "", type = TvType.TvSeries)
            val posterUrl = firstShow.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
            val nation = firstShow.attributes["tvg-country"]?.toString() ?: "TR"
newAnimeSearchResponse(
         //   newLiveSearchResponse(
                cleanTitle,
                LoadData(listOf(firstShow.url.toString()), cleanTitle, posterUrl, firstShow.attributes["group-title"]?.toString() ?: cleanTitle, nation).toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl = posterUrl
        //        this.lang = nation
    addDubStatus(isDub = true)
            }
        }
    }

    // Hızlı arama için arama fonksiyonunu kullanır.
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // Bir diziye tıklandığında tüm bölümlerini yükler.
    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val cleanTitle = loadData.title

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        
        val allShows = kanallar.items.filter { 
            val (itemCleanTitle, _, _) = parseEpisodeInfo(it.title.toString())
            itemCleanTitle == cleanTitle
        }
        
        val finalPosterUrl = allShows.firstOrNull()?.attributes?.get("tvg-logo")?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
        val plot = "TMDB'den özet alınamadı."

        // Aynı sezon ve bölüm numarasına sahip tüm girişleri gruplar.
        val groupedEpisodes = allShows.groupBy {
            val (_, season, episode) = parseEpisodeInfo(it.title.toString())
            Pair(season, episode)
        }

        val currentShowEpisodes = groupedEpisodes.mapNotNull { (key, episodeItems) ->
            val (season, episode) = key
            if (season != null && episode != null) {
                val episodePoster = episodeItems.firstOrNull()?.attributes?.get("tvg-logo")?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL
                val episodeTitle = episodeItems.firstOrNull()?.title.toString()
                val (episodeCleanTitle, _, _) = parseEpisodeInfo(episodeTitle)

                // Aynı bölümün tüm URL'lerini bir listede toplar.
                val allUrls = episodeItems.map { it.url.toString() }

                newEpisode(LoadData(allUrls, episodeTitle, episodePoster, episodeItems.firstOrNull()?.attributes?.get("group-title")?.toString() ?: "Bilinmeyen Grup", episodeItems.firstOrNull()?.attributes?.get("tvg-country")?.toString() ?: "TR", season, episode).toJson()) {
                    this.name = "$episodeCleanTitle S$season E$episode"
                    this.season = season
                    this.episode = episode
                    this.posterUrl = episodePoster
                }
            } else null
        }.sortedWith(compareBy({ it.season }, { it.episode }))

        
        
        
        
val processedEpisodes = currentShowEpisodes.map { episode ->
    episode.apply {
        val episodeLoadData = parseJson<LoadData>(this.data)
        this.posterUrl = episodeLoadData.poster
    }
}

return newAnimeLoadResponse(
    cleanTitle,
    url,
    TvType.TvSeries,
    processedEpisodes
) {
    this.posterUrl = finalPosterUrl
    this.plot = plot
    this.tags = listOf(loadData.group, loadData.nation)
}
    


    // Bölümü oynatmak için gerekli linkleri sağlar.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        
        loadData.urls.forEachIndexed { index, videoUrl ->
            val linkQuality = Qualities.Unknown.value

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                 //   name = "Kaynak ${index + 1}",
                    name = "${loadData.title} Kaynak ${index + 1}",
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = linkQuality
                }
            )
        }
        return true
    }

    // Gelen verinin URL mi yoksa JSON mu olduğunu kontrol edip ilgili LoadData nesnesini döndürür.
    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.firstOrNull { it.url == data }

            if (kanal != null) {
                val (cleanTitle, season, episode) = parseEpisodeInfo(kanal.title.toString())
                
                return LoadData(
                    urls = listOf(kanal.url.toString()),
                    cleanTitle,
                    kanal.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: DEFAULT_POSTER_URL,
                    kanal.attributes["group-title"]?.toString() ?: "Bilinmeyen Grup",
                    kanal.attributes["tvg-country"]?.toString() ?: "TR",
                    season ?: 1,
                    episode ?: 0
                )
            } else {
                throw Exception("LoadData: URL bulunamadı veya format hatalı")
            }
        }
    }
}
