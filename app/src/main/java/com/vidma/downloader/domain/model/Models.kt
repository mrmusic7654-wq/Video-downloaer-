package com.vidma.downloader.domain.model

import kotlin.math.roundToInt

/**
 * Pure domain models — no Android or yt-dlp types reach this package.
 */

/** Convenience progress formatting used by UIs. */
fun DownloadTask.percentText(): String =
    if (progress <= 0f) "0%" else "${(progress * 100).roundToInt().coerceIn(0, 100)}%"

enum class MediaKind { Video, Audio }

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
