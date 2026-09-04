package com.vidma.downloader.util

import java.util.Locale

/** Human-friendly formatters shared by every screen. */

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatCount(count: Long?): String {
    if (count == null) return ""
    return when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

fun timeAgo(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    if (diff < 60_000) return "just now"
    val minutes = diff / 60_000
    if (minutes < 60) return "$minutes min ago"
    val hours = minutes / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val days = hours / 24
    return if (days == 1L) "yesterday" else "$days days ago"
}
