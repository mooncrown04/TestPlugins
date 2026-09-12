import com.lagradost.cloudstream3.gradle.CloudstreamExtension

// Üst taraftaki plugins ve dependencies bloklarından sonra:

configure<CloudstreamExtension> {
    name.set("Galatasaray Font Eklentisi")
    description.set("Uygulamanın genel yazı tipini Galatasaray temasındaki özel font ile değiştirir.")
    authors.set(listOf("Aytac Afsar"))
    
    status.set(1) // 1 = Working
    tvTypes.set(listOf("Others"))
    iconUrl.set("https://raw.githubusercontent.com/.../icon.png")
}
