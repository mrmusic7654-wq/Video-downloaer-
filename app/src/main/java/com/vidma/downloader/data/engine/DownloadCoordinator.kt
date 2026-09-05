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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.UUID

/**
 * Runs yt-dlp tasks (max [MAX_CONCURRENT] at once), tracks live state and
 * publishes finished media into the library. Owns its IO coroutine scope for
 * the lifetime of the process.
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
    )

    // Touched from main (start/cancel/retry/remove) and IO (task completion),
    // so all three are concurrent.
    private val params = java.util.concurrent.ConcurrentHashMap<String, StartParams>()
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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
    ): String {
        val id = UUID.randomUUID().toString()
        val p = StartParams(
            url = url, kind = kind, selector = selector, audioFormat = audioFormat,
            containerExt = containerExt, requestLabel = requestLabel, title = title,
            coverUrl = coverUrl, durationSec = durationSec, toPublic = toPublic,
        )
        params[id] = p
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
        )
    }

    fun remove(id: String) {
        params.remove(id)
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
        update(id) { copy(state = DownloadState.Resolving) }
        // dedicated per-task staging sub-dir so cleanup never harms siblings
        val stagedDir = File(storage.stagingDir(), id).apply { mkdirs() }
        try {
            val template = stagedDir.absolutePath + File.separator + "%(title).90B [%(id)s].%(ext)s"
            var engineFailure: String? = null
            var exitCode = try {
                YtDlpEngine.execute(
                    context = appContext,
                    url = p.url,
                    processId = id,
                    kindSelector = { request -> configureRequest(request, p, template) },
                ) { percent, eta, line ->
                    onEngineLine(id, percent, eta, line)
                }
            } catch (error: Exception) {
                // A process/bootstrap exception should still get a chance to
                // use the narrow OkHttp direct-media fallback below.
                engineFailure = error.message?.take(240)
                -1
            }

            // Direct CDN/file URLs do not need a second extractor. If yt-dlp
            // cannot recognise one, the OkHttp path still gives the user a
            // reliable download with the same progress UI.
            var directOutput: File? = null
            if (exitCode != 0 && id !in cancelled && DirectHttpEngine.canHandle(p.url)) {
                update(id) {
                    copy(
                        state = DownloadState.Downloading,
                        statusLine = "Trying direct media download…",
                        error = null,
                    )
                }
                directOutput = DirectHttpEngine.download(
                    url = p.url,
                    outputDir = stagedDir,
                    title = p.title,
                    processId = id,
                    onProgress = { percent, eta, line -> onEngineLine(id, percent, eta, line) },
                )
                if (directOutput != null) exitCode = 0
            }

            if (id in cancelled) {
                cleanupTaskDir(stagedDir)
                update(id) { copy(state = DownloadState.Cancelled, progress = 0f, statusLine = "Cancelled") }
                return
            }

            if (exitCode != 0) {
                val detail = tasksMap.value[id]?.statusLine?.takeIf { it.isNotBlank() }
                cleanupTaskDir(stagedDir)
                update(id) {
                    copy(
                        state = DownloadState.Failed,
                        error = detail ?: engineFailure ?: "yt-dlp exited with code $exitCode",
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
        val COVER_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp")
    }
}
