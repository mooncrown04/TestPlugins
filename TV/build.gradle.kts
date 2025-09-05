plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.plugin")
}

android {
    namespace = "com.MoOnCrOwNTV"
    compileSdkVersion(34)

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(34)

        val apiKey = project.findProperty("tmdbApiKey")?.toString() ?: ""
        buildConfigField("String", "TMDB_SECRET_API", "\"$apiKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Hatanın kaynağı olan kütüphane bu satırda ekleniyor.
    implementation("com.lagradost.cloudstream3:cloudstream3-api:latest.integration")
}

cloudstream {
    authors = listOf("MoOnCrOwN", "GitLatte", "patr0nq", "keyiflerolsun")
    language = "tr"
    description = "MoOnCrOwN TV"
    status = 1
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powerdizi/powerdizi.png"
}
