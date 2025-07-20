// build.gradle.kts (Projenizin kök dizininde)

// Gradle'ın kendisi için gerekli plugin'lerin ve bağımlılıkların tanımlandığı blok.
// Bu, "Unresolved reference: import" ve "Expecting an element" hatalarını çözecektir.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // Cloudstream plugin'i için JitPack deposu
        maven("https://maven.pkg.github.com/LagradOst/CloudStream-Releases/") // Cloudstream'in release deposu
    }
    dependencies {
        // Android Gradle Plugin (AGP) - Kendi Gradle versiyonunuza uygun olanı kullanın
        classpath("com.android.tools.build:gradle:8.1.0") // Örnek: Gradle 8.1.0 için
        // Kotlin Gradle Plugin - Kendi Kotlin versiyonunuza uygun olanı kullanın
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0") // Örnek: Kotlin 1.8.0 için
        // Cloudstream Gradle Plugin - Kendi Cloudstream plugin versiyonunuza uygun olanı kullanın
        classpath("com.lagradost.cloudstream3:gradle-plugin:1.0.0") // Örnek: Cloudstream plugin versiyonu
    }
}

// Kök projeye uygulanacak plugin'ler. Eğer kök proje bir Android uygulaması ise bu gereklidir.
plugins {
    id("com.android.application") // Eğer kök proje bir Android uygulaması ise
    id("org.jetbrains.kotlin.android") // Eğer kök proje Kotlin kullanıyorsa
    // Eğer kök proje Cloudstream özelliklerini doğrudan kullanıyorsa, buraya da eklenebilir:
    // id("com.lagradost.cloudstream3.gradle")
}

// Tüm projeler (kök ve alt projeler) için ortak yapılandırmalar
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // Cloudstream plugin'i için JitPack deposu
        maven("https://maven.pkg.github.com/LagradOst/CloudStream-Releases/") // Cloudstream'in release deposu
    }
}

// Kök projenin Android yapılandırması (eğer kök proje bir Android uygulaması ise)
android {
    namespace = "com.mooncrown04.cloudstream" // Kök projenizin paket adı
    compileSdk = 34 // Genellikle en son stabil versiyonu kullanın

    defaultConfig {
        minSdk = 21 // Cloudstream için minimum desteklenen SDK
        targetSdk = 34 // Hedeflenen SDK versiyonu
        versionCode = 1 // Uygulamanızın versiyon kodu
        versionName = "1.0" // Uygulamanızın versiyon adı
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Test runner
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

    packaging {
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/*.txt")
    }
}

// Kotlin derleme görevleri için JVM hedefi ayarı
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}
