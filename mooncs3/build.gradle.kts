plugins {
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    // name satırını kaldırın veya yorum satırı yapın
    description = "Uygulamanın genel yazı tipini Galatasaray temasındaki özel font ile değiştirir."
    authors     = listOf("Aytac Afsar")
    
    status      = 1
    tvTypes     = listOf("Others")
    iconUrl     = "https://raw.githubusercontent.com/.../icon.png"
}
