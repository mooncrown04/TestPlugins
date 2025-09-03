/ İki farklı formatı işleyebilen yardımcı fonksiyon
// Erişim belirleyici private'dan public'e değiştirildi
public fun parseEpisodeInfo(text: String): Triple<String, Int?, Int?> {
    // Birinci format için regex: "Dizi Adı-Sezon. Sezon Bölüm. Bölüm(Ek Bilgi)"
    val format1Regex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")

    // İkinci format için regex: "Dizi Adı sXXeYY"
    val format2Regex = Regex("""(.*?)\s*s(\d+)e(\d+)""")

    // Üçüncü ve en önemli format için regex: "Dizi Adı Sezon X Bölüm Y"
    // Bu, "The Big Bang Theory Sezon 1 Bölüm 1" formatını yakalar.
    val format3Regex = Regex("""(.*?)\s*Sezon\s*(\d+)\s*Bölüm\s*(\d+).*""")

    // Formatları sırayla deniyoruz
    val matchResult1 = format1Regex.find(text)
    if (matchResult1 != null) {
        val (title, seasonStr, episodeStr) = matchResult1.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    val matchResult2 = format2Regex.find(text)
    if (matchResult2 != null) {
        val (title, seasonStr, episodeStr) = matchResult2.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    val matchResult3 = format3Regex.find(text)
    if (matchResult3 != null) {
        val (title, seasonStr, episodeStr) = matchResult3.destructured
        val season = seasonStr.toIntOrNull()
        val episode = episodeStr.toIntOrNull()
        return Triple(title.trim(), season, episode)
    }

    // Hiçbir format eşleşmezse, orijinal başlığı ve null değerleri döndür.
    return Triple(text.trim(), null, null)
}
