    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = cookies).document // Çerezler korunuyor

        val title = document.selectFirst("h1.title span[class='inlineFree']")?.text()?.trim() ?: "" // İkinci dosyadan alınan seçici
        val poster: String? = document.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: document.selectFirst("head meta[property=og:image]")?.attr("content") // Orijinal dosyadan korundu

        // İkinci dosyadan alınan yıl, derecelendirme ve süre çekimi
        val year = Regex("""uploadDate": "(\d+)""").find(document.html())?.groupValues?.get(1)?.toIntOrNull()
        val rating = document.selectFirst("span.percent")?.text()?.first()?.toString()?.toRatingInt()
        val duration = Regex("duration' : '(.*)',").find(document.html())?.groupValues?.get(1)?.toIntOrNull()

        val tags = document.select("div.categoriesWrapper a[data-label='Category']") // İkinci dosyadan alınan seçici
            .map { it?.text()?.trim().toString().replace(", ", "") } // Orijinal dosyadan formatlama korunuyor

        val recommendations = document.selectXpath("//a[contains(@class, 'img')]").mapNotNull { // İkinci dosyadan alınan seçici
            val recName = it.attr("title").trim()
            val recHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("src")) // İkinci dosyadan alınan seçici
            MovieSearchResponse(recName, recHref, TvType.NSFW) {
                this.posterUrl = recPosterUrl
            }
        }

        val actors =
            document.select("div.pornstarsWrapper a[data-label='Pornstar']") // İkinci dosyadan alınan seçici
                .mapNotNull {
                    // İkinci dosyadan Actor nesnesi oluşturma mantığı
                    Actor(it.text().trim(), it.select("img").attr("src")) // İkinci dosyada img.attr("src") vardı, bu da aktör posteri olabilir
                }
