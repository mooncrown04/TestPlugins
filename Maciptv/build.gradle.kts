// TestPlugins/src/Maciptv/build.gradle.kts

// Configure the android extension for this specific module
import com.android.build.gradle.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.JavaVersion

plugins {
    // These plugins are applied locally if not applied globally in settings.gradle.kts or root build.gradle.kts.
    // 'com.android.library' and 'kotlin-android' are already applied in root's subprojects.
    // 'com.lagradost.cloudstream3.gradle' is also applied in root's subprojects.
    // So, only keep plugins that are specific to this module and not globally applied.
    // Assuming kotlin-parcelize and kotlin-kapt are not global:
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("com.lagradost.cloudstream3.gradle") // <-- Bu satır eklendi
    // id("org.jetbrains.kotlin.android") // This is already applied by root subprojects, no need to apply again
}

version = 3

// Configure the cloudstream extension for this specific module
configure<CloudstreamExtension> {
    authors = listOf("mooncrown") // Eklentinin yazarları güncellendi
    language = "en" // Eklentinin desteklediği dil
    description = "A Cloudstream3 plugin for Portal API based IPTV services." // Eklentinin kısa açıklaması

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     **/
    status = 1 // will be 3 if unspecified
    tvTypes = listOf("Live", "Movie") // Canlı TV ve Film türlerini destekler
    iconUrl = "https://raw.githubusercontent.com/YourUsername/YourRepo/master/icon.png" // Kendi ikon URL'nizi buraya ekleyin
    internalName = "Maciptv" // Dahili adı, .cs3 dosyasının adı olacaktır
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

    // OkHttp3 için bağımlılıklar (API istekleri için kullanılır)
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // En son stabil versiyonu kullanın
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0") // Opsiyonel, URLConnection ile uyumluluk için
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Opsiyonel, ağ isteklerini loglamak için

    // JSON ayrıştırma için (JSONObject kullanıldığı için)
    implementation("org.json:json:20231013") // En son stabil versiyonu kullanın

    // Eğer PortalApiProvider içinde WebViewResolver kullanılıyorsa bu bağımlılık gerekli olabilir:
    // implementation("com.lagradost.cloudstream3:webview:LATEST_CLOUDSTREAM_VERSION")
    // Lütfen Cloudstream'in WebView modülünün doğru versiyonunu ve bağımlılık yolunu kontrol edin.
}

// Configure the android extension for this specific module
configure<LibraryExtension> {
    compileSdk = 34 // Genellikle en son stabil versiyonu kullanın
    namespace = "com.moncrown" // Maciptv'nin doğru paket adı

    defaultConfig {
        minSdk = 21 // Cloudstream için minimum desteklenen SDK
        targetSdk = 34 // Hedeflenen SDK versiyonu

        // BuildConfig alanları (eğer API anahtarları veya benzeri kullanılıyorsa)
        // Örneğin, eğer PortalApiProvider içinde TMDB_SECRET_API gibi bir şey kullanacaksanız:
        // val tmdbApiKey = System.getenv("TMDB_API_KEY") ?: ""
        // buildConfigField("String", "TMDB_SECRET_API", "\"$tmdbApiKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Kotlin 1.8.x ve Gradle 8+ için genellikle 17 idealdir
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        buildConfig = true // BuildConfig sınıfı oluşturmayı etkinleştir
    }

    packaging { // packagingOptions yerine 'packaging' kullanıldı
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}
