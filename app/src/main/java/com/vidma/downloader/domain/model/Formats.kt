package com.vidma.downloader.domain.model

/**
 * User-facing format vocabulary + translation to yt-dlp format selectors.
 */

/** Quick quality presets shown on the Downloader screen (display order). */
enum class QualityPreset(val label: String, val height: Int?) {
    Auto("Auto · best", null),
    K4320("8K", 4320),
    K2160("4K", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
}

/** Presets in UI order (highest first, Auto pinned at front). */
fun qualityChoices(): List<QualityPreset> = QualityPreset.entries.toList()

/** Audio container options when extracting soundtracks. */
enum class AudioFormatPref(val label: String, val ytArg: String) {
    Mp3("MP3", "mp3"),
    M4a("M4A", "m4a"),
    Opus("Opus", "opus"),
    Flac("FLAC", "flac"),
    Wav("WAV", "wav"),
}

/** Container preference for merged video+audio output. */
enum class ContainerPref(val label: String, val ext: String?) {
    Auto("Auto", null),
    Mp4("MP4", "mp4"),
    Mkv("MKV", "mkv"),
    Webm("WebM", "webm"),
}

object FormatRules {

    /** yt-dlp selector for a preset height (or "best" when null). */
    fun videoSelector(height: Int?): String =
        if (height == null) {
            "bv*+ba/b"
        } else {
            "bestvideo[height<=$height]+bestaudio/best[height<=$height]/best"
        }

    /** Short human label stored on the task, e.g. "1080p · mp4" / "MP3 audio". */
    fun requestLabel(
        kind: MediaKind,
        preset: QualityPreset,
        container: ContainerPref,
        audio: AudioFormatPref,
    ): String = when (kind) {
        MediaKind.Audio -> "${audio.label} audio"
        MediaKind.Video -> {
            val base = if (preset == QualityPreset.Auto) "Best" else "${preset.height}p"
            "$base · ${container.ext ?: "mp4"}"
        }
    }
}
