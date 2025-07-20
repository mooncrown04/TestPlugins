// TestPlugins/src/Maciptv/build.gradle.kts

// Yapılandırma blokları için gerekli uzantıları import edin
import com.android.build.gradle.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.JavaVersion
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.android") // Bu satır en üste taşındı
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

version = 3

// Bu modül için cloudstream uzantısını yapılandırın
configure<CloudstreamExtension> {
    authors = listOf("mooncrown04", "GitLatte", "patr0nq", "keyiflerolsun")
    language = "tr"
    description = "powerboard`un Maciptv arşivi"

    /**
     * Durum int'i aşağıdaki gibidir:
     * 0: Kapalı
     * 1: Tamam
     * 2: Yavaş
     * 3: Sadece Beta
     **/
    status = 1 // belirtilmezse 3 olur
    tvTypes = listOf("Live", "Movie")
    iconUrl = "https://raw.githubusercontent.com/Zerk1903/zerkfilm/refs/heads/main/Maciptv.png"
    // internalName = "Maciptv" // Bu satır kaldırıldı, çünkü "Unresolved reference" hatasına neden oluyordu.
                               // Genellikle plugin id'sinden veya modül adından otomatik olarak türetilir.
}

// JVM Toolchain'i Kotlin derlemesi için ayarla
kotlin {
    jvmToolchain(17) // Tüm Kotlin görevleri için Java 17 kullanılmasını sağlar
}

// KAPT için özel JVM hedefi ayarı
kapt {
    // KAPT'ın Java 17'yi kullanmasını sağlamak için jvmTarget'ı açıkça ayarla
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    // Tüm bağımlılıkları parantez () içine alın!
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX UI ve yardımcı kütüphaneler
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")

    implementation("androidx.annotation:annotation:1.8.0")

    // Cloudstream core API'sine bağımlılık (Bu, birçok Cloudstream yardımcı fonksiyonunu sağlar)
    implementation(project(":app"))
}

// Bu modül için android uzantısını yapılandırın
configure<LibraryExtension> {
    namespace = "com.mooncrown" // Kotlin dosyalarınızdaki 'package com.mooncrown' ile eşleşmeli

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

    buildFeatures {
        buildConfig = true // Bu satırın olduğundan emin olun
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Kotlin 1.8.x ve Gradle 8+ için genellikle 17 idealdir
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    packaging { // packagingOptions yerine 'packaging' kullanıldı
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}
