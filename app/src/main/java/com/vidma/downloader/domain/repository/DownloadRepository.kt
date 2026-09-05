package com.vidma.downloader.domain.repository

import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.EngineStatus
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for everything download related.
 * Implemented in the data layer (engine + storage + preferences).
 */
interface DownloadRepository {

    val engineReady: Flow<Boolean>

    /** Full engine lifecycle (unpacking → ready / failed) for honest UI feedback. */
    val engineStatus: Flow<EngineStatus>

    /** Live task list, newest first. */
    val tasks: Flow<List<DownloadTask>>

    /** Completed items, newest first. */
    val library: Flow<List<LibraryItem>>

    /** Resolve metadata for [url] (blocking engine call → suspend). */
    suspend fun fetchMediaInfo(url: String): Result<MediaSummary>

    /**
     * Fire-and-forget: queue a task and run it. Returns its id.
     * [title]/[coverUrl]/[durationSec] pre-fill the task when the caller
     * already fetched metadata (optional).
     *
     * [directSource] marks [url] as a plain media file captured from a web
     * page: it skips the yt-dlp extractor entirely and streams straight
     * through OkHttp (works on any site, real progress).
     *
     * [formatId] / [formatHeight] / [formatFps] describe a specific stream
     * picked from the "Formats" sheet; they translate into a precise
     * yt-dlp -f selector so the engine downloads exactly the row the user
     * tapped (no guessing, no merging surprises).
     */
    fun startDownload(
        url: String,
        kind: MediaKind,
        selector: String?,
        audioFormat: String?,
        containerExt: String?,
        requestLabel: String,
        title: String? = null,
        coverUrl: String? = null,
        durationSec: Int = 0,
        directSource: Boolean = false,
        formatId: String? = null,
        formatHeight: Int = 0,
        formatFps: Int = 0,
        formatVcodec: String? = null,
        formatAcodec: String? = null,
        formatExt: String? = null,
    ): String

    fun cancelTask(id: String)

    fun retryTask(id: String)

    /** Pause a still-running task: cancellation with re-queue semantics. */
    fun pauseTask(id: String)

    /** Resume a paused task — re-issues the original engine request. */
    fun resumeTask(id: String)

    fun removeTask(id: String)

    /** Cancel every currently-running download. */
    fun pauseAllActive()

    /** Retry every task currently in Failed state. */
    fun resumeAllFailed()

    /** Remove every task in a terminal state (completed/failed/cancelled). */
    fun clearTerminal()

    /** Delete from storage + library history. */
    suspend fun deleteLibraryItem(item: LibraryItem): Boolean

    suspend fun clearLibrary()
}
