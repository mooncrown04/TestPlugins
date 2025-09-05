package com.MoOnCrOwNTV

import java.io.InputStream

data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null
)

class IptvPlaylistParser {
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (!reader.readLine().isExtendedM3u()) throw Exception("Invalid M3U header")

        val playlistItems = mutableListOf<PlaylistItem>()
        var currentIndex = 0
        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    val item = playlistItems[currentIndex]
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                    val referrer = line.getTagValue("http-referrer")

                    val headers = mutableMapOf<String, String>()
                    if (userAgent != null) headers["user-agent"] = userAgent
                    if (referrer != null) headers["referrer"] = referrer

                    playlistItems[currentIndex] = item.copy(userAgent = userAgent, headers = headers)
                } else if (!line.startsWith("#")) {
                    val item = playlistItems[currentIndex]
                    val url = line.getUrl()
                    val userAgent = line.getUrlParameter("user-agent")
                    val referrer = line.getUrlParameter("referer")
                    val urlHeaders = if (referrer != null) item.headers + mapOf("referrer" to referrer) else item.headers

                    playlistItems[currentIndex] = item.copy(
                        url = url,
                        headers = item.headers + urlHeaders,
                        userAgent = userAgent ?: item.userAgent
                    )
                    currentIndex++
                }
            }
            line = reader.readLine()
        }

        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String = replace("\"", "").trim()
    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)
    private fun String.getTitle(): String? = split(",").lastOrNull()?.replaceQuotesAndTrim()
    private fun String.getUrl(): String? = split("|").firstOrNull()?.replaceQuotesAndTrim()

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
        }.toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}
