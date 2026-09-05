package com.vidma.downloader.features.downloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidma.downloader.VidmaApp
import com.vidma.downloader.data.engine.DirectHttpEngine
import com.vidma.downloader.data.engine.YtDlpEngine
import com.vidma.downloader.domain.model.AudioFormatPref
import com.vidma.downloader.domain.model.ContainerPref
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.FormatRules
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.model.QualityPreset
import com.vidma.downloader.domain.repository.DownloadRepository
import com.vidma.downloader.ui.theme.AccentPreset
import com.vidma.downloader.util.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Metadata resolution phase of the Downloader hero card. */
sealed interface FetchPhase {
    data object Idle : FetchPhase
    data object Fetching : FetchPhase
    data class Ready(val summary: MediaSummary) : FetchPhase
    data class Error(val message: String) : FetchPhase
}

/**
 * App-wide downloader state: URL form, metadata fetch, live task queue,
 * library history and the settings that theme/root observe.
 */
class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val appContainer = (application as VidmaApp).container
    private val repo: DownloadRepository = appContainer.repository

    // ---------------- observed by the whole app ----------------

    val downloads: StateFlow<List<DownloadTask>> = repo.tasks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<List<LibraryItem>> = repo.library
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val engineReady: StateFlow<Boolean> = repo.engineReady
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Optional post-processing capability; the lean APK can still download. */
    val ffmpegReady: StateFlow<Boolean> = YtDlpEngine.ffmpegReady
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Last engine-init failure, if any (surfaced in the engine-status card). */
    val engineError: StateFlow<String?> = YtDlpEngine.lastInitError
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val accent: StateFlow<AccentPreset> = appContainer.prefs.accentFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentPreset.Aurora)

    val publicStorage: StateFlow<Boolean> = appContainer.prefs.publicStorageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ---------------- home form state ----------------

    private val _urlText = MutableStateFlow("")
    val urlText: StateFlow<String> = _urlText.asStateFlow()

    private val _kind = MutableStateFlow(MediaKind.Video)
    val kind: StateFlow<MediaKind> = _kind.asStateFlow()

    private val _quality = MutableStateFlow(QualityPreset.Auto)
    val quality: StateFlow<QualityPreset> = _quality.asStateFlow()

    private val _audioFormat = MutableStateFlow(AudioFormatPref.Mp3)
    val audioFormat: StateFlow<AudioFormatPref> = _audioFormat.asStateFlow()

    private val _container = MutableStateFlow(ContainerPref.Mp4)
    val container: StateFlow<ContainerPref> = _container.asStateFlow()

    /** When true (and kind == Video), grab the video stream only, no audio. */
    private val _videoOnly = MutableStateFlow(false)
    val videoOnly: StateFlow<Boolean> = _videoOnly.asStateFlow()

    private val _fetchPhase = MutableStateFlow<FetchPhase>(FetchPhase.Idle)
    val fetchPhase: StateFlow<FetchPhase> = _fetchPhase.asStateFlow()

    /** Kept so the browser can jump straight to a prefilled home tab. */
    private val _lastSharedUrl = MutableStateFlow<String?>(null)
    val lastSharedUrl: StateFlow<String?> = _lastSharedUrl.asStateFlow()

    private val _transient = MutableStateFlow<String?>(null)
    val transient: StateFlow<String?> = _transient.asStateFlow()

    /** Retains metadata of the *last resolved* URL for the start button. */
    private var readySummary: MediaSummary? = null
    private var fetchJob: Job? = null

    // ---------------- url form ----------------

    fun onUrlChange(text: String) {
        _urlText.value = text
        val normalised = normalizeUrl(text)
        if (readySummary?.url != normalised) {
            fetchJob?.cancel()
            readySummary = null
            _fetchPhase.value = FetchPhase.Idle
        }
    }

    fun onKindChange(kind: MediaKind) {
        _kind.value = kind
    }

    fun onQualityChange(preset: QualityPreset) {
        _quality.value = preset
    }

    fun onAudioChange(pref: AudioFormatPref) {
        _audioFormat.value = pref
    }

    fun onContainerChange(pref: ContainerPref) {
        _container.value = pref
    }

    fun onVideoOnlyChange(enabled: Boolean) {
        _videoOnly.value = enabled
    }

    fun clearUrl() {
        _urlText.value = ""
        _fetchPhase.value = FetchPhase.Idle
        readySummary = null
    }

    /** Handle a URL shared into vidma (SEND / VIEW intents). */
    fun offerSharedUrl(raw: String?) {
        val url = raw?.let { normalizeUrl(it) } ?: return
        if (_urlText.value.isNotBlank() && _urlText.value != url) {
            _lastSharedUrl.value = url
        } else {
            _lastSharedUrl.value = null
            _urlText.value = url
            readySummary = null
            _fetchPhase.value = FetchPhase.Idle
        }
    }

    /** Used when the shared-URL chip is tapped (switches home + fills). */
    fun adoptSharedUrl() {
        _lastSharedUrl.value?.let {
            _lastSharedUrl.value = null
            _urlText.value = it
            fetch()
        }
    }

    // ---------------- metadata fetch ----------------

    fun fetch() {
        val raw = _urlText.value
        val url = normalizeUrl(raw)
        if (url == null) {
            _fetchPhase.value = FetchPhase.Error("That doesn't look like a link — paste a video or page URL.")
            return
        }
        // Do not reject a paste while the first-run runtime is unpacking.
        // fetchMediaInfo initializes the engine on IO and the loading card
        // gives immediate feedback instead of forcing a second tap.
        _urlText.value = url
        resolveMedia(url)
    }

    /** Resolve metadata for [url] and surface the result in [fetchPhase]. */
    private fun resolveMedia(url: String) {
        _fetchPhase.value = FetchPhase.Fetching
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val result = repo.fetchMediaInfo(url)
            _fetchPhase.value = result.fold(
                onSuccess = {
                    readySummary = it
                    FetchPhase.Ready(it)
                },
                onFailure = {
                    readySummary = null
                    FetchPhase.Error(userMessageFor(it))
                },
            )
        }
    }

    /**
     * Resolve for the browser flow, but fall back to a direct download for URLs
     * yt-dlp can't "resolve" (a raw CDN .mp4/.webm file). Such files are still
     * perfectly downloadable via the narrow OkHttp path, so the browser never
     * quietly dead-ends on them.
     */
    private fun resolveForBrowser(url: String, title: String?) {
        _fetchPhase.value = FetchPhase.Fetching
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val result = repo.fetchMediaInfo(url)
            _fetchPhase.value = result.fold(
                onSuccess = {
                    readySummary = it
                    FetchPhase.Ready(it)
                },
                onFailure = { error ->
                    if (DirectHttpEngine.canHandle(url)) {
                        readySummary = null
                        startUrlDirect(url, MediaKind.Video, "Direct media", title, null)
                        FetchPhase.Idle
                    } else {
                        readySummary = null
                        FetchPhase.Error(userMessageFor(error))
                    }
                },
            )
        }
    }

    // ---------------- download actions ----------------

    fun startDownload() {
        val summary = readySummary ?: run {
            _transient.value = "Resolve the link first — press the search arrow."
            return
        }
        val k = _kind.value
        val videoOnly = k == MediaKind.Video && _videoOnly.value
        val label = when {
            k == MediaKind.Audio && !ffmpegReady.value -> "Source audio"
            k == MediaKind.Audio -> FormatRules.requestLabel(k, _quality.value, _container.value, _audioFormat.value)
            videoOnly -> {
                val base = if (_quality.value == QualityPreset.Auto) "Best" else "${_quality.value.height}p"
                "$base · video only"
            }
            else -> FormatRules.requestLabel(k, _quality.value, _container.value, _audioFormat.value)
        }
        repo.startDownload(
            url = summary.url,
            kind = k,
            selector = if (k == MediaKind.Video) {
                if (videoOnly) FormatRules.videoOnlySelector(_quality.value.height)
                else FormatRules.videoSelector(_quality.value.height)
            } else null,
            audioFormat = if (k == MediaKind.Audio) _audioFormat.value.ytArg else null,
            containerExt = _container.value.ext.takeIf { k == MediaKind.Video && !videoOnly },
            requestLabel = label,
            title = summary.title,
            coverUrl = summary.thumbnailUrl,
            durationSec = summary.durationSec,
            videoOnly = videoOnly,
        )
        _transient.value = if (videoOnly) {
            "Downloading video only — “${summary.title.take(48)}”…"
        } else if (k == MediaKind.Video) {
            "Downloading “${summary.title.take(48)}”…"
        } else {
            "Extracting ${_audioFormat.value.label} audio…"
        }
    }

    /**
     * Route a page (from the browser's "download this page" action) into the
     * same format studio as a pasted link. Instead of silently queueing a
     * generic mp4 — which offered no Video/Audio, quality or container choice
     * and frequently never showed progress — this resolves the page's metadata
     * and lets the user pick exactly what to save. The actual download then
     * starts only when they tap Download.
     */
    fun prepareDownload(rawUrl: String, title: String?) {
        val url = normalizeUrl(rawUrl) ?: run {
            _transient.value = "That doesn't look like a downloadable page."
            return
        }
        _lastSharedUrl.value = null
        _urlText.value = url
        readySummary = null
        resolveForBrowser(url, title)
    }

    /** Start the browser-page download directly (kept for quick one-tap flows). */
    fun startUrlDirect(url: String, kind: MediaKind, requestLabel: String, title: String?, cover: String?) {
        repo.startDownload(
            url = url,
            kind = kind,
            selector = if (kind == MediaKind.Video) FormatRules.videoSelector(null) else null,
            audioFormat = if (kind == MediaKind.Audio) AudioFormatPref.Mp3.ytArg else null,
            containerExt = null,
            requestLabel = requestLabel,
            title = title,
            coverUrl = cover,
            durationSec = 0,
        )
        _transient.value = "Queued $requestLabel download…"
    }

    fun cancelTask(id: String) {
        repo.cancelTask(id)
        _transient.value = "Download cancelled"
    }

    fun retryTask(id: String) {
        repo.retryTask(id)
        _transient.value = "Retrying…"
    }

    fun removeTask(id: String) = repo.removeTask(id)

    // ---------------- library ----------------

    fun deleteLibraryItem(item: LibraryItem) {
        viewModelScope.launch {
            repo.deleteLibraryItem(item)
            _transient.value = "Removed from library"
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            repo.clearLibrary()
            _transient.value = "Library cleared"
        }
    }

    // ---------------- settings ----------------

    fun setAccent(preset: AccentPreset) {
        viewModelScope.launch(Dispatchers.IO) { appContainer.prefs.setAccent(preset) }
    }

    fun setPublicStorage(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { appContainer.prefs.setPublicStorage(enabled) }
    }

    fun retryEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { YtDlpEngine.initialize(getApplication()) }
                .onFailure {
                    // Prefer the engine's own (cause-chain) description over the
                    // top-level message, which is often just "failed to initialize".
                    val detail = YtDlpEngine.lastInitError.value
                        ?: it.message
                        ?: it.javaClass.simpleName
                    _transient.value = "Engine failed: ${detail?.take(140)}"
                }
        }
    }

    fun showMessage(msg: String) {
        _transient.value = msg
    }

    fun consumeTransient() {
        _transient.value = null
    }

    private fun userMessageFor(t: Throwable): String {
        val m = t.message ?: return "Could not read that link."
        val clean = m.lineSequence().firstOrNull().orEmpty().trim().take(160)
        return when {
            clean.isBlank() -> "Could not read that link."
            else -> clean
        }
    }
}
