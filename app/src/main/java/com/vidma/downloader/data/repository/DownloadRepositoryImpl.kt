package com.vidma.downloader.data.repository

import android.content.Context
import com.vidma.downloader.data.engine.DownloadCoordinator
import com.vidma.downloader.data.engine.YtDlpEngine
import com.vidma.downloader.data.model.HistoryRecord
import com.vidma.downloader.data.storage.MediaStorage
import com.vidma.downloader.data.store.VidmaPrefs
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.EngineStatus
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException

/** Glues prefs + storage + engine coordinator behind the domain interface. */
class DownloadRepositoryImpl(
    private val appContext: Context,
    private val prefs: VidmaPrefs,
    private val storage: MediaStorage,
    private val coordinator: DownloadCoordinator,
) : DownloadRepository {

    override val engineReady: Flow<Boolean> =
        YtDlpEngine.status.map { it is EngineStatus.Ready }

    override val engineStatus: Flow<EngineStatus> = YtDlpEngine.status

    override val tasks: Flow<List<DownloadTask>> = coordinator.tasks

    override val library: Flow<List<LibraryItem>> =
        prefs.libraryFlow.map { records -> records.map { it.toDomain() } }

    override suspend fun fetchMediaInfo(url: String): Result<MediaSummary> = try {
        Result.success(
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                YtDlpEngine.fetchInfo(appContext, url)
            },
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
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
        directSource: Boolean,
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
        directSource = directSource,
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
