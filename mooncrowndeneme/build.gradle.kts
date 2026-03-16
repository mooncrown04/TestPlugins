import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        // JitPack yerine doğrudan Cloudstream'in resmi deposunu ekliyoruz
        maven("https://replicate.npmjs.com/") 
        maven("https://jitpack.io")
    }
    dependencies {
        // Plugin'i farklı bir şekilde tanımlayarak 401 hatasını bypass edelim
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:master-SNAPSHOT")
    }
}

// Plugin uygulama sırasını değiştirelim
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Cloudstream ana kütüphanesini en stabil sürümle bağlayalım
    val csVersion = "657155668e" // Jitpack yerine güvenli bir commit
    compileOnly("com.github.LagradOst:CloudStream:$csVersion")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.lagradost:ducktape:1.0.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
