// TestPlugins/src/SinemaM3u/build.gradle.kts

import java.util.Properties // Bu satırı ekleyin

version = 3

dependencies {
    // Tüm bağımlılıkları parantez () içine alın!
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX UI ve yardımcı kütüphaneler
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")

    implementation("androidx.annotation:annotation:1.8.0")

    // Buraya projenizdeki diğer bağımlılıkları ekleyebilirsiniz
}

android {
    // BU SATIRI EKLEYİN
    namespace = "com.mooncrown" // Kotlin dosyalarınızdaki 'package com.mooncrown' ile eşleşmeli

    compileSdk = 34 // Veya kullandığınız en yüksek SDK versiyonu
    defaultConfig {
        minSdk = 21 // Minimum SDK
        // ... diğer defaultConfig ayarları

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

    // defaultConfig içinde tanımlandığı için bu bloğa gerek kalmadı
    // buildTypes {
    //     debug {
    //         buildConfigField("String", "TMDB_SECRET_API", "\"${properties.getProperty("TMDB_SECRET_API") ?: ""}\"")
    //     }
    //     release {
    //         buildConfigField("String", "TMDB_SECRET_API", "\"${properties.getProperty("TMDB_SECRET_API") ?: ""}\"")
    //     }
    // }
}

cloudstream {
    authors       = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language      = "tr"
    description = "powerboard`un sinema arşivi"

    /**
     * Durum int'i aşağıdaki gibidir:
     * 0: Kapalı
     * 1: Tamam
     * 2: Yavaş
     * 3: Sadece Beta
     **/
    status  = 1 // belirtilmezse 3 olur
    tvTypes = listOf("Movie")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powersinema/powersinema.png"
}
