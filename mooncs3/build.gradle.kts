version = 3

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
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
    name.set("Galatasaray Font Eklentisi")
    description.set("Uygulamanın genel yazı tipini Galatasaray temasındaki özel font ile değiştirir.")
    authors.set(listOf("Aytac Afsar"))
    
    status.set(1) // Veya com.lagradost.cloudstream3.gradle.Status.Working
    tvTypes.set(listOf("Others"))
    iconUrl.set("https://raw.githubusercontent.com/.../icon.png")
}
