package com.vidma.downloader.data.engine

import android.content.Context
import com.vidma.downloader.domain.model.EngineStatus
import com.vidma.downloader.domain.model.MediaFormat
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
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
        // [YoutubeDL.execute] blocks its thread, so it runs in [engineScope]
        // (never on the caller's dispatcher) with a hard timeout: a hung
        // extractor is destroyed instead of freezing the UI on "parsing…".
        // The outcome is captured as a Result inside the child — after a
        // timeout the orphaned call can finish or fail without ever throwing
        // unobserved (an un-awaited failed child would crash the process).
        val deferred = engineScope.async(Dispatchers.IO) {
            try {
                Result.success(YoutubeDL.getInstance().execute(request, processId, null))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // Safety net: never leak a python process on any exit path.
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
        }
        val response = try {
            withTimeout(INFO_TIMEOUT_MS) { deferred.await() }.getOrThrow()
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

        val formats = buildFormats(info)
        val heights = formats
            .filter { it.height > 0 }
            .map { it.height }
            .distinct()
            .sortedDescending()
        val best = formats.firstOrNull { it.height > 0 && it.vcodec != "none" }
            ?: formats.firstOrNull { it.height > 0 }
            ?: formats.firstOrNull()

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
            availableFormats = formats,
            bestMergedFormat = best,
            // youtubedl-android has exposed these fields as both strings and
            // numbers across releases. Parsing Any keeps upgrades compatible.
            viewCount = parseCount(info.viewCount),
            likeCount = parseCount(info.likeCount),
        )
    }

    /**
     * Translate the engine's raw format list into the UI's [MediaFormat]
     * model. Filters out no-data storyboards, normalises codecs, and
     * estimates size from bitrate when the file size itself is unknown.
     */
    private fun buildFormats(info: VideoInfo): List<MediaFormat> {
        val raw: List<VideoFormat>? = info.formats
        if (raw == null) return emptyList()
        val result = mutableListOf<MediaFormat>()
        val seen = HashSet<String>()
        for (f in raw) {
            val formatId = f.formatId?.takeIf { it.isNotBlank() } ?: continue
            val ext = f.ext?.takeIf { it.isNotBlank() } ?: continue
            // Skip storyboards, "none" rows and obviously useless payloads.
            val vcodec = f.vcodec?.takeIf { it.isNotBlank() && it != "none" }
            val acodec = f.acodec?.takeIf { it.isNotBlank() && it != "none" }
            if (vcodec == null && acodec == null) continue
            val kind = if (vcodec == null) MediaKind.Audio else MediaKind.Video
            val height = f.height
            val fps = f.fps
            // Skip progressive duplicates we already represented as a merged
            // (video+audio) row.
            val dedupeKey = "$height|$fps|${vcodec ?: "-"}|${acodec ?: "-"}|$ext"
            if (!seen.add(dedupeKey)) continue
            val isProgressive = vcodec != null && acodec != null
            val mediaType = when {
                kind == MediaKind.Audio -> "audio"
                isProgressive -> "video+audio"
                else -> "video"
            }
            val label = buildString {
                if (kind == MediaKind.Audio) {
                    append("Audio")
                } else {
                    if (height > 0) {
                        append("${height}p")
                        if (fps > 0) append(fps)
                    } else {
                        append("Video")
                    }
                }
                append(" · ").append(ext)
            }
            val sizeBytes = parseFileSize(f.fileSize, f.tbr, info.duration)
            result += MediaFormat(
                id = formatId,
                label = label,
                kind = kind,
                height = height,
                fps = fps,
                vcodec = vcodec,
                acodec = acodec,
                ext = ext,
                sizeBytes = sizeBytes,
                mediaType = mediaType,
            )
        }
        // Sort: video with height first (desc), audio at the end, ties by size.
        return result.sortedWith(
            compareByDescending<MediaFormat> { it.height }
                .thenByDescending { it.fps }
                .thenBy { it.mediaType == "audio" }
                .thenBy { it.ext },
        )
    }

    private fun parseFileSize(
        rawSize: Long,
        rawBitrate: Int,
        durationSec: Int,
    ): Long {
        if (rawSize > 0L) return rawSize
        // Estimate: bitrate (kbps) * duration (s) / 8 = bytes.
        val kbps = rawBitrate.toDouble()
        if (kbps <= 0.0 || durationSec <= 0) return 0L
        return (kbps * 1000.0 / 8.0 * durationSec).toLong()
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

    private fun parseCount(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return value.replace(",", "").trim().toLongOrNull()
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
            addOption("--buffer-size", "64K")
            addOption("--http-chunk-size", "1M")
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
        // Same supervision as [fetchInfo]: the blocking library call runs in
        // [engineScope] with a hard total-time cap, and its outcome is a
        // Result so a post-timeout orphan can never throw unobserved.
        val deferred = engineScope.async(Dispatchers.IO) {
            try {
                Result.success(
                    YoutubeDL.getInstance().execute(
                        request,
                        processId,
                    ) { progress, eta, line ->
                        onProgress(progress, eta, line)
                    },
                )
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }.getOrThrow().exitCode
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

    // Supervision constants live directly on this object — an `object`
    // declaration cannot have a companion.
    /** Max time for a metadata resolve before we destroy the process. */
    private const val INFO_TIMEOUT_MS = 120_000L

    /** Hard total-time cap for a download; the stall watchdog is the main guard. */
    private const val DOWNLOAD_TIMEOUT_MS = 30L * 60_000L

    private val ERROR_JSON_PATTERN = Regex("\"type\"\\s*:\\s*\"error\"")
    private val ERROR_MESSAGE_PATTERN = Regex("\"error\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
}
