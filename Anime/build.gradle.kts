plugins {
    id("com.android.library") // veya "com.android.application" ihtiyacınıza göre
    kotlin("android")
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val apiKey = project.findProperty("tmdbApiKey")?.toString() ?: ""
        buildConfigField("String", "TMDB_SECRET_API", "\"$apiKey\"")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.lagradost.cloudstream3:cloudstream-tmdb:1.0.0")
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language    = "tr"
    description = "yabancı anime arşivi"
    status      = 1
    tvTypes     = listOf("TvSeries")
    iconUrl     = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powerdizi/powerdizi.png"
}
