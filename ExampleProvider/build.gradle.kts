version = 3

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Eksik AndroidX UI ve yardımcı kütüphaneleri BURAYA EKLENDİ!
    // Bu kütüphaneler, BlankFragment.kt dosyanızdaki "Unresolved reference" hatalarını çözecektir.
    implementation "androidx.core:core-ktx:1.13.1" // ResourcesCompat ve TextViewCompat gibi temel yardımcılar
    implementation "androidx.appcompat:appcompat:1.7.0" // Temel Android UI bileşenleri

    implementation "com.google.android.material:material:1.12.0" // BottomSheetDialogFragment ve diğer Material Design bileşenleri için
    implementation "androidx.fragment:fragment-ktx:1.7.1" // Fragment API'ları için (Material kütüphanesi bunu dolaylı olarak getirse de, açıkça belirtmek iyi bir pratik olabilir)

    implementation "androidx.annotation:annotation:1.8.0" // @RequiresApi gibi özel annotation'lar için

    // Projenizin diğer bağımlılıkları buraya eklenebilir
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // TMDB_SECRET_API'nin BuildConfig'e nasıl enjekte edildiği
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
