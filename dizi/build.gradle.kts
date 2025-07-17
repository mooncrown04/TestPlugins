plugins {
    // Cloudstream eklenti API'sini kullanmak için gerekli plugin
    id("com.lagradost.cloudstream") version "1.0.0" // Güncel Cloudstream plugin versiyonunu buraya girin

    // Kotlin Android projeleri için gerekli
    id("org.jetbrains.kotlin.android")

    // Parcelize anotasyonunu kullanmak için
    id("kotlin-parcelize")

    // Kapt (Kotlin Annotation Processing Tool) kullanmak için, özellikle bazı kütüphaneler için gerekebilir
    id("kotlin-kapt")
}

android {
    // Android SDK versiyonları ve derleme ayarları
    compileSdk = 34 // Genellikle en son stabil versiyonu kullanın

    defaultConfig {
        minSdk = 21 // Cloudstream için minimum desteklenen SDK
        targetSdk = 34 // Hedeflenen SDK versiyonu

        // BuildConfig alanları
        // TMDB API anahtarınızı BuildConfig'e eklemek için
        // local.properties dosyanızda 'tmdbApiKey="YOUR_API_KEY"' şeklinde tanımlanmalı
        val apiKey = project.findProperty("tmdbApiKey")?.toString() ?: ""
        buildConfigField("String", "TMDB_SECRET_API", "\"$apiKey\"")
    }

    // JVM uyumluluğu
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Kotlin 1.8.x ve Gradle 8+ için genellikle 17 idealdir
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    // Derleme özellikleri (build features)
    buildFeatures {
        buildConfig = true // BuildConfig sınıfı oluşturmayı etkinleştir
    }

    // Android kaynak birleştirme stratejisi, çakışmaları önlemek için
    packagingOptions {
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}

dependencies {
    // Kotlin Coroutines kütüphanesi
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX temel kütüphaneleri
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.annotation:annotation:1.8.0")

    // Cloudstream API bağımlılıkları
    // Bunlar Cloudstream eklenti geliştirme için temel API'lerdir
    implementation(project(":app")) // Cloudstream core API'sine bağımlılık
}

// Cloudstream özel yapılandırma bloğu
cloudstream {
    // Eklenti için genel bilgiler
    authors = listOf("GitLatte", "patr0nq", "keyiflerolsun") // Eklentinin yazarları
    language = "tr" // Eklentinin desteklediği dil
    description = "powerboard`un yabancı dizi arşivi" // Eklentinin kısa açıklaması

    /**
     * Eklentinin durumu (0: Kapalı, 1: Çalışıyor, 2: Yavaş, 3: Beta)
     * Belirtilmezse varsayılan 3 (Beta) olur.
     **/
    status = 1 // Eklentinin durumu (örneğin, "Ok" yani çalışıyor)

    // Eklentinin desteklediği içerik türleri
    // Dizi eklentisi için 'TvSeries'
    // Film eklentisi için 'Movie'
    // Canlı TV için 'Live'
    tvTypes = listOf("TvSeries")

    // Eklentinin ana simgesinin URL'si
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powerdizi/powerdizi.png"

    // Eklentinin dahili adı (build output dosya adı için kullanılır)
    // Eğer bu dosyayı 'dizi' modülü için kullanıyorsanız, buraya 'DiziProvider' gibi bir isim verebilirsiniz.
    internalName = "Dizi" // Bu isim, .cs3 dosyasının adı olacaktır (örn: DiziProvider.cs3)
}
