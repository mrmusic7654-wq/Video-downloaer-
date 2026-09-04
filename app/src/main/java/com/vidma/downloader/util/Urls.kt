package com.vidma.downloader.util

import java.net.URI

/** URL helpers used by the paste box and the browser toolbar. */

/** Trim, strip stray quotes and add a scheme when missing. Null when hopeless. */
fun normalizeUrl(raw: String): String? {
    var url = raw.trim().trim('"', '\'', '“', '”', '‘', '’', ' ')
    if (url.isEmpty()) return null
    // strip trailing punctuation that often arrives with copied links
    url = url.trimEnd('.', ',', ';')
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        if (url.contains(" ") || !url.contains(".")) return null
        url = "https://$url"
    }
    return url
}

/** "https://www.youtube.com/watch?v=abc" → "youtube.com" */
fun hostOf(url: String): String = runCatching {
    val withScheme = if (url.startsWith("http")) url else "https://$url"
    val host = URI(withScheme).host ?: ""
    host.removePrefix("www.")
}.getOrDefault("")

/** Loose check: something that can be handed to a browser / yt-dlp. */
fun looksLikeUrl(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty() || t.contains(" ")) return false
    return t.startsWith("http://") || t.startsWith("https://") || t.contains(".")
}

/** Web-ish address (not about:/file:/data: pages). */
fun isWebPageUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.startsWith("about:") || lower.startsWith("file:") || lower.startsWith("data:")) return false
    if (lower.startsWith("http://") || lower.startsWith("https://")) return true
    return looksLikeUrl(url)
}
