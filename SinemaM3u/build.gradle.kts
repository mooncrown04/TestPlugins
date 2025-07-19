// TestPlugins/src/ExampleProvider/build.gradle.kts

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
  val properties = Properties().apply {
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            propertiesFile.inputStream().use { load(it) }
        }
    }
    buildTypes {
        debug {
            // TMDB_SECRET_API'yi BuildConfig'e ekleyin
            buildConfigField("String", "TMDB_SECRET_API", "\"${properties.getProperty("TMDB_SECRET_API") ?: ""}\"")
        }
        release {
            buildConfigField("String", "TMDB_SECRET_API", "\"${properties.getProperty("TMDB_SECRET_API") ?: ""}\"")
        }
    }
}

cloudstream {
    authors       = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language      = "tr"
    description = "powerboard`un sinema arşivi"

    /**
     * Durum int'i aşağıdaki gibidir:
     * 0: Kapalı
     * 1: Tamam
     * 2: Yavaş
     * 3: Sadece Beta
     **/
    status  = 1 // belirtilmezse 3 olur
    tvTypes = listOf("Movie")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/master/img/powersinema/powersinema.png"
}
