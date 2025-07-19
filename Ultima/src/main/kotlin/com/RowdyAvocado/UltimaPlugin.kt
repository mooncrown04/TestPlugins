package com.RowdyAvocado

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.mvvm.logError
import com.RowdyAvocado.MetaProviders.AniList
import com.RowdyAvocado.MetaProviders.MyAnimeList
import com.RowdyAvocado.MetaProviders.Simkl
import com.RowdyAvocado.Settings.ConfigureExtensions
import com.RowdyAvocado.Settings.ConfigureWatchSync
import com.lagradost.cloudstream3.R // R sınıfınızın doğru paketini kontrol edin

class UltimaPlugin : Plugin() {
    override fun load() {
        // 'app' referansı genellikle AcraApplication.context'ten gelir.
        // Eğer bu bir Plugin sınıfı ise, doğrudan context'e erişiminiz olmayabilir.
        // Bu durumda, AcraApplication.context'i kullanmak en güvenli yoldur.
        // Eğer Simkl, AniList, MyAnimeList constructor'ları artık parametre almıyorsa:
        registerMainAPI(Simkl())
        registerMainAPI(AniList())
        registerMainAPI(MyAnimeList())

        // Ayarlar menüsüne yeni anahtar ekleme
        addKey(R.string.ultima_settings_key) {
            // Context'i güvenli bir şekilde alıyoruz
            val ctx = AcraApplication.context
            if (ctx is AppCompatActivity) {
                // ConfigureExtensions bir BottomSheetDialogFragment ise,
                // show metodu FragmentManager gerektirir.
                ConfigureExtensions.show(ctx.supportFragmentManager)
            } else {
                logError(IllegalStateException("UltimaPlugin: Context AppCompatActivity değil, ayarlar gösterilemiyor."))
            }
        }

        // İzleme senkronizasyonu ayarları menüsüne yeni anahtar ekleme
        addKey(R.string.ultima_watchsync_key) {
            val ctx = AcraApplication.context
            if (ctx is AppCompatActivity) {
                ConfigureWatchSync.show(ctx.supportFragmentManager)
            } else {
                logError(IllegalStateException("UltimaPlugin: Context AppCompatActivity değil, izleme senkronizasyonu ayarları gösterilemiyor."))
            }
        }

        // Yerel eklentileri yeniden yükleme anahtarı
        addKey(R.string.ultima_reload_local_plugins_key) {
            val ctx = AcraApplication.context
            if (ctx is AppCompatActivity) {
                // hotReloadAllLocalPlugins genellikle AcraApplication.Companion'da bulunur
                AcraApplication.hotReloadAllLocalPlugins(ctx)
            } else {
                logError(IllegalStateException("UltimaPlugin: Context AppCompatActivity değil, yerel eklentiler yeniden yüklenemiyor."))
            }
        }

        // Çevrimiçi eklentileri yükleme anahtarı
        addKey(R.string.ultima_reload_online_plugins_key) {
            val ctx = AcraApplication.context
            if (ctx is AppCompatActivity) {
                // loadAllOnlinePlugins genellikle AcraApplication.Companion'da bulunur
                AcraApplication.loadAllOnlinePlugins(ctx)
            } else {
                logError(IllegalStateException("UltimaPlugin: Context AppCompatActivity değil, çevrimiçi eklentiler yüklenemiyor."))
            }
        }
    }
}
