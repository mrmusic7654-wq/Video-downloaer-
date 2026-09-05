package com.vidma.downloader.domain.model

import kotlin.math.roundToInt

/**
 * Pure domain models — no Android or yt-dlp types reach this package.
 */

/** Convenience progress formatting used by UIs. */
fun DownloadTask.percentText(): String =
    if (progress <= 0f) "0%" else "${(progress * 100).roundToInt().coerceIn(0, 100)}%"

enum class MediaKind { Video, Audio }

/** Lifecycle of the bundled yt-dlp runtime (unpack → ready / failed). */
sealed interface EngineStatus {
    /** First launch: the Python/yt-dlp payload is still unpacking. */
    data object Initializing : EngineStatus

    data object Ready : EngineStatus

    data class Failed(val message: String) : EngineStatus
}

/** A media element discovered on a browser page (a real <video>/<audio> tag). */
data class PageMediaSource(
    val kind: MediaKind,
    val url: String,
    val title: String? = null,
    val poster: String? = null,
) {
    /** URL without query string / fragment (what determines the file type). */
    val path: String get() = url.substringBefore('?').substringBefore('#')

    /** True for plain media files an HTTP client can fetch directly. */
    val isDirectFile: Boolean
        get() {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in DIRECT_MEDIA_EXTS
        }

    /** True for streaming manifests (HLS/DASH) that the engine understands. */
    val isManifest: Boolean
        get() = path.lowercase().contains(".m3u8") || path.lowercase().contains(".mpd")

    /** MSE blobs live inside the WebView — not fetchable by the app process. */
    val isBlob: Boolean get() = url.startsWith("blob:")

    private companion object {
        val DIRECT_MEDIA_EXTS = setOf(
            "mp4", "m4v", "webm", "mov", "mkv", "avi", "3gp", "3g2", "ts",
            "ogv", "flv", "wmv", "mpg", "mpeg", "f4v",
            "mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav",
        )
    }
}

/**
 * Everything the browser hand-off passes to the download queue: the page it
 * came from, a streaming manifest found on it (better engine target) and a
 * direct media file (fast OkHttp path that works on any site).
 */
data class CaptureRequest(
    val pageUrl: String,
    val manifestUrl: String? = null,
    val directUrl: String? = null,
    val title: String? = null,
    val cover: String? = null,
) {
    /** The URL the engine should resolve (manifest when present, else the page). */
    val engineTarget: String get() = manifestUrl ?: pageUrl
}

/** Lifecycle of one download task. */
enum class DownloadState {
    /** Waiting for an engine slot (not yet used — kept for queue semantics). */
    Queued,

    /** yt-dlp is extracting metadata / resolving the URL. */
    Resolving,

    /** Media bytes are being fetched (percent-driven). */
    Downloading,

    /** ffmpeg post-processing (merge / audio extraction). */
    Processing,

    /** Copying the finished file into public storage. */
    Finishing,

    Completed,
    Failed,
    Cancelled,
}

/** In-memory representation of an ongoing download task (runtime state). */
data class DownloadTask(
    val id: String,
    val url: String,
    val title: String? = null,
    val kind: MediaKind = MediaKind.Video,
    /** Human label of what was requested, e.g. "1080p · mp4". */
    val requestLabel: String = "",
    val state: DownloadState = DownloadState.Queued,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    /** Last raw progress line from the engine (nice console feel). */
    val statusLine: String = "",
    val error: String? = null,
    /** Cover: local file path or https URL, when known. */
    val coverUrl: String? = null,
    val startedAtMs: Long = 0L,
) {
    val isActive: Boolean
        get() = state == DownloadState.Queued || state == DownloadState.Resolving ||
            state == DownloadState.Downloading || state == DownloadState.Processing ||
            state == DownloadState.Finishing

    val isFailed: Boolean get() = state == DownloadState.Failed
}

/** A finished, library-grade media item. */
data class LibraryItem(
    val id: String,
    val url: String,
    val title: String,
    val kind: MediaKind,
    val ext: String,
    /** Absolute path for app-private files or a content:// URI for public ones. */
    val filePath: String,
    val coverUri: String? = null,
    val sizeBytes: Long = 0L,
    val addedAtMs: Long = 0L,
    val durationSec: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val inPublicStorage: Boolean = true,
)

/** What the "Download" hero needs to know about a pasted URL. */
data class MediaSummary(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val durationSec: Int = 0,
    val thumbnailUrl: String? = null,
    val extractor: String? = null,
    /** Resolutions actually offered by the extractor (descending). */
    val availableHeights: List<Int> = emptyList(),
    val viewCount: Long? = null,
    val likeCount: Long? = null,
) {
    val platformLabel: String
        get() = extractor?.takeIf { it.isNotBlank() } ?: "Web"
}

/**
 * Normalised position for the browser's floating download action.
 * Storing fractions instead of pixels keeps the placement stable across
 * phones, tablets, rotation and font/display-size changes.
 */
data class FabPosition(
    val xFraction: Float = 0.88f,
    val yFraction: Float = 0.82f,
) {
    fun clamped(): FabPosition = FabPosition(
        xFraction = xFraction.coerceIn(0f, 1f),
        yFraction = yFraction.coerceIn(0f, 1f),
    )
}
