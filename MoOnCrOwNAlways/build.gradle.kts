plugins {
kotlin("jvm")
id("com.android.library")
id("kotlin-android")
id("com.lagradost.cloudstream3.gradle")
kotlin("plugin.serialization")
}

dependencies {
implementation(project(":plugins"))
implementation(kotlin("stdlib"))
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
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
authors = listOf("MoOnCrOwN","GitLatte", "patr0nq", "keyiflerolsun")
language = "tr"
description = "powerboard`un sinema arşivi"
status = 1
tvTypes = listOf("Movie")
iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powersinema/powersinema.png"
}
