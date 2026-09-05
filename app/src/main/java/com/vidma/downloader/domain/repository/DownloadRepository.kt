package com.vidma.downloader.domain.repository

import com.vidma.downloader.domain.model.DownloadTask
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
        videoOnly: Boolean = false,
    ): String

    fun cancelTask(id: String)

    fun retryTask(id: String)

    fun removeTask(id: String)

    /** Delete from storage + library history. */
    suspend fun deleteLibraryItem(item: LibraryItem): Boolean

    suspend fun clearLibrary()
}
