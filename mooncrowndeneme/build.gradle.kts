import com.lagradost.cloudstream3.gradle.CloudstreamExtension

// Plugins bloğu (Eğer dosyanın başında varsa kalsın)
plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3")
}

// Eklentinin versiyonu.
version = 1 

cloudstream {
    // Kendi adını veya ekibini yazabilirsin
    authors     = listOf("MoOnCrOwN") 
    language    = "tr"
    description = "GitHub üzerinden dinamik olarak güncellenen Mooncrown sinema arşivi."

    status  = 1 
    // İleride dizi eklemek istersen TvSeries'i buraya ekledim, derleme yapmana gerek kalmaz.
    tvTypes = listOf("Movie", "TvSeries") 
    
    iconUrl = "https://raw.githubusercontent.com/mooncrown04/TestPlugins/master/icon.png"
}

android {
    // Mevcut android ayarların (compileSdk vb.) burada kalmalı
    namespace = "com.example" // Paket adınla uyumlu olmalı
}

dependencies {
    val cloudstreamVersion = "3.0.0" // Kullandığın SDK versiyonuna göre değişebilir
    
    // Temel Cloudstream kütüphaneleri
    implementation("com.lagradost:cloudstream3:$cloudstreamVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // KRİTİK: GitHub'dan gelen JS kodunu çalıştırmak için gereken motor
    implementation("com.lagradost:ducktape:1.0.4")
}
