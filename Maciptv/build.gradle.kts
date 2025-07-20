// src/Maciptv/build.gradle.kts

// Yapılandırma blokları için gerekli uzantıları import edin
import com.android.build.gradle.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.JavaVersion
import java.util.Properties // Bu satırı ekleyin

plugins {
    // Android kütüphane modülü için gerekli temel plugin
    id("com.android.library")
    // Kotlin Android projeleri için gerekli
    id("org.jetbrains.kotlin.android") // BU SATIR EKLENDİ!
    // Cloudstream eklenti API'sini kullanmak için gerekli plugin
    id("com.lagradost.cloudstream3.gradle")
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

version = 3

// Bu modül için cloudstream uzantısını yapılandırın
configure<CloudstreamExtension> {
    authors = listOf("GitLatte", "patr0nq", "keyiflerolsun") // Yazar listesini güncelleyin
    language = "tr" // Dil
    description = "Maciptv için Cloudstream eklentisi" // Eklentinin açıklaması

    /**
     * Durum int'i aşağıdaki gibidir:
     * 0: Kapalı
     * 1: Tamam
     * 2: Yavaş
     * 3: Sadece Beta
     **/
    status = 1 // belirtilmezse 3 olur
    tvTypes = listOf("Live", "Movie") // Desteklenen TV türleri
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/maciptv/maciptv.png" // Eklenti simgesi URL'si
    internalName = "Maciptv" // internalName'i buraya taşıdık
}

dependencies {
    // Tüm bağımlılıkları parantez () içine alın!
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX UI ve yardımcı kütüphaneler
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.17.1") // Fragment-ktx versiyonunu 1.7.1 olarak güncelledik

    implementation("androidx.annotation:annotation:1.8.0")

    // Cloudstream core API'sine bağımlılık (Bu, birçok Cloudstream yardımcı fonksiyonunu sağlar)
    implementation(project(":app"))
}

// Bu modül için android uzantısını yapılandırın
configure<LibraryExtension> {
    // Kotlin dosyalarınızdaki 'package com.mooncrown' ile eşleşmeli
    namespace = "com.mooncrown" 

    compileSdk = 34 // Veya kullandığınız en yüksek SDK versiyonu
    defaultConfig {
        minSdk = 21 // Minimum SDK
        targetSdk = 34 // Hedeflenen SDK versiyonu

        // TMDB_SECRET_API'yi local.properties'ten veya ortam değişkenlerinden yüklemek için
        // properties nesnesini burada tanımlayın ve yükleyin
        val properties = Properties().apply {
            val propertiesFile = project.rootProject.file("local.properties")
            if (propertiesFile.exists()) {
                propertiesFile.inputStream().use { this.load(it) }
            } else {
                // Eğer local.properties yoksa, GitHub Actions ortamında ortam değişkenlerini kullanabiliriz.
                // Bu durumda, GitHub Actions workflow'unuzda TMDB_SECRET_API'yi ortam değişkeni olarak ayarladığınızdan emin olun.
                // Örneğin: TMDB_SECRET_API: ${{ secrets.TMDB_SECRET_API_KEY }}
                setProperty("TMDB_SECRET_API", System.getenv("TMDB_SECRET_API") ?: "")
            }
        }
        
        // buildConfigField'ı güncellendi: properties nesnesinden değeri alacak
        buildConfigField("String", "TMDB_SECRET_API", "\"${properties.getProperty("TMDB_SECRET_API") ?: ""}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Kotlin 1.8.x ve Gradle 8+ için genellikle 17 idealdir
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        buildConfig = true // Bu satırın olduğundan emin olun
    }

    packaging { // packagingOptions yerine 'packaging' kullanıldı
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}
