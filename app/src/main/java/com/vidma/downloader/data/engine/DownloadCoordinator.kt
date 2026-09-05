package com.vidma.downloader.data.engine

import android.content.Context
import com.vidma.downloader.data.model.HistoryRecord
import com.vidma.downloader.data.storage.MediaStorage
import com.vidma.downloader.data.store.VidmaPrefs
import com.vidma.downloader.domain.model.DownloadState
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.FormatRules
import com.vidma.downloader.domain.model.MediaKind
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs yt-dlp tasks (max [MAX_CONCURRENT] at once), tracks live state and
 * publishes finished media into the library. Owns its IO coroutine scope for
 * the lifetime of the process.
 *
 * Two guards make sure a task can never sit "not progressing" forever:
 *  * a per-task **stall watchdog** kills the engine process when no data
 *    flows for a while (resolving or downloading), and
 *  * the engine's own hard time caps (see [YtDlpEngine]).
 */
class DownloadCoordinator(
    private val appContext: Context,
    private val storage: MediaStorage,
    private val prefs: VidmaPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(MAX_CONCURRENT)

    private val tasksMap = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())

    /** Live tasks, newest first. */
    val tasks: StateFlow<List<DownloadTask>> = tasksMap
        .map { map -> map.values.sortedByDescending { it.startedAtMs } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Params needed to (re)build a task's engine request. */
    private data class StartParams(
        val url: String,
        val kind: MediaKind,
        val selector: String?,
        val audioFormat: String?,
        val containerExt: String?,
        val requestLabel: String,
        val title: String?,
        val coverUrl: String?,
        val durationSec: Int,
        val toPublic: Boolean,
        val directSource: Boolean = false,
    )

    // Touched from main (start/cancel/retry/remove) and IO (task completion),
    // so all three are concurrent.
    private val params = ConcurrentHashMap<String, StartParams>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    /** Last time a task emitted any engine line (stall detection). */
    private val activityStamps = ConcurrentHashMap<String, AtomicLong>()
    /** Set by the watchdog when it kills a stalled task (used for the error line). */
    private val stalledMessages = ConcurrentHashMap<String, String>()

    fun start(
        url: String,
        kind: MediaKind,
        selector: String?,
        audioFormat: String?,
        containerExt: String?,
        requestLabel: String,
        title: String?,
        coverUrl: String?,
        durationSec: Int,
        toPublic: Boolean,
        directSource: Boolean = false,
    ): String {
        val id = UUID.randomUUID().toString()
        val p = StartParams(
            url = url, kind = kind, selector = selector, audioFormat = audioFormat,
            containerExt = containerExt, requestLabel = requestLabel, title = title,
            coverUrl = coverUrl, durationSec = durationSec, toPublic = toPublic,
            directSource = directSource,
        )
        params[id] = p
        activityStamps[id] = AtomicLong(System.currentTimeMillis())
        tasksMap.update { current ->
            current + (id to DownloadTask(
                id = id,
                url = url,
                title = title,
                kind = kind,
                requestLabel = requestLabel,
                state = DownloadState.Queued,
                coverUrl = coverUrl,
                startedAtMs = System.currentTimeMillis(),
            ))
        }
        val job = scope.launch {
            slots.acquire()
            try {
                if (id in cancelled) {
                    update(id) { copy(state = DownloadState.Cancelled, progress = 0f) }
                    return@launch
                }
                runTask(id, p)
            } finally {
                slots.release()
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
        return id
    }

    fun cancel(id: String) {
        cancelled.add(id)
        YtDlpEngine.cancel(id)
        DirectHttpEngine.cancel(id)
        val task = tasksMap.value[id]
        if (task != null && task.state == DownloadState.Queued) {
            update(id) { copy(state = DownloadState.Cancelled, progress = 0f, statusLine = "Cancelled") }
        }
    }

    fun retry(id: String) {
        val p = params[id] ?: return
        remove(id)
        start(
            url = p.url, kind = p.kind, selector = p.selector, audioFormat = p.audioFormat,
            containerExt = p.containerExt, requestLabel = p.requestLabel, title = p.title,
            coverUrl = p.coverUrl, durationSec = p.durationSec, toPublic = p.toPublic,
            directSource = p.directSource,
        )
    }

    fun remove(id: String) {
        params.remove(id)
        activityStamps.remove(id)
        stalledMessages.remove(id)
        val job = activeJobs.remove(id)
        if (job?.isActive == true) {
            cancelled.add(id)
            YtDlpEngine.cancel(id)
            DirectHttpEngine.cancel(id)
            job.cancel()
        }
        cancelled.remove(id)
        tasksMap.update { current -> current - id }
    }

    private suspend fun runTask(id: String, p: StartParams) {
        val activity = activityStamps[id] ?: AtomicLong(System.currentTimeMillis()).also { activityStamps[id] = it }
        update(id) { copy(state = DownloadState.Resolving) }
        // dedicated per-task staging sub-dir so cleanup never harms siblings
        val stagedDir = File(storage.stagingDir(), id).apply { mkdirs() }
        // Watchdog lives inside the task's coroutine scope: it is always
        // cancelled (finally) and always sees the task's cancellation.
        val watchdog = scope.launch { watchForStall(id, activity) }
        try {
            val template = stagedDir.absolutePath + File.separator + "%(title).90B [%(id)s].%(ext)s"
            var engineFailure: String? = null
            var directOutput: File? = null
            var exitCode = if (p.directSource) {
                // The browser captured a plain media file on the page — skip
                // the extractor entirely and stream it with OkHttp. Works on
                // any site and reports real byte-level progress.
                update(id) { copy(state = DownloadState.Downloading, statusLine = "Direct media download…") }
                val out = DirectHttpEngine.download(
                    url = p.url,
                    outputDir = stagedDir,
                    title = p.title,
                    processId = id,
                    onProgress = { percent, eta, line -> onEngineLine(id, percent, eta, line) },
                )
                if (out == null) engineFailure = "The direct file could not be downloaded (blocked or expired link?)"
                directOutput = out
                if (out != null) 0 else -1
            } else {
                try {
                    YtDlpEngine.execute(
                        context = appContext,
                        url = p.url,
                        processId = id,
                        kindSelector = { request -> configureRequest(request, p, template) },
                        // Named (not trailing): onProgress is no longer the
                        // last parameter since the timeout cap was added.
                        onProgress = { percent, eta, line ->
                            onEngineLine(id, percent, eta, line)
                        },
                    )
                } catch (error: Exception) {
                    // A process/bootstrap exception should still get a chance to
                    // use the narrow OkHttp direct-media fallback below.
                    engineFailure = error.message?.take(240)
                    -1
                }
            }

            // Direct CDN/file URLs do not need a second extractor. If yt-dlp
            // cannot recognise one, the OkHttp path still gives the user a
            // reliable download with the same progress UI.
            if (exitCode != 0 && !p.directSource && id !in cancelled && DirectHttpEngine.canHandle(p.url)) {
                update(id) {
                    copy(
                        state = DownloadState.Downloading,
                        statusLine = "Trying direct media download…",
                        error = null,
                    )
                }
                val fallback = DirectHttpEngine.download(
                    url = p.url,
                    outputDir = stagedDir,
                    title = p.title,
                    processId = id,
                    onProgress = { percent, eta, line -> onEngineLine(id, percent, eta, line) },
                )
                if (fallback != null) {
                    directOutput = fallback
                    exitCode = 0
                }
            }

            if (id in cancelled) {
                cleanupTaskDir(stagedDir)
                update(id) { copy(state = DownloadState.Cancelled, progress = 0f, statusLine = "Cancelled") }
                return
            }

            if (exitCode != 0) {
                val detail = stalledMessages[id]
                    ?: tasksMap.value[id]?.statusLine?.takeIf { it.isNotBlank() }
                    ?: engineFailure
                    ?: "yt-dlp exited with code $exitCode"
                cleanupTaskDir(stagedDir)
                update(id) {
                    copy(
                        state = DownloadState.Failed,
                        error = detail,
                    )
                }
                return
            }

            // ---- engine finished successfully: find the output file ----
            update(id) { copy(state = DownloadState.Processing, progress = 1f, statusLine = "Post-processing…") }
            val output = directOutput ?: findOutputFile(stagedDir)
            if (output == null) {
                cleanupTaskDir(stagedDir)
                update(id) {
                    copy(
                        state = DownloadState.Failed,
                        error = "Finished but no output file was found",
                    )
                }
                return
            }

            // ---- publish into Downloads (or private shelf) ----
            val outputExt = output.extension.ifBlank { "mp4" }
            val mediaTitle = p.title?.takeIf { it.isNotBlank() }
                ?: output.nameWithoutExtension.replace(Regex("\\s*\\[[^\\]]*]$"), "")
            update(id) { copy(title = mediaTitle) }

            // Grab the thumbnail before publish() removes the staged media
            // file. The old order silently lost every cover on successful jobs.
            val coverPath = storage.takeCover(output, id)
            update(id) { copy(state = DownloadState.Finishing, statusLine = "Moving to Downloads…") }
            val published = storage.publish(output, p.kind, mediaTitle, p.toPublic)
            cleanupTaskDir(stagedDir)

            when (published) {
                is MediaStorage.PublishResult.Public ->
                    complete(id, p, published.uri, published.sizeBytes, coverPath, outputExt)
                is MediaStorage.PublishResult.LegacyPublic ->
                    complete(id, p, published.path, published.sizeBytes, coverPath, outputExt, inPublic = true)
                is MediaStorage.PublishResult.Private ->
                    complete(id, p, published.path, published.sizeBytes, coverPath, outputExt, inPublic = false)
                is MediaStorage.PublishResult.Failure -> {
                    update(id) {
                        copy(state = DownloadState.Failed, error = published.reason)
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            update(id) {
                copy(
                    state = if (id in cancelled) DownloadState.Cancelled else DownloadState.Failed,
                    error = msg.take(300),
                )
            }
            cleanupTaskDir(stagedDir)
        } finally {
            watchdog.cancel()
            activityStamps.remove(id)
            stalledMessages.remove(id)
        }
    }

    /**
     * Kills a task when its engine goes silent: no output line for
     * [RESOLVE_STALL_MS] while resolving, or no data for [DOWNLOAD_STALL_MS]
     * while downloading. (Queued tasks are exempt — they merely wait for a
     * free slot, no process is running.) After the kill, [runTask] records
     * the task as failed with an actionable message (retry is one tap away).
     */
    private suspend fun watchForStall(id: String, activity: AtomicLong) {
        while (true) {
            delay(8_000)
            val task = tasksMap.value[id] ?: return
            if (!task.isActive) return
            val limitMs = when (task.state) {
                DownloadState.Resolving -> RESOLVE_STALL_MS
                DownloadState.Downloading -> DOWNLOAD_STALL_MS
                else -> continue
            }
            if (System.currentTimeMillis() - activity.get() < limitMs) continue
            stalledMessages[id] = if (task.state == DownloadState.Resolving) {
                "The engine stalled while resolving this link — it may be unreachable or too slow. Tap retry."
            } else {
                "The transfer stalled (no data for ${DOWNLOAD_STALL_MS / 60_000} min). Tap retry, or try a different format."
            }
            update(id) { copy(statusLine = "Engine stalled — stopping…") }
            YtDlpEngine.cancel(id)
            DirectHttpEngine.cancel(id)
            return
        }
    }

    private fun configureRequest(request: YoutubeDLRequest, p: StartParams, template: String) {
        request.addOption("-o", template)
        val postProcess = YtDlpEngine.ffmpegReady.value
        when (p.kind) {
            MediaKind.Video -> {
                // Separate bestvideo+bestaudio streams require FFmpeg. The
                // lean build deliberately selects a single muxed stream.
                request.addOption(
                    "-f",
                    if (postProcess) p.selector ?: "bv*+ba/b"
                    else FormatRules.singleStreamVideoSelector(extractHeight(p.selector)),
                )
                if (postProcess) {
                    p.containerExt?.let { request.addOption("--merge-output-format", it) }
                }
            }
            MediaKind.Audio -> {
                if (postProcess) {
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("-x")
                    request.addOption("--audio-format", p.audioFormat ?: "mp3")
                    request.addOption("--audio-quality", "0")
                } else {
                    // Do not request -x when no converter is packaged. A
                    // source m4a/opus file is preferable to a failed task.
                    request.addOption("-f", FormatRules.sourceAudioSelector)
                }
            }
        }
    }

    private fun extractHeight(selector: String?): Int? =
        Regex("height<=([0-9]+)").find(selector.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun onEngineLine(id: String, percent: Float, eta: Long, line: String) {
        activityStamps[id]?.set(System.currentTimeMillis())
        val current = tasksMap.value[id] ?: return
        val trimmed = line.trim().take(160)
        if (percent >= 0f && percent <= 100f) {
            val phase = if (percent >= 99.5f) DownloadState.Processing else DownloadState.Downloading
            update(id) {
                copy(
                    state = phase,
                    progress = percent / 100f,
                    etaSeconds = eta,
                    statusLine = trimmed,
                )
            }
            return
        }
        val lower = line.lowercase()
        val state = when {
            lower.contains("merger") || lower.contains("extractaudio") ||
                lower.contains("videoremuxer") || lower.contains("ffmpeg") ||
                lower.contains("fixup") -> DownloadState.Processing
            else -> current.state
        }
        if (trimmed.isNotEmpty()) {
            update(id) { copy(state = state, statusLine = trimmed) }
        }
    }

    private suspend fun complete(
        id: String,
        p: StartParams,
        filePath: String,
        sizeBytes: Long,
        coverPath: String?,
        ext: String,
        inPublic: Boolean = true,
    ) {
        val task = tasksMap.value[id]
        val title = task?.title ?: p.title ?: "Downloaded media"
        val record = HistoryRecord(
            id = id,
            url = p.url,
            title = title,
            kind = p.kind.name,
            ext = ext,
            filePath = filePath,
            coverUri = coverPath,
            sizeBytes = sizeBytes,
            addedAtMs = System.currentTimeMillis(),
            durationSec = p.durationSec,
            inPublicStorage = inPublic,
        )
        prefs.upsertLibrary(record)
        update(id) {
            copy(
                state = DownloadState.Completed,
                progress = 1f,
                etaSeconds = 0,
                statusLine = "Saved to ${if (inPublic) "Downloads" else "vidma storage"}",
            )
        }
    }

    private fun update(id: String, transform: DownloadTask.() -> DownloadTask) {
        tasksMap.update { current ->
            val task = current[id] ?: return@update current
            current + (id to task.transform())
        }
    }

    private fun findOutputFile(dir: File): File? =
        dir.listFiles()
            ?.filter { it.isFile }
            ?.filterNot { it.extension.lowercase() in COVER_EXTS }
            ?.filterNot { it.name.endsWith(".part") }
            ?.maxByOrNull { it.length() }

    private fun cleanupTaskDir(dir: File) {
        runCatching { dir.deleteRecursively() }
    }

    /** Number of active tasks (used by compact queue badges). */
    val activeCount: StateFlow<Int> = tasksMap.map { list -> list.values.count { it.isActive } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private companion object {
        const val MAX_CONCURRENT = 2
        /** Resolving silence before we kill the extractor. */
        const val RESOLVE_STALL_MS = 100_000L
        /** Download silence (no bytes) before we kill the transfer. */
        const val DOWNLOAD_STALL_MS = 150_000L
        val COVER_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp")
    }
}
