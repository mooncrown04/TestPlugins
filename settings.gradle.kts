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

rootProject.name = "CloudstreamPlugins"

// Bu dosya, hangi projelerin dahil edildiğini ayarlar.
// Tüm yeni projeler, "disabled" değişkeninde belirtilmedikçe otomatik olarak dahil edilmelidir.

// Otomatik dahil etme bloğunu yorumladık, çünkü manuel include kullanılıyor.
// Eğer otomatik dahil etmeyi kullanmak isterseniz, aşağıdaki yorumları kaldırın
// ve manuel include satırını yorumlayın.
/*
val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
*/

// Yalnızca tek bir projeyi dahil etmek için, önceki satırları (ilk hariç) yorumlayın ve eklentinizi şöyle dahil edin:
// Modüllerin src klasörü altında olduğunu belirten öneki kaldırdık, çünkü settings.gradle.kts zaten src klasörünün içinde.
include(":app")
include(":Maciptv",":dizi",":Pornhub",":Sinema")
