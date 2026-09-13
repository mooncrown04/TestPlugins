plugins {
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    name        = "Galatasaray Font Eklentisi"
    description = "Uygulamanın genel yazı tipini Galatasaray temasındaki özel font ile değiştirir."
    authors     = listOf("Aytac Afsar")
    
    status      = 1
    tvTypes     = listOf("Others")
    iconUrl     = "https://raw.githubusercontent.com/.../icon.png"
}
