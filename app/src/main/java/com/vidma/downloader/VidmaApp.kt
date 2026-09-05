package com.vidma.downloader

import android.app.Application
import android.content.Context
import com.vidma.downloader.data.engine.DownloadCoordinator
import com.vidma.downloader.data.engine.YtDlpEngine
import com.vidma.downloader.data.repository.DownloadRepositoryImpl
import com.vidma.downloader.data.storage.MediaStorage
import com.vidma.downloader.data.store.VidmaPrefs
import com.vidma.downloader.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual, zero-dependency DI container (multi-layer MVVM without the
 * annotation-processing overhead). All singletons the UI talks to live here.
 */
class AppContainer(private val appContext: Context) {

    val prefs: VidmaPrefs = VidmaPrefs(appContext)
    val storage: MediaStorage = MediaStorage(appContext)
    val coordinator: DownloadCoordinator = DownloadCoordinator(appContext, storage, prefs)
    val repository: DownloadRepository = DownloadRepositoryImpl(appContext, prefs, storage, coordinator)

    private val initErrorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        android.util.Log.e("VidmaApp", "background init failed", error)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + initErrorHandler)

    init {
        appScope.launch {
            // Wipe stale partial files from previous sessions. Never fatal:
            // a leftover .part file must not be able to kill the process.
            runCatching { storage.cleanStaging() }
                .onFailure { android.util.Log.w("VidmaApp", "staging cleanup failed", it) }
            // Warm the yt-dlp runtime before the first tap. FFmpeg is
            // optional in the lean build and is detected by the engine.
            runCatching { YtDlpEngine.initialize(appContext) }
                .onFailure { android.util.Log.e("VidmaEngine", "engine init failed", it) }
        }
    }
}

class VidmaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
