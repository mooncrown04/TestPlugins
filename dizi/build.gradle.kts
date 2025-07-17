version = 3

dependencies {
    // Tüm bağımlılıkları parantez () içine alın!
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX UI ve yardımcı kütüphaneler
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")

    implementation("androidx.annotation:annotation:1.8.0")

    // Buraya projenizdeki diğer bağımlılıkları ekleyebilirsiniz
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

cloudstream {
    authors     = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language    = "tr"
    description = "powerboard`un yabancı dizi arşivi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powerdizi/powerdizi.png"
}
