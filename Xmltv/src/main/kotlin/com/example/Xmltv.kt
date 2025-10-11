package com.example

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.text.*
import kotlin.collections.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.Calendar
// Mutex için gerekli import
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock 

// ***************************************************************
// VERİ MODELLERİ (AYNI KALACAK)
// ***************************************************************
data class ProgramInfo(
    val name: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val start: Long,
    val end: Long
)
data class EpgProgram(val name: String, val description: String?, val start: Long, val end: Long, val channel: String)
data class EpgData(val programs: Map<String, List<EpgProgram>> = emptyMap())
// ... (PlaylistItem, Playlist, Parser Sınıfları aynı kalacak) ...
// (Lütfen EpgXmlParser ve XmlPlaylistParser sınıflarını buraya eklemeyi unutmayın)


// EpgXmlParser sınıfı (Önceki kodlardan kopyalanmalı)
class EpgXmlParser { /* ... içeriği aynı ... */ }
// XmlPlaylistParser sınıfı (Önceki kodlardan kopyalanmalı)
class XmlPlaylistParser { /* ... içeriği aynı ... */ }


// ***************************************************************
// ANA API SINIFI - MUTEX İLE GÜVENLİ YÜKLEME
// ***************************************************************

class Xmltv : MainAPI() {
    // URL'ler
    override var mainUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b.xml"
    private val secondaryXmlUrl = "https://dl.dropbox.com/scl/fi/vg40bpys8ym1jjrcuv1wp/XMLTvcs.xml?rlkey=7g2chxiol35z6kg6b36c4nyv8"
    private val tertiaryXmlUrl = "http://lg.mkvod.ovh/mmk/fav/94444407da9b-2.xml"
    private val epgUrl = "https://raw.githubusercontent.com/braveheart1983/tvg-macther/refs/heads/main/tr-epg.xml"
    
    // EPG Önbellekleme değişkenleri
    @Volatile
    private var cachedEpgData: EpgData? = null 
    // Mutex tanımlaması
    private val epgMutex = Mutex() 

    override var name = "35 Xmltv"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    data class GroupedChannelData(
        val title: String,
        val posterUrl: String,
        val description: String? = null,
        val nation: String? = null,
        val items: List<PlaylistItem>
    )

    // ⭐ CRITICAL FIX: Mutex ile coroutine uyumlu kilitleme kullanılır.
    private suspend fun loadEpgData(): EpgData? {
        if (cachedEpgData != null) return cachedEpgData
        
        // Mutex.withLock, kilidi tutarken askıya alma (suspend) işlemlerine izin verir.
        return epgMutex.withLock {
            if (cachedEpgData != null) return cachedEpgData

            runCatching {
                Log.d("Xmltv", "EPG verisi ilk kez yükleniyor...")
                
                // app.get(epgUrl) burası askıya alma noktasıdır ve artık Mutex içindedir.
                val epgResponse = app.get(epgUrl).text 
                val epgData = EpgXmlParser().parseEPG(epgResponse)
                
                cachedEpgData = epgData
                epgData
            }.getOrElse { e ->
                Log.e("Xmltv", "EPG yüklenirken KRİTİK HATA oluştu: ${e.message}", e)
                null
            }
        }
    }

    // (createGroupedChannelItems, getMainPage ve search fonksiyonları aynı kalacak)
    // ...

    override suspend fun load(url: String): LoadResponse {
        val groupedData = parseJson<GroupedChannelData>(url)
        
        // EPG KODU BAŞLANGIÇ
        val epgData = loadEpgData() 
        val channelTvgId = groupedData.items.firstOrNull()?.attributes?.get("tvg-id")

        val programs: List<ProgramInfo> = if (channelTvgId != null && epgData != null) {
            epgData.programs[channelTvgId]
                ?.map { epgProgram: EpgProgram -> 
                    ProgramInfo( 
                        name = epgProgram.name,
                        description = epgProgram.description,
                        // ...
                        start = epgProgram.start,
                        end = epgProgram.end
                    )
                }
                ?.sortedBy { it.start } 
                ?: emptyList() 
        } else {
            emptyList()
        }
        
        // EPG verisini PLOT (Açıklama) metnine dönüştürme
        val epgPlotText = if (programs.isNotEmpty()) {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            
            val formattedPrograms = programs
                .filter { Calendar.getInstance().apply { timeInMillis = it.start }.get(Calendar.DAY_OF_YEAR) in (today)..(today + 1) }
                .joinToString(separator = "\n") { program ->
                    val startCal = Calendar.getInstance().apply { timeInMillis = program.start }
                    val startHour = String.format("%02d", startCal.get(Calendar.HOUR_OF_DAY))
                    val startMinute = String.format("%02d", startCal.get(Calendar.MINUTE))
                    val descriptionText = program.description?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                    
                    "[$startHour:$startMinute] ${program.name}$descriptionText"
                }
            
            "\n\n--- YAYIN AKIŞI ---\n" + formattedPrograms
        } else {
            "\n\n--- Yayın Akışı Bulunamadı ---"
        }

        val originalPlot = groupedData.description ?: ""
        val finalPlot = originalPlot + epgPlotText
        // EPG KODU BİTİŞ

        return newLiveStreamLoadResponse(
            name = groupedData.title,
            url = groupedData.toJson(), 
            dataUrl = groupedData.toJson(), 
        ) {
            this.posterUrl = groupedData.posterUrl
            // YALNIZCA PLOT ATAMASI
            this.plot = finalPlot 
            this.type = TvType.Live
            // this.program = programs satırı kaldırıldı
       }
    }
    
    // (loadLinks fonksiyonu aynı kalacak)
    // ...
}
