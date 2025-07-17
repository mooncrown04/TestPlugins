version = 3

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX UI ve yardımcı kütüphaneler (Bunlar eksikti!)
    // Temel AndroidX bileşenleri ve uyumluluk sınıfları için
    implementation "androidx.core:core-ktx:1.13.1"
    implementation "androidx.appcompat:appcompat:1.7.0"

    // BottomSheetDialogFragment ve Material Design bileşenleri için
    implementation "com.google.android.material:material:1.12.0"

    // Fragment ile ilgili uzantılar için (Material kütüphanesi bunu içerse de, açıkça eklemek sorunları önleyebilir)
    implementation "androidx.fragment:fragment-ktx:1.7.1"

    // @RequiresApi gibi annotation'lar için
    implementation "androidx.annotation:annotation:1.8.0"

    // Buraya projenizdeki diğer bağımlılıkları ekleyebilirsiniz
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // tmdbApiKey adında bir proje özelliği bulunamazsa varsayılan olarak boş dize kullanır
        val apiKey = project.findProperty("tmdbApiKey")?.toString() ?: ""
        buildConfigField("String", "TMDB_SECRET_API", "\"$apiKey\"")
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
