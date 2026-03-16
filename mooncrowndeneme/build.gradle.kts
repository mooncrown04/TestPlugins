// Eklentinin versiyonu. GitHub'da JS'yi değiştirmen bunu etkilemez, 
// ama eklenti altyapısında büyük bir değişiklik yaparsan bu sayıyı artırırsın.
version = 1 

cloudstream {
    // Kendi adını veya ekibini yazabilirsin
    authors     = listOf("MoOnCrOwN") 
    language    = "tr"
    // Açıklamayı sistemine uygun yap
    description = "GitHub üzerinden dinamik olarak güncellenen Mooncrown sinema arşivi."

    status  = 1 // 1: Aktif ve Sorunsuz demek
    tvTypes = listOf("Movie") // Eğer dizi de ekleyeceksen "TvSeries" ekleyebilirsin
    
    // Kendi logon varsa onun linkini koy, yoksa şimdilik bu kalabilir
    iconUrl = "https://raw.githubusercontent.com/mooncrown04/TestPlugins/master/icon.png"
}
