// TestPlugins/src/Ultima/build.gradle.kts

// Yapılandırma blokları için gerekli uzantıları import edin
import com.android.build.gradle.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension // Bu import geri getirildi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.JavaVersion
// import java.util.Properties // Artık API anahtarlarını okumadığımız için bu import'a gerek kalmayabilir

plugins {
    // Cloudstream eklentisinin uzantılarını tanımak için bu plugin'leri açıkça ekliyoruz
    id("com.android.library") // Android kütüphane modülü için gerekli
    id("com.lagradost.cloudstream3.gradle") // Cloudstream plugin'i
    id("org.jetbrains.kotlin.android") // Kotlin Android plugin'i
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

// Kullanıcının verdiği versiyon numarası
version = 41

// Bu modül için cloudstream uzantısını yapılandırın
// configure<CloudstreamExtension> sarmalayıcısı kaldırıldı, doğrudan 'cloudstream' bloğu kullanıldı
cloudstream { // Doğrudan 'cloudstream' bloğu kullanıldı
    // Kullanıcının verdiği değerler
    description = "The ultimate All-in-One home screen to access all of your extensions at one place (You need to select/deselect sections in Ultima's settings to load other extensions on home screen)"
    authors = listOf("RowdyRushya")
    status = 1
    tvTypes = listOf("All")
    requiresResources = true
    language = "en"
    iconUrl = "https://raw.githubusercontent.com/Rowdy-Avocado/Rowdycado-Extensions/master/logos/ultima.png"
    internalName = "Ultima" // internalName, cloudstream bloğunun içinde
}

dependencies {
    // Tüm bağımlılıkları parantez () içine alın!
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX UI ve yardımcı kütüphaneler
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Kullanıcının verdiği material bağımlılığı
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.annotation:annotation:1.8.0")

    // Cloudstream core API'sine bağımlılık (Bu, birçok Cloudstream yardımcı fonksiyonunu sağlar)
    implementation(project(":app"))

    // Rhino JavaScript motoru bağımlılığı (mozilla ve Scriptable hataları için)
    // Cloudstream'in kendi içinde bir JS motoru varsa bu gerekli olmayabilir,
    // ancak hata devam ederse bu satırı eklemeyi deneyin.
    implementation("org.mozilla:rhino:1.7.14") // En son stabil versiyonu kullanın
}

// Bu modül için android uzantısını yapılandırın
configure<LibraryExtension> {
    // Ultima eklentinizin doğru paket adı
    // Eğer Ultima'daki Kotlin dosyalarınız 'package com.RowdyAvocado' ile başlıyorsa bu doğru.
    namespace = "com.RowdyAvocado" 

    compileSdk = 34 // Genellikle en son stabil versiyonu kullanın
    defaultConfig {
        minSdk = 21 // Cloudstream için minimum desteklenen SDK
        // targetSdk = 34 // Deprecated uyarısı nedeniyle kaldırıldı. compileSdk yeterli olmalı.

        // API anahtarlarını kaldırdık, bu yüzden properties nesnesine gerek kalmadı.
        // Eğer başka buildConfigField'larınız varsa buraya ekleyebilirsiniz.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Kotlin 1.8.x ve Gradle 8+ için genellikle 17 idealdir
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        buildConfig = true // BuildConfig sınıfı oluşturmayı etkinleştir (ancak içinde API anahtarları olmayacak)
    }

    packaging { // packagingOptions yerine 'packaging' kullanıldı
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}
