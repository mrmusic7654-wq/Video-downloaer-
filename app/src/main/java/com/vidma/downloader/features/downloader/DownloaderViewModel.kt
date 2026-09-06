package com.vidma.downloader.features.downloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidma.downloader.VidmaApp
import com.vidma.downloader.data.engine.YtDlpEngine
import com.vidma.downloader.domain.model.AudioFormatPref
import com.vidma.downloader.domain.model.CaptureRequest
import com.vidma.downloader.domain.model.ContainerPref
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.EngineStatus
import com.vidma.downloader.domain.model.FormatRules
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaFormat
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.model.QualityPreset
import com.vidma.downloader.domain.repository.DownloadRepository
import com.vidma.downloader.ui.theme.AccentPreset
import com.vidma.downloader.util.hostOf
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

    /** Full engine lifecycle — the resolver UI reports "unpacking" honestly. */
    val engineStatus: StateFlow<EngineStatus> = repo.engineStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, EngineStatus.Initializing)

    /** Optional post-processing capability; the lean APK can still download. */
    val ffmpegReady: StateFlow<Boolean> = YtDlpEngine.ffmpegReady
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val accent: StateFlow<AccentPreset> = appContainer.prefs.accentFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentPreset.Platinum)

    val publicStorage: StateFlow<Boolean> = appContainer.prefs.publicStorageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ---------------- home form state ----------------

    private val _urlText = MutableStateFlow("")
    val urlText: StateFlow<String> = _urlText.asStateFlow()

    /**
     * True when the user is using the *second* URL field (the link bar).
     * The first field is a search bar that opens the in-app browser.
     */
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

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

    /**
     * Set when the user wants to open the in-app browser with a query /
     * URL. The home screen reacts to this and navigates accordingly.
     */
    private val _openBrowserWith = MutableStateFlow<String?>(null)
    val openBrowserWith: StateFlow<String?> = _openBrowserWith.asStateFlow()

    fun consumeOpenBrowser() {
        _openBrowserWith.value = null
    }

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

    fun onSearchChange(text: String) {
        _searchText.value = text
    }

    fun onSearchSubmit() {
        val raw = _searchText.value.trim()
        if (raw.isEmpty()) return
        val url = normalizeUrl(raw) ?: "https://www.google.com/search?q=" +
            java.net.URLEncoder.encode(raw, "UTF-8")
        _openBrowserWith.value = url
        _searchText.value = ""
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

    fun clearSearch() {
        _searchText.value = ""
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

    // ---------------- download actions ----------------

    /**
     * Quick-download the engine's "best" stream — used by the [Download]
     * icon in the studio header when the user wants the easy path. (The
     * Formats sheet also calls this for each picked row, with [format]
     * pointing at the user's selection.)
     */
    fun startDownload(format: MediaFormat? = null) {
        val summary = readySummary ?: run {
            _transient.value = "Resolve the link first — paste a link and press Resolve."
            return
        }
        val k = format?.kind ?: _kind.value
        val label = format?.label
            ?: if (k == MediaKind.Audio && !ffmpegReady.value) "Source audio"
            else FormatRules.requestLabel(k, _quality.value, _container.value, _audioFormat.value)
        repo.startDownload(
            url = summary.url,
            kind = k,
            selector = if (k == MediaKind.Video && format == null) {
                FormatRules.videoSelector(_quality.value.height)
            } else null,
            audioFormat = if (k == MediaKind.Audio && format == null) _audioFormat.value.ytArg else null,
            containerExt = if (k == MediaKind.Video && format == null) {
                _container.value.ext.takeIf { k == MediaKind.Video }
            } else null,
            requestLabel = label,
            title = summary.title,
            coverUrl = summary.thumbnailUrl,
            durationSec = summary.durationSec,
            formatId = format?.id,
            formatHeight = format?.height ?: 0,
            formatFps = format?.fps ?: 0,
            formatVcodec = format?.vcodec,
            formatAcodec = format?.acodec,
            formatExt = format?.ext,
        )
        _transient.value = when {
            k == MediaKind.Audio -> "Extracting audio · ${format?.ext ?: _audioFormat.value.label}"
            format != null -> "Downloading ${format.label}…"
            else -> "Downloading “${summary.title.take(48)}”…"
        }
    }

    /**
     * Starts a download from the browser's capture sheet.
     *
     * * [useDirect] + a captured direct file + video mode → the exact file
     *   the page plays, streamed through OkHttp with real progress (any site).
     * * everything else → the yt-dlp engine, fed with the best known target:
     *   a manifest found on the page, the page URL itself, or (audio-only +
     *   FFmpeg) the direct file to demux sound from.
     */
    fun startCapture(
        request: CaptureRequest,
        useDirect: Boolean,
        kind: MediaKind,
        quality: QualityPreset,
        container: ContainerPref,
        audioFormat: AudioFormatPref,
    ) {
        val title = request.title?.takeIf { it.isNotBlank() }
            ?: hostOf(request.pageUrl).ifBlank { request.pageUrl }

        // Direct file: the exact media the page plays (video or audio).
        // Streamed through OkHttp with real progress — works on any site.
        if (useDirect && request.directUrl != null) {
            repo.startDownload(
                url = request.directUrl,
                kind = kind,
                selector = null,
                audioFormat = null,
                containerExt = null,
                requestLabel = "Direct file",
                title = title,
                coverUrl = request.cover,
                durationSec = 0,
                directSource = true,
            )
            _transient.value = if (kind == MediaKind.Audio) {
                "Downloading “${title.take(48)}” (direct audio)…"
            } else {
                "Downloading “${title.take(48)}” (direct file)…"
            }
            return
        }

        // Engine path.
        val hasFfmpeg = ffmpegReady.value
        val isAudio = kind == MediaKind.Audio
        val url = when {
            // A manifest found on the page beats re-resolving the whole page.
            request.manifestUrl != null -> request.manifestUrl
            else -> request.pageUrl
        }
        val label = when {
            isAudio && !hasFfmpeg -> "Source audio"
            else -> FormatRules.requestLabel(kind, quality, container, audioFormat)
        }
        repo.startDownload(
            url = url,
            kind = kind,
            selector = if (isAudio) null else FormatRules.videoSelector(quality.height),
            audioFormat = if (isAudio && hasFfmpeg) audioFormat.ytArg else null,
            containerExt = if (isAudio) null else container.ext,
            requestLabel = label,
            title = title,
            coverUrl = request.cover,
            durationSec = 0,
            directSource = false,
        )
        _transient.value = if (isAudio) {
            "Extracting ${audioFormat.label} audio…"
        } else {
            "Downloading “${title.take(48)}”…"
        }
    }

    /**
     * Starts a download from the browser's *resolved* save sheet — i.e. the
     * page was parsed first (thumbnail / title / formats known).
     *
     * * [format] = null → the primary "Download best" CTA (kind/quality path,
     *   mirroring the home studio's quick download),
     * * [format] != null → one specific row the user picked from the
     *   "Available files" list (the engine gets its exact format id).
     *
     * The direct-file path still goes through [startCapture] with
     * useDirect = true.
     */
    fun startBrowserCapture(
        summary: MediaSummary,
        request: CaptureRequest,
        kind: MediaKind,
        format: MediaFormat? = null,
        quality: QualityPreset,
        container: ContainerPref,
        audioFormat: AudioFormatPref,
    ) {
        val cover = summary.thumbnailUrl ?: request.cover
        val title = summary.title.takeIf { it.isNotBlank() }
            ?: request.title?.takeIf { it.isNotBlank() }
            ?: hostOf(request.pageUrl).ifBlank { request.pageUrl }
        val targetFormat = format
        if (targetFormat != null) {
            repo.startDownload(
                url = summary.url,
                kind = targetFormat.kind,
                selector = null,
                audioFormat = null,
                containerExt = null,
                requestLabel = targetFormat.label,
                title = title,
                coverUrl = cover,
                durationSec = summary.durationSec,
                formatId = targetFormat.id,
                formatHeight = targetFormat.height,
                formatFps = targetFormat.fps,
                formatVcodec = targetFormat.vcodec,
                formatAcodec = targetFormat.acodec,
                formatExt = targetFormat.ext,
            )
            _transient.value = "Downloading ${targetFormat.label} · “${title.take(40)}”…"
            return
        }
        val hasFfmpeg = ffmpegReady.value
        val isAudio = kind == MediaKind.Audio
        repo.startDownload(
            url = summary.url,
            kind = kind,
            selector = if (isAudio) null else FormatRules.videoSelector(quality.height),
            audioFormat = if (isAudio && hasFfmpeg) audioFormat.ytArg else null,
            containerExt = if (isAudio) null else container.ext,
            requestLabel = if (isAudio && !hasFfmpeg) {
                "Source audio"
            } else {
                FormatRules.requestLabel(kind, quality, container, audioFormat)
            },
            title = title,
            coverUrl = cover,
            durationSec = summary.durationSec,
        )
        _transient.value = when {
            isAudio -> "Extracting ${audioFormat.label} audio · “${title.take(40)}”…"
            else -> "Downloading “${title.take(44)}”…"
        }
    }

    fun cancelTask(id: String) {
        repo.cancelTask(id)
        _transient.value = "Download cancelled"
    }

    fun pauseTask(id: String) {
        repo.pauseTask(id)
        _transient.value = "Paused"
    }

    fun resumeTask(id: String) {
        repo.resumeTask(id)
        _transient.value = "Resuming…"
    }

    fun retryTask(id: String) {
        repo.retryTask(id)
        _transient.value = "Retrying…"
    }

    fun removeTask(id: String) = repo.removeTask(id)

    /** Remove a batch of tasks (used by the Progress screen bulk action). */
    fun removeTasks(ids: List<String>) {
        ids.forEach { repo.removeTask(it) }
        if (ids.isNotEmpty()) _transient.value = "Removed ${ids.size} item${if (ids.size == 1) "" else "s"}"
    }

    fun pauseAllActive() {
        repo.pauseAllActive()
        _transient.value = "All downloads paused"
    }

    fun resumeAllFailed() {
        repo.resumeAllFailed()
        _transient.value = "Retrying failed downloads"
    }

    fun clearTerminal() {
        repo.clearTerminal()
        _transient.value = "Cleared finished downloads"
    }

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
