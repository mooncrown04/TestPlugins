buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        // master-SNAPSHOT yerine sabit bir sürüm veya commit deniyoruz
        classpath("com.github.LagradOst:CloudStream-Gradle-Plugin:966299f061") 
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3")

cloudstream {
    authors     = listOf("MoOnCrOwN")
    language    = "tr"
    description = "Mooncrown deneme eklentisi."
    status      = 1
    tvTypes     = listOf("Movie")
}

android {
    namespace = "com.mooncrown.deneme"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    // Ana Cloudstream kütüphanesi
    val csVersion = "657155668e" 
    compileOnly("com.github.LagradOst:CloudStream:$csVersion")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.lagradost:ducktape:1.0.4")
}
