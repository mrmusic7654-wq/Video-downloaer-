package com.vidma.downloader.data.repository

import android.content.Context
import com.vidma.downloader.data.engine.DownloadCoordinator
import com.vidma.downloader.data.engine.YtDlpEngine
import com.vidma.downloader.data.model.HistoryRecord
import com.vidma.downloader.data.storage.MediaStorage
import com.vidma.downloader.data.store.VidmaPrefs
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Glues prefs + storage + engine coordinator behind the domain interface. */
class DownloadRepositoryImpl(
    private val appContext: Context,
    private val prefs: VidmaPrefs,
    private val storage: MediaStorage,
    private val coordinator: DownloadCoordinator,
) : DownloadRepository {

    override val engineReady: Flow<Boolean> = YtDlpEngine.ready

    override val tasks: Flow<List<DownloadTask>> = coordinator.tasks

    override val library: Flow<List<LibraryItem>> =
        prefs.libraryFlow.map { records -> records.map { it.toDomain() } }

    override suspend fun fetchMediaInfo(url: String): Result<MediaSummary> = try {
        // Guard against a metadata resolve that never returns (a stuck/old
        // extractor, a hung network call, a first-run unpack fault). yt-dlp
        // itself has --socket-timeout, but that only bounds a single network
        // op; a wall-clock timeout here ensures the UI never shows an endless
        // "resolving" state and instead surfaces something actionable.
        val summary = withContext(Dispatchers.IO) {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                YtDlpEngine.fetchInfo(appContext, url)
            }
        } ?: throw java.util.concurrent.TimeoutException(
            "The engine took too long to read that link. Check the URL or try again.",
        )
        Result.success(summary)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private companion object {
        // 90s is generous: first-run Python unpack + a slow extractor can be
        // slow, but an indefinite hang is never useful.
        const val RESOLVE_TIMEOUT_MS = 90_000L
    }

    override fun startDownload(
        url: String,
        kind: MediaKind,
        selector: String?,
        audioFormat: String?,
        containerExt: String?,
        requestLabel: String,
        title: String?,
        coverUrl: String?,
        durationSec: Int,
        videoOnly: Boolean,
    ): String = coordinator.start(
        url = url,
        kind = kind,
        selector = selector,
        audioFormat = audioFormat,
        containerExt = containerExt,
        requestLabel = requestLabel,
        title = title,
        coverUrl = coverUrl,
        durationSec = durationSec,
        toPublic = prefs.publicStorageNow,
        videoOnly = videoOnly,
    )

    override fun cancelTask(id: String) = coordinator.cancel(id)

    override fun retryTask(id: String) = coordinator.retry(id)

    override fun removeTask(id: String) = coordinator.remove(id)

    override suspend fun deleteLibraryItem(item: LibraryItem): Boolean {
        val cover = item.coverUri?.takeUnless { it.startsWith("http") }
        val deleted = storage.deleteMedia(item.filePath, cover)
        prefs.removeFromLibrary(item.id)
        return deleted
    }

    override suspend fun clearLibrary() {
        prefs.libraryFlow.first().forEach { record ->
            val cover = record.coverUri?.takeUnless { it.startsWith("http") }
            storage.deleteMedia(record.filePath, cover)
        }
        prefs.clearLibrary()
    }
}

private fun HistoryRecord.toDomain(): LibraryItem = LibraryItem(
    id = id,
    url = url,
    title = title,
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.Video),
    ext = ext,
    filePath = filePath,
    coverUri = coverUri,
    sizeBytes = sizeBytes,
    addedAtMs = addedAtMs,
    durationSec = durationSec,
    videoWidth = videoWidth,
    videoHeight = videoHeight,
    inPublicStorage = inPublicStorage,
)
