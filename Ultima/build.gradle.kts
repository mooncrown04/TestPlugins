// TestPlugins/src/Ultima/build.gradle.kts

// Import necessary extensions for configuration blocks
import com.android.build.gradle.LibraryExtension // For android {} block configuration
import com.lagradost.cloudstream3.gradle.CloudstreamExtension // For cloudstream {} block configuration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget // For jvmTarget in kotlinOptions
import org.gradle.api.JavaVersion // For JavaVersion in compileOptions

plugins {
    // These plugins are applied locally if not applied globally in settings.gradle.kts or root build.gradle.kts.
    // 'com.android.library' and 'kotlin-android' are already applied in root's subprojects.
    // 'com.lagradost.cloudstream3.gradle' is also applied in root's subprojects.
    // So, only keep plugins that are specific to this module and not globally applied.
    // Assuming kotlin-parcelize and kotlin-kapt are not global:
    id("kotlin-parcelize")
    id("kotlin-kapt")
    // id("org.jetbrains.kotlin.android") // This is already applied by root subprojects, no need to apply again
}

version = 3

// Configure the cloudstream extension for this specific module
configure<CloudstreamExtension> {
    authors = listOf("RowdyAvocado") // Eklentinin yazarları
    language = "en" // Eklentinin desteklediği dil
    description = "Ultima plugin for Cloudstream" // Eklentinin kısa açıklaması

    /**
     * Eklentinin durumu (0: Kapamalı, 1: Tamam, 2: Yavaş, 3: Beta)
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

// Configure the android extension for this specific module
configure<LibraryExtension> {
    compileSdk = 34 // Bu, root'taki compileSdkVersion(35) ile çakışabilir, ancak şimdilik bırakıldı.
    namespace = "com.RowdyAvocado" // Root'taki "com.example" namespace'ini geçersiz kılar

    defaultConfig {
        minSdk = 21
        targetSdk = 34 // Bu, root'taki targetSdk = 35 ile çakışabilir, ancak şimdilik bırakıldı.

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { // kotlinOptions artık doğru bağlamda
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures { // buildFeatures artık doğru bağlamda
        buildConfig = true
    }

    packagingOptions {
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}
