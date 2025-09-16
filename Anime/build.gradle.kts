import java.util.Properties
import java.io.FileInputStream

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localProps.load(FileInputStream(localFile))
        }

        val apiKey = localProps.getProperty("tmdbApiKey") ?: ""
        buildConfigField("String", "TMDB_SECRET_API", "\"$apiKey\"")
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language    = "tr"
    description = "yabancı anime arşivi"

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
dependencies {
    implementation("com.lagradost.cloudstream3:cloudstream-tmdb:1.1.0")
}
