import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // Hash yerine versiyon kullanarak Jitpack erişimini garantiliyoruz
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:master-SNAPSHOT")
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3")

configure<CloudstreamExtension> {
    authors = listOf("MoOnCrOwN")
    language = "tr"
    description = "Mooncrown IPTV ve Film Eklentisi"
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
}

android {
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
    // Cloudstream bağımlılığı (Versiyonu master-SNAPSHOT yaparak güncel tutuyoruz)
    compileOnly("com.github.LagradOst:CloudStream:master-SNAPSHOT")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.lagradost:ducktape:1.0.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
