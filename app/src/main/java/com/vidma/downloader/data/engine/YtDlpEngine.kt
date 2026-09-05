package com.vidma.downloader.data.engine

import android.content.Context
import com.vidma.downloader.domain.model.EngineStatus
import com.vidma.downloader.domain.model.MediaSummary
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Thin, suspend-friendly wrapper around youtubedl-android.
 *
 * The library contains the yt-dlp/Python runtime. FFmpeg is intentionally an
 * optional build capability: the lean arm64 release does not ship another
 * native binary, so it can stay close to the 20–30 MB download target. When a
 * full build is made with -Pvidma.withFfmpeg=true, post-processing and audio
 * conversion are enabled automatically through the same API.
 *
 * Hardening over the raw library:
 *  * [status] exposes the real runtime lifecycle so the UI can show "the
 *    engine is still unpacking" instead of pretending a scan is running.
 *  * [fetchInfo] runs with a process id + timeout, so a hung extractor can no
 *    longer leave the resolver stuck at "parsing…" forever — the Python
 *    process is destroyed and the user gets an actionable error.
 *  * [execute] has a hard total-time cap on top of the coordinator's
 *    no-data stall watchdog.
 */
object YtDlpEngine {

    /** The engine understood the request but could not find media to read. */
    class EngineResolveException(message: String) : Exception(message)

    /** The engine process stopped responding and was destroyed. */
    class EngineStalledException(message: String) : Exception(message)

    private val initMutex = Mutex()
    @Volatile private var initialized = false

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Initializing)
    /** Live lifecycle of the bundled runtime: Initializing → Ready | Failed. */
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _ffmpegReady = MutableStateFlow(false)
    /** True only when the optional FFmpeg artifact was packaged and initialized. */
    val ffmpegReady: StateFlow<Boolean> = _ffmpegReady.asStateFlow()

    private val _lastInitError = MutableStateFlow<String?>(null)
    val lastInitError: StateFlow<String?> = _lastInitError.asStateFlow()

    /**
     * The library's [YoutubeDL.execute] is a blocking process call. These
     * jobs live in their own scope so callers can time them out with
     * [withTimeout] without blocking or leaking the caller's dispatcher.
     */
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Idempotent, thread-safe initialization. It is called from Application
     * startup and again by every engine operation, so a user can paste a link
     * immediately while the first-run unpack is still in progress.
     */
    suspend fun initialize(context: Context) {
        initMutex.withLock {
            if (initialized) {
                if (_status.value !is EngineStatus.Ready) _status.value = EngineStatus.Ready
                return
            }
            _status.value = EngineStatus.Initializing
            val app = context.applicationContext
            try {
                YoutubeDL.getInstance().init(app)

                // FFmpeg is an optional dependency in the lean build. Reflection
                // keeps that build linkable while still enabling the full build.
                _ffmpegReady.value = initializeOptionalFfmpeg(app)
                initialized = true
                _lastInitError.value = null
                _status.value = EngineStatus.Ready
            } catch (error: Throwable) {
                initialized = false
                val message = (error.message ?: error.javaClass.simpleName).take(240)
                _lastInitError.value = message
                _status.value = EngineStatus.Failed(message.ifBlank { "Engine failed to initialize" })
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

    /**
     * Resolve metadata for a single URL. Runs the real yt-dlp --dump-json
     * pipeline on the caller's IO context — but with a process id and a hard
     * timeout: a hung extractor is destroyed instead of freezing the UI on
     * "parsing the video" forever.
     */
    suspend fun fetchInfo(context: Context, url: String): MediaSummary {
        initialize(context)
        val cleanUrl = url.trim()
        val processId = "vidma-info-${UUID.randomUUID()}"
        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--dump-json")
            addOption("--ignore-errors")
            addOption("--no-playlist")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--extractor-retries", 2)
            addOption("--socket-timeout", 20)
        }
        val job = engineScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().execute(request, processId, null)
            } finally {
                // Safety net: never leak a python process on any exit path.
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
        }
        val response = try {
            withTimeout(INFO_TIMEOUT_MS) { job.await() }
        } catch (e: TimeoutCancellationException) {
            YoutubeDL.getInstance().destroyProcessById(processId)
            throw EngineStalledException(
                "The engine stopped responding while reading this link — the site may be very slow or unreachable. Tap retry.",
            )
        }

        val json = extractJson(response.out)
        if (ERROR_JSON_PATTERN.containsMatchIn(json)) {
            // yt-dlp with --dump-json --ignore-errors reports extractor
            // failures as {"type":"error","error":"..."} instead of throwing.
            val message = ERROR_MESSAGE_PATTERN
                .find(json)
                ?.groupValues?.get(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\n", " ")
                ?.replace("\\u0026", "&")
                ?.trim()
            throw EngineResolveException(
                message?.takeIf { it.isNotBlank() }
                    ?: "The engine could not find downloadable media at that link.",
            )
        }
        val info: VideoInfo = try {
            YoutubeDL.objectMapper.readValue(json, VideoInfo::class.java)
        } catch (e: Exception) {
            throw EngineResolveException(
                "The engine returned an unexpected response — the link may not point to media.",
            )
        }

        val heights = info.formats
            ?.mapNotNull { format -> format.height.takeIf { it > 0 } }
            ?.distinct()
            ?.sortedDescending()
            ?: emptyList()

        return MediaSummary(
            url = cleanUrl,
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

    /**
     * The first balanced JSON object in the engine's stdout. Trailing log
     * lines (if any) would otherwise break the strict Jackson parse.
     */
    private fun extractJson(out: String): String {
        val start = out.indexOf('{')
        if (start < 0) {
            throw EngineResolveException(
                "The engine returned no data for this link. It may be private, region-locked or not media.",
            )
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until out.length) {
            val c = out[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) return out.substring(start, i + 1)
        }
        return out.substring(start)
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
     *
     * The blocking library call runs in [engineScope] with a hard total-time
     * cap; the coordinator additionally kills the process when *no data*
     * flows for a while (stall watchdog).
     */
    suspend fun execute(
        context: Context,
        url: String,
        kindSelector: (YoutubeDLRequest) -> Unit,
        processId: String,
        onProgress: (Float, Long, String) -> Unit,
        timeoutMs: Long = DOWNLOAD_TIMEOUT_MS,
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
        val job = engineScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().execute(
                    request,
                    processId,
                ) { progress, eta, line ->
                    onProgress(progress, eta, line)
                }
            } finally {
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
        }
        return try {
            withTimeout(timeoutMs) { job.await() }.exitCode
        } catch (e: TimeoutCancellationException) {
            YoutubeDL.getInstance().destroyProcessById(processId)
            throw EngineStalledException(
                "The download timed out — the source stopped responding.",
            )
        }
    }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            .getOrDefault(false)

    private companion object {
        /** Max time for a metadata resolve before we destroy the process. */
        const val INFO_TIMEOUT_MS = 120_000L

        /** Hard total-time cap for a download; the stall watchdog is the main guard. */
        const val DOWNLOAD_TIMEOUT_MS = 30L * 60_000L

        val ERROR_JSON_PATTERN = Regex("\"type\"\\s*:\\s*\"error\"")
        val ERROR_MESSAGE_PATTERN = Regex("\"error\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    }
}
