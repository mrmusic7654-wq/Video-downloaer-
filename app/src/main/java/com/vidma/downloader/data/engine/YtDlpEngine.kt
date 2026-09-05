package com.vidma.downloader.data.engine

import android.content.Context
import com.vidma.downloader.domain.model.MediaSummary
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thin, suspend-friendly wrapper around youtubedl-android.
 *
 * The library contains the yt-dlp/Python runtime. FFmpeg is intentionally an
 * optional build capability: the lean arm64 release does not ship another
 * native binary, so it can stay close to the 20–30 MB download target. When a
 * full build is made with -Pvidma.withFfmpeg=true, post-processing and audio
 * conversion are enabled automatically through the same API.
 */
object YtDlpEngine {

    private val initMutex = Mutex()
    @Volatile private var initialized = false

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _ffmpegReady = MutableStateFlow(false)
    /** True only when the optional FFmpeg artifact was packaged and initialized. */
    val ffmpegReady: StateFlow<Boolean> = _ffmpegReady.asStateFlow()

    private val _lastInitError = MutableStateFlow<String?>(null)
    val lastInitError: StateFlow<String?> = _lastInitError.asStateFlow()

    /**
     * Idempotent, thread-safe initialization. It is called from Application
     * startup and again by every engine operation, so a user can paste a link
     * immediately while the first-run unpack is still in progress.
     */
    suspend fun initialize(context: Context) {
        initMutex.withLock {
            if (initialized) return
            val app = context.applicationContext
            try {
                YoutubeDL.getInstance().init(app)

                // FFmpeg is an optional dependency in the lean build. Reflection
                // keeps that build linkable while still enabling the full build.
                _ffmpegReady.value = initializeOptionalFfmpeg(app)
                initialized = true
                _lastInitError.value = null
                _ready.value = true
            } catch (error: Throwable) {
                initialized = false
                _ready.value = false
                // Surface the *root* cause, not just the wrapper message. The
                // youtubedl init wraps the real error (e.g. a stripped
                // raw/ytdlp resource or missing native lib) as "failed to
                // initialize", which alone is useless for debugging.
                _lastInitError.value = describeThrowable(error)
                android.util.Log.e("VidmaEngine", "yt-dlp engine init failed", error)
                throw error
            }
        }
    }

    private fun initializeOptionalFfmpeg(context: Context): Boolean = runCatching {
        val type = Class.forName("com.yausername.ffmpeg.FFmpeg")
        val instance = type.getMethod("getInstance").invoke(null)
        type.getMethod("init", Context::class.java).invoke(instance, context)
        true
    }.getOrDefault(false)

    /** True when the underlying binaries finished unpacking. */
    fun isInitialized(): Boolean = initialized

    /** Builds a short, diagnostic description walking the whole cause chain. */
    private fun describeThrowable(error: Throwable): String {
        val parts = ArrayList<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4) {
            val msg = current.message?.takeIf { it.isNotBlank() }
                ?: current.javaClass.simpleName
            if (parts.isEmpty() || parts.last() != msg) parts.add(msg)
            current = current.cause
            depth++
        }
        return parts.joinToString(" → ").take(240)
    }

    /**
     * Resolve metadata for a single URL. Runs the real yt-dlp --dump-json
     * pipeline on the caller's IO context.
     */
    suspend fun fetchInfo(context: Context, url: String): MediaSummary {
        initialize(context)
        val request = YoutubeDLRequest(url.trim()).apply {
            addOption("--no-playlist")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--extractor-retries", 2)
            addOption("--socket-timeout", 30)
        }
        val info = YoutubeDL.getInstance().getInfo(request)

        val heights = info.formats
            ?.mapNotNull { format -> format.height.takeIf { it > 0 } }
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
            // youtubedl-android has exposed these fields as both strings and
            // numbers across releases. Parsing Any keeps upgrades compatible.
            viewCount = parseCount(info.viewCount),
            likeCount = parseCount(info.likeCount),
        )
    }

    private fun parseCount(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.replace(",", "").trim().toLongOrNull()
        else -> null
    }

    /**
     * Runs a full download. Progress is emitted as a percentage (0..100), ETA
     * in seconds, and the latest raw yt-dlp line. The --newline/--progress
     * pair is important: without it some yt-dlp versions only redraw a line
     * and Android never receives reliable progress callbacks.
     */
    suspend fun execute(
        context: Context,
        url: String,
        kindSelector: (YoutubeDLRequest) -> Unit,
        processId: String,
        onProgress: (Float, Long, String) -> Unit,
    ): Int {
        initialize(context)
        val request = YoutubeDLRequest(url.trim()).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--progress")
            addOption("--continue")
            addOption("--part")
            addOption("--no-overwrites")
            addOption("--retries", 4)
            addOption("--extractor-retries", 3)
            addOption("--file-access-retries", 3)
            addOption("--fragment-retries", 8)
            addOption("--retry-sleep", "exp=1:20")
            addOption("--socket-timeout", 30)
            addOption("--concurrent-fragments", 4)
            addOption("--write-thumbnail")
        }
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
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            .getOrDefault(false)
}
