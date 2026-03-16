import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // Jitpack 401 hatasını aşmak için sabit commit ID kullanıyoruz
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:f1134267e7")
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3")

// Proje Versiyonu
version = 1 

cloudstream {
    // metadata ayarları
    authors     = listOf("MoOnCrOwN")
    language    = "tr"
    description = "GitHub üzerinden dinamik olarak güncellenen Mooncrown sinema arşivi."

    status  = 1 // 1: Beta, 2: Stable
    tvTypes = listOf("Movie", "TvSeries") 
    
    iconUrl = "https://raw.githubusercontent.com/mooncrown04/TestPlugins/master/icon.png"
}

android {
    namespace = "com.mooncrown.deneme" // Benzersiz bir paket adı
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
    val cloudstreamVersion = "3.0.0"
    
    // Temel Cloudstream kütüphaneleri
    compileOnly("com.github.LagradOst:CloudStream:$cloudstreamVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JS ve JSON işleme için gerekli bağımlılıklar
    implementation("com.lagradost:ducktape:1.0.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
