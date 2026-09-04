package com.vidma.downloader.features.downloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidma.downloader.VidmaApp
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

    private val _fetchPhase = MutableStateFlow<FetchPhase>(FetchPhase.Idle)
    val fetchPhase: StateFlow<FetchPhase> = _fetchPhase.asStateFlow()

    /** Kept so the browser can jump straight to a prefilled home tab. */
    private val _lastSharedUrl = MutableStateFlow<String?>(null)
    val lastSharedUrl: StateFlow<String?> = _lastSharedUrl.asStateFlow()

    private val _transient = MutableStateFlow<String?>(null)
    val transient: StateFlow<String?> = _transient.asStateFlow()

    /** Retains metadata of the *last resolved* URL for the start button. */
    private var readySummary: MediaSummary? = null

    // ---------------- url form ----------------

    fun onUrlChange(text: String) {
        _urlText.value = text
        if (_fetchPhase.value is FetchPhase.Error) _fetchPhase.value = FetchPhase.Idle
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
        if (!engineReady.value) {
            _fetchPhase.value = FetchPhase.Error("The download engine is still starting… try again in a moment.")
            return
        }
        _urlText.value = url
        _fetchPhase.value = FetchPhase.Fetching
        viewModelScope.launch {
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

    // ---------------- download actions ----------------

    fun startDownload() {
        val summary = readySummary ?: run {
            _transient.value = "Resolve the link first — press the search arrow."
            return
        }
        val k = _kind.value
        val label = FormatRules.requestLabel(k, _quality.value, _container.value, _audioFormat.value)
        repo.startDownload(
            url = summary.url,
            kind = k,
            selector = if (k == MediaKind.Video) {
                FormatRules.videoSelector(_quality.value.height)
            } else null,
            audioFormat = if (k == MediaKind.Audio) _audioFormat.value.ytArg else null,
            containerExt = _container.value.ext.takeIf { k == MediaKind.Video },
            requestLabel = label,
            title = summary.title,
            coverUrl = summary.thumbnailUrl,
            durationSec = summary.durationSec,
        )
        _transient.value = if (k == MediaKind.Video) {
            "Downloading “${summary.title.take(48)}”…"
        } else {
            "Extracting ${_audioFormat.value.label} audio…"
        }
    }

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
                .onFailure { _transient.value = "Engine failed: ${it.message?.take(80)}" }
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
