import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // master-SNAPSHOT yerine çalışan stabil commit
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:f0f1c4293a")
    }
}

// Pluginleri güvenli yoldan apply ediyoruz
apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3")

configure<CloudstreamExtension> {
    authors = listOf("MoOnCrOwN")
    language = "tr"
    description = "Mooncrown Deneme Eklentisi"
    // 0: Belirsiz, 1: Çalışıyor, 2: Bozuk, 3: Beta
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
}

android {
    // Gradle 8+ için namespace zorunludur
    namespace = "com.mooncrown.deneme"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Cloudstream ana kütüphanesi (Derleme için gerekli)
    val csVersion = "657155668e" 
    compileOnly("com.github.LagradOst:CloudStream:$csVersion")
    
    // Yardımcı kütüphaneler
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.lagradost:ducktape:1.0.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
