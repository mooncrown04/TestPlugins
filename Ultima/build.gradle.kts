// build.gradle.kts dosyanızın başında bu import'u ekleyin
import java.util.Properties

plugins {
    // ... diğer pluginleriniz
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
}

android {
    // ... diğer android yapılandırmalarınız
    namespace = "com.RowdyAvocado" // Doğru namespace'i ayarladığınızdan emin olun

    buildFeatures {
        buildConfig = true
    }

    // properties nesnesini burada tanımlayın ve yükleyin
    val properties = Properties().apply {
        // local.properties dosyasını projenin kök dizininde arar
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            propertiesFile.inputStream().use { load(it) }
        } else {
            // Eğer local.properties yoksa, GitHub Actions ortamında ortam değişkenlerini kullanabiliriz.
            // Bu kısım, secrets'ları doğrudan build.gradle.kts'e geçirmek için kullanılır.
            // Ancak, hassas verileri doğrudan build.gradle.kts'e gömmek yerine
            // secrets'ları GitHub Actions workflow'unuzda ortam değişkeni olarak ayarlamak daha güvenlidir.
            // Örneğin:
            // SIMKL_API: ${{ secrets.SIMKL_CLIENT_ID }}
            // MAL_API: ${{ secrets.MDL_API_KEY }}
            // gibi.
            // Bu durumda properties.getProperty() yerine System.getenv("SIMKL_API") kullanabilirsiniz.
        }
    }

    buildTypes {
        debug {
            // properties.getProperty() çağrıları için
            // eğer local.properties kullanıyorsanız bu şekilde kalabilir.
            buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
            buildConfigField("String", "MAL_API", "\"${properties.getProperty("MAL_API")}\"")
        }
        release {
            // Release build'leri için de aynı şekilde tanımlamanız gerekebilir
            buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
            buildConfigField("String", "MAL_API", "\"${properties.getProperty("MAL_API")}\"")
        }
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
}
// use an integer for version numbers
version = 41


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "The ultimate All-in-One home screen to access all of your extensions at one place (You need to select/deselect sections in Ultima's settings to load other extensions on home screen)"
    authors = listOf("RowdyRushya")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("All")

    requiresResources = true
    language = "en"

    // random cc logo i found
    iconUrl = "https://raw.githubusercontent.com/Rowdy-Avocado/Rowdycado-Extensions/master/logos/ultima.png"
}

android {
    defaultConfig {
   

        buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
        buildConfigField("String", "MAL_API", "\"${properties.getProperty("MAL_API")}\"")
    }
}
