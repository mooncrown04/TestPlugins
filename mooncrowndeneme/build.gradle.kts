import com.lagradost.cloudstream3.gradle.CloudstreamExtension

// 1. REPOLARI VE CLASSPATH'İ EL İLE TANIMLIYORUZ
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // En güncel sürümü açıkça belirtiyoruz
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:master-SNAPSHOT")
    }
}

// 2. PLUGINLERİ UYGULUYORUZ (id yerine apply kullanıyoruz)
apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3")

version = 1 

cloudstream {
    authors     = listOf("MoOnCrOwN") 
    language    = "tr"
    description = "GitHub üzerinden dinamik olarak güncellenen Mooncrown sinema arşivi."

    status  = 1 
    tvTypes = listOf("Movie", "TvSeries") 
    
    iconUrl = "https://raw.githubusercontent.com/mooncrown04/TestPlugins/master/icon.png"
}

android {
    namespace = "com.example"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    // Cloudstream SDK'nın kendisi
    implementation("com.github.LagradOst:CloudStream:3.0.0") 
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JS motoru (Loglardaki hatayı çözen kısım)
    implementation("com.lagradost:ducktape:1.0.4")
}
