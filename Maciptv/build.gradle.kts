plugins {
    id("cloudstream") version "1.0.0"
}

version = 1

repositories {
    mavenCentral()
    google()
    maven(url = "https://jitpack.io")
    maven("https://maven.pkg.github.com/recloudstream/cloudstream")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

cloudstream {
    authors = listOf("mooncrown04")
    language = "tr"
    description = "Maciptv portalı üzerinden canlı yayın eklentisi"
    status = 1
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/maciptv/maciptv.png"
}
