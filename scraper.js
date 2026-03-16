// GitHub: scraper.js

function search(query) {
    // Burada istediğin siteyi kazıyabilirsin
    // Örnek olarak FilmModu mantığını buraya taşıyabilirsin
    var html = httpGet("https://www.filmmodu.nl/film-ara?term=" + query);
    
    // JSON formatında sonuç döndür
    return [
        {
            "title": "GitHub'dan Gelen Film",
            "url": "https://site.com/film-linki",
            "poster": "https://poster-linki.com/img.jpg"
        }
    ];
}

function load(url) {
    return {
        "title": "Dinamik Detay",
        "plot": "Bu veri GitHub üzerindeki JS dosyasından geldi!",
        "year": 2026,
        "poster": "..."
    };
}
