// settings.gradle.kts

pluginManagement {
    repositories {
        gradlePluginPortal() // Gradle'ın kendi plugin portalı
        mavenCentral()       // Yaygın Maven deposu
        google()             // Google'ın Android için deposu
        maven("https://jitpack.io") // JitPack, Cloudstream'in bazı bağımlılıklarını barındırabilir
        // Cloudstream'in kendi plugin deposu: BU ÇOK ÖNEMLİ!
        maven("https://maven.pkg.github.com/LagradOst/CloudStream-Releases/")
    }
}




dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/LagradOst/CloudStream-Releases/")
    }
}









rootProject.name = "CloudstreamPlugins"

// Bu dosya, hangi projelerin dahil edildiğini ayarlar.
// Tüm yeni projeler, "disabled" değişkeninde belirtilmedikçe otomatik olarak dahil edilmelidir.

val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}

// Yalnızca tek bir projeyi dahil etmek için, önceki satırları (ilk hariç) yorumlayın ve eklentinizi şöyle dahil edin:
include( ":TV",":dizi",":Sinema")
