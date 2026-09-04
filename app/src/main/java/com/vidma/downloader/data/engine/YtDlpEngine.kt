package com.vidma.downloader.data.engine

import android.content.Context
import com.vidma.downloader.domain.model.MediaSummary
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thin, suspend-friendly wrapper around the youtubedl-android library.
 *
 * The library bundles the real yt-dlp + python runtime and ffmpeg inside the
 * APK. [initialize] is slow on first launch (native assets are unpacked), so
 * it runs once in the background and is guarded by [ready].
 */
object YtDlpEngine {

    private val initMutex = Mutex()
    private var initialized = false

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Idempotent, thread-safe initialization. Safe to call from any coroutine.
     */
    suspend fun initialize(context: Context) {
        initMutex.withLock {
            if (initialized) return
            val app = context.applicationContext
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            initialized = true
            _ready.value = true
        }
    }

    /** True when the underlying binaries finished unpacking. */
    fun isInitialized(): Boolean = initialized

    /**
     * Resolve metadata for a single URL. Runs the real yt-dlp
     * `--dump-json` pipeline (blocking) on the calling (IO) context.
     */
    suspend fun fetchInfo(context: Context, url: String): MediaSummary {
        initialize(context)
        val request = YoutubeDLRequest(url.trim())
        request.addOption("--no-playlist")
        val info = YoutubeDL.getInstance().getInfo(request)

        val heights = info.formats
            ?.mapNotNull { f -> f.height.takeIf { it > 0 } }
            ?.distinct()
            ?.sortedDescending()
            ?: emptyList()

        return MediaSummary(
            url = url.trim(),
            title = info.title?.takeIf { it.isNotBlank() }
                ?: info.fulltitle?.takeIf { it.isNotBlank() }
                ?: "Untitled media",
            uploader = info.uploader,
            durationSec = info.duration.coerceAtLeast(0),
            thumbnailUrl = info.thumbnail,
            extractor = info.extractorKey ?: info.extractor,
            availableHeights = heights,
            viewCount = info.viewCount?.replace(",", "")?.toLongOrNull(),
            likeCount = info.likeCount?.replace(",", "")?.toLongOrNull(),
        )
    }

    /**
     * Runs a full download (blocking). [onProgress] receives
     * (percent, etaSeconds, rawLine); percent/eta are -1 until known.
     * [processId] allows cancellation via [YoutubeDL.destroyProcessById].
     */
    suspend fun execute(
        context: Context,
        url: String,
        kindSelector: (YoutubeDLRequest) -> Unit,
        processId: String,
        onProgress: (Float, Long, String) -> Unit,
    ): Int {
        initialize(context)
        val request = YoutubeDLRequest(url.trim())
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--retries", 3)
        request.addOption("--fragment-retries", 5)
        request.addOption("--socket-timeout", 20)
        request.addOption("--concurrent-fragments", 4)
        request.addOption("--write-thumbnail")
        kindSelector(request)
        val response = YoutubeDL.getInstance().execute(
            request,
            processId,
        ) { progress, eta, line ->
            onProgress(progress, eta, line)
        }
        return response.exitCode
    }

    fun cancel(processId: String): Boolean =
        YoutubeDL.getInstance().destroyProcessById(processId)
}
