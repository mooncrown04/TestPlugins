

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
//include(":dizi",":  ",":Sinema")
