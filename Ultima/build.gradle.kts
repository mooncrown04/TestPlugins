/ TestPlugins/src/Ultima/build.gradle.kts

plugins {
    // Cloudstream eklenti API'sini kullanmak için gerekli plugin
    // Lütfen Cloudstream GitHub reposundan aldığınız güncel versiyonu buraya girin!
    id("com.lagradost.cloudstream") version "-SNAPSHOT" // <-- BURASI GÜNCELLENDİ!

    // Kotlin Android projeleri için gerekli
    id("org.jetbrains.kotlin.android")

    // Parcelize anotasyonunu kullanmak için
    id("kotlin-parcelize")

    // Kapt (Kotlin Annotation Processing Tool) kullanmak için, özellikle bazı kütüphaneler için gerekebilir
    id("kotlin-kapt")
}
pluginManagement {
    repositories {
        gradlePluginPortal() // Gradle'ın kendi plugin portalı
        google()
        mavenCentral()
        maven("https://jitpack.io") // Cloudstream plugin'i için JitPack deposu
        maven("https://maven.pkg.github.com/LagradOst/CloudStream-Releases/") // Cloudstream'in release deposu
    }
}

// Mevcut settings.gradle.kts içeriğinizin geri kalanı buraya gelecek
// Örneğin:
// rootProject.name = "TestPlugins"
// include(":dizi", ":ExampleProvider", ":Pornhub", ":Ultima")
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

    // Cloudstream core API'sine bağımlılık (Bu, birçok Cloudstream yardımcı fonksiyonunu sağlar)
    implementation(project(":app"))

    // Rhino JavaScript motoru bağımlılığı (mozilla ve Scriptable hataları için)
    // Cloudstream'in kendi içinde bir JS motoru varsa bu gerekli olmayabilir,
    // ancak hata devam ederse bu satırı eklemeyi deneyin.
    implementation("org.mozilla:rhino:1.7.14") // En son stabil versiyonu kullanın

    // Diğer bağımlılıklar (eğer varsa)
}

android {
    // Android SDK versiyonları ve derleme ayarları
    compileSdk = 34 // Genellikle en son stabil versiyonu kullanın

    // Namespace'i burada tanımlayın, AndroidManifest.xml'den kaldırıldı
    namespace = "com.RowdyAvocado" // Ultima eklentinizin doğru paket adı

    defaultConfig {
        minSdk = 21 // Cloudstream için minimum desteklenen SDK
        targetSdk = 34 // Hedeflenen SDK versiyonu

        // BuildConfig alanları
        // API anahtarlarınızı BuildConfig'e eklemek için GitHub Secret'tan okunacak.
        val tmdbApiKey = System.getenv("TMDB_API_KEY") ?: ""
        buildConfigField("String", "TMDB_SECRET_API", "\"$tmdbApiKey\"")

        val simklClientId = System.getenv("SIMKL_CLIENT_ID") ?: ""
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"$simklClientId\"")

        val simklClientSecret = System.getenv("SIMKL_CLIENT_SECRET") ?: ""
        buildConfigField("String", "SIMKL_CLIENT_SECRET", "\"$simklClientSecret\"")

        val mdlApiKey = System.getenv("MDL_API_KEY") ?: ""
        buildConfigField("String", "MDL_API_KEY", "\"$mdlApiKey\"")
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

// Cloudstream özel yapılandırma bloğu
cloudstream {
    // Eklenti için genel bilgiler
    authors = listOf("RowdyAvocado") // Eklentinin yazarları
    language = "en" // Eklentinin desteklediği dil
    description = "Ultima plugin for Cloudstream" // Eklentinin kısa açıklaması

    /**
     * Eklentinin durumu (0: Kapalı, 1: Tamam, 2: Yavaş, 3: Beta)
     * Belirtilmezse varsayılan 3 (Beta) olur.
     **/
    status = 1 // Eklentinin durumu (örneğin, "Ok" yani çalışıyor)

    // Eklentinin desteklediği içerik türleri (örneğin, TvSeries, Movie, Anime)
    tvTypes = listOf("Movie", "TvSeries", "Anime")

    // Eklentinin ana simgesinin URL'si
    iconUrl = "https://raw.githubusercontent.com/RowdyAvocado/Ultima/master/Ultima.png"

    // Eklentinin dahili adı (build output dosya adı için kullanılır)
    internalName = "Ultima" // Bu isim, .cs3 dosyasının adı olacaktır (örn: Ultima.cs3)
}
