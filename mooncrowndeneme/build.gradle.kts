import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        // JitPack alternatifleri ve ana repo
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/LagradOst/CloudStream-Releases")
    }
    dependencies {
        // En güncel ve stabil SNAPSHOT yerine doğrudan sürüm aramaya zorlayalım
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:master-SNAPSHOT")
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3")

cloudstream {
    authors     = listOf("MoOnCrOwN")
    language    = "tr"
    description = "GitHub üzerinden dinamik olarak güncellenen Mooncrown sinema arşivi."
    status      = 1
    tvTypes     = listOf("Movie", "TvSeries")
    iconUrl     = "https://raw.githubusercontent.com/mooncrown04/TestPlugins/master/icon.png"
}

android {
    namespace = "com.mooncrown.deneme"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    // Cloudstream kütüphanesini 'compileOnly' yaparak ana uygulama ile çakışmasını önleyelim
    compileOnly("com.github.LagradOst:CloudStream:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JS motoru ve veri işleme
    implementation("com.lagradost:ducktape:1.0.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
