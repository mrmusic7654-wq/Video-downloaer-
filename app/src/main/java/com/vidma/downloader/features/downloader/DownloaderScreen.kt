package com.vidma.downloader.features.downloader

import com.vidma.downloader.ui.components.core.VidmaIcons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.vidma.downloader.ui.components.media.PlayerSheet
import kotlinx.coroutines.flow.StateFlow
import com.vidma.downloader.domain.model.AudioFormatPref
import com.vidma.downloader.domain.model.ContainerPref
import com.vidma.downloader.domain.model.DownloadState
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.EngineStatus
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaFormat
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.model.qualityChoices
import com.vidma.downloader.ui.components.core.AuroraRing
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.GlassTextField
import com.vidma.downloader.ui.components.core.SectionTitle
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.components.core.VidmaButton
import com.vidma.downloader.ui.components.core.VidmaChoiceChip
import com.vidma.downloader.ui.components.core.VidmaGlassButton
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.components.core.VidmaModeToggle
import com.vidma.downloader.ui.components.media.MediaSquare
import com.vidma.downloader.ui.components.media.TaskRow
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatBytes
import com.vidma.downloader.util.formatCount
import com.vidma.downloader.util.formatDuration
import com.vidma.downloader.util.hostOf
import com.vidma.downloader.util.timeAgo

/**
 * HOME — "Download" tab.
 *
 * Two search bars (one for the in-app browser, one for paste-a-link),
 * a media resolver that lists every available stream with thumbnail /
 * title / size, and a live download queue.
 */
@Composable
fun DownloaderScreen(
    vm: DownloaderViewModel,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalVidmaPalette.current
    val urlText by vm.urlText.collectAsStateV()
    val searchText by vm.searchText.collectAsStateV()
    val kind by vm.kind.collectAsStateV()
    val quality by vm.quality.collectAsStateV()
    val audioFormat by vm.audioFormat.collectAsStateV()
    val container by vm.container.collectAsStateV()
    val fetchPhase by vm.fetchPhase.collectAsStateV()
    val downloads by vm.downloads.collectAsStateV()
    val library by vm.library.collectAsStateV()
    val engineReady by vm.engineReady.collectAsStateV()
    val engineStatus by vm.engineStatus.collectAsStateV()
    val ffmpegReady by vm.ffmpegReady.collectAsStateV()
    val sharedUrl by vm.lastSharedUrl.collectAsStateV()

    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val active = downloads.filter { it.isActive }
    val recentTasks = downloads.take(6)
    val libraryById = remember(library) { library.associateBy { it.id } }

    var playItem by remember { mutableStateOf<LibraryItem?>(null) }
    var showFormats by remember { mutableStateOf(false) }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) vm.adoptSharedUrl()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ================= header =================
        item {
            BrandHeader(
                activeCount = active.size,
                onDownloads = onOpenDownloads,
                onSettings = onOpenSettings,
                palette = palette,
            )
        }

        // ================= hero copy =================
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .requiredSize(8.dp)
                            .background(palette.secondary, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "VIDEO DOWNLOADER",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.secondary,
                            letterSpacing = 2.sp,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Grab any video\nfrom the web.",
                    style = MaterialTheme.typography.displayMedium.copy(
                        color = VidmaBase.TextHigh,
                        lineHeight = 46.sp,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Two ways in: search the web, or paste a link directly. YouTube · TikTok · Vimeo · Instagram & 1000+ more sites — vidma lists every available file so you pick by thumbnail and size, not by guessing.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = VidmaBase.TextMid),
                )
            }
        }

        // ================= two search bars =================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---- search the web ----
                GlassCard(contentPadding = PaddingValues(14.dp), glowing = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = palette.secondary,
                                modifier = Modifier.requiredSize(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "BROWSE THE WEB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.secondary,
                                    letterSpacing = 1.5.sp,
                                ),
                            )
                        }
                        Text(
                            text = "Search anything — when you find a video, tap its download icon.",
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                        )
                        GlassTextField(
                            value = searchText,
                            onValueChange = vm::onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Search or paste a web address…",
                            leadingIcon = Icons.Rounded.Search,
                            trailing = {
                                if (searchText.isNotEmpty()) {
                                    VidmaIconButton(
                                        icon = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        onClick = vm::clearSearch,
                                        size = 34.dp,
                                        palette = palette,
                                    )
                                } else {
                                    VidmaIconButton(
                                        icon = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste",
                                        onClick = {
                                            val clip = clipboard.getText()?.text
                                            if (clip != null) {
                                                vm.onSearchChange(clip)
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } else {
                                                vm.showMessage("Clipboard is empty")
                                            }
                                        },
                                        size = 34.dp,
                                        palette = palette,
                                    )
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { vm.onSearchSubmit() },
                                onGo = { vm.onSearchSubmit() },
                            ),
                        )
                    }
                }

                // ---- paste a link ----
                GlassCard(contentPadding = PaddingValues(14.dp), glowing = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = palette.secondary,
                                modifier = Modifier.requiredSize(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "PASTE A LINK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.secondary,
                                    letterSpacing = 1.5.sp,
                                ),
                            )
                        }
                        Text(
                            text = "Direct link → vidma lists every file the engine understands, with thumbnails and size.",
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                        )
                        GlassTextField(
                            value = urlText,
                            onValueChange = vm::onUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "https://youtube.com/watch?v=…",
                            leadingIcon = Icons.Rounded.ArrowForward,
                            trailing = {
                                if (urlText.isNotEmpty()) {
                                    VidmaIconButton(
                                        icon = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        onClick = vm::clearUrl,
                                        size = 34.dp,
                                        palette = palette,
                                    )
                                } else {
                                    VidmaIconButton(
                                        icon = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste",
                                        onClick = {
                                            val clip = clipboard.getText()?.text
                                            if (clip != null) {
                                                vm.onUrlChange(clip)
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } else {
                                                vm.showMessage("Clipboard is empty")
                                            }
                                        },
                                        size = 34.dp,
                                        palette = palette,
                                    )
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { vm.fetch() },
                                onGo = { vm.fetch() },
                            ),
                        )
                        // share chip (URL sent into vidma from another app)
                        if (sharedUrl != null) {
                            GlassCard(
                                onClick = vm::adoptSharedUrl,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = palette.secondary,
                                        modifier = Modifier.requiredSize(16.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Open shared link  ·  ${hostOf(sharedUrl!!)}",
                                        style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VidmaButton(
                                text = when (fetchPhase) {
                                    is FetchPhase.Fetching -> "Resolving…"
                                    else -> "Resolve link"
                                },
                                icon = if (fetchPhase is FetchPhase.Fetching) null else Icons.Rounded.Search,
                                loading = fetchPhase is FetchPhase.Fetching,
                                onClick = vm::fetch,
                                modifier = Modifier.weight(1f),
                                height = 52.dp,
                                palette = palette,
                            )
                        }
                    }
                }
            }
        }

        // ================= resolver output / errors =================
        when (val phase = fetchPhase) {
            is FetchPhase.Error -> item {
                ErrorBanner(message = phase.message, onRetry = vm::fetch, palette = palette)
            }
            is FetchPhase.Fetching -> item {
                ResolvingCard(url = urlText, engineStatus = engineStatus, palette = palette)
            }
            is FetchPhase.Ready -> item {
                MediaStudio(
                    summary = phase.summary,
                    kind = kind,
                    quality = quality,
                    audioFormat = audioFormat,
                    container = container,
                    engineReady = engineReady,
                    ffmpegReady = ffmpegReady,
                    onKind = { vm.onKindChange(it) },
                    onQuality = { vm.onQualityChange(it) },
                    onAudio = { vm.onAudioChange(it) },
                    onContainer = { vm.onContainerChange(it) },
                    onOpenFormats = { showFormats = true },
                    onQuickDownload = { vm.startDownload() },
                    palette = palette,
                )
            }
            is FetchPhase.Idle -> Unit
        }

        // ================= queue =================
        if (recentTasks.isNotEmpty()) {
            item {
                SectionTitle(
                    text = if (active.isEmpty()) "Recent downloads" else "Download queue",
                    trailing = {
                        Text(
                            text = "${active.size} active",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                        )
                    },
                )
            }
            items(recentTasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onCancel = vm::cancelTask,
                    onPause = if (task.state == DownloadState.Downloading) ({ vm.pauseTask(it) }) else null,
                    onResume = if (task.isFailed || task.state == DownloadState.Cancelled) ({ vm.resumeTask(it) }) else null,
                    onRetry = vm::retryTask,
                    onDismiss = vm::removeTask,
                    onPlay = { t ->
                        libraryById[t.id]?.let { playItem = it }
                    },
                )
            }
            if (downloads.size > 6) {
                item {
                    Text(
                        text = "Open the Progress tab to manage every download at once",
                        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenDownloads)
                            .padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else if (engineReady) {
            // ================= library teaser =================
            if (library.isNotEmpty()) {
                item {
                    SectionTitle(text = "In your library", trailing = {
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                            modifier = Modifier.clickable(onClick = onOpenLibrary),
                        )
                    })
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(library.take(8), key = { it.id }) { item ->
                            LibraryTeaserCard(item = item, onClick = { playItem = item })
                        }
                    }
                }
            }
        } else {
            item {
                EngineStartingCard(
                    engineStatus = engineStatus,
                    palette = palette,
                    onRetry = vm::retryEngine,
                )
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
    }

    // ------------- Formats sheet (the "pick what to download" UI) -------------
    val ready = (fetchPhase as? FetchPhase.Ready)?.summary
    if (showFormats && ready != null) {
        FormatsSheet(
            summary = ready,
            engineReady = engineReady,
            ffmpegReady = ffmpegReady,
            onDismiss = { showFormats = false },
            onDownload = { format ->
                showFormats = false
                vm.startDownload(format)
            },
        )
    }

    // ------------- playback -------------
    val playerItem = playItem
    if (playerItem != null) {
        PlayerSheetHost(
            item = playerItem,
            onDismiss = { playItem = null },
            vm = vm,
            palette = palette,
        )
    }
}

/** Tiny helper so screens read flows lifecycle-aware. */
@Composable
private fun <T> StateFlow<T>.collectAsStateV(): State<T> = collectAsStateWithLifecycle()

@Composable
private fun BrandHeader(
    activeCount: Int,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    palette: VidmaPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // logo mark
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .requiredSize(38.dp)
                    .background(
                        Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary)),
                        RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "vidma",
                style = TextStyle(
                    fontFamily = com.vidma.downloader.ui.theme.VidmaFonts.Sora,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = VidmaBase.TextHigh,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onDownloads)
                    .padding(vertical = 4.dp),
            ) {
                StatusPill(text = "$activeCount running", dotColor = palette.success, pulsing = true)
            }
            Spacer(Modifier.width(10.dp))
        }
        VidmaIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Settings",
            onClick = onSettings,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, palette: VidmaPalette) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        borderVisible = true,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .requiredSize(26.dp)
                    .background(palette.danger.copy(alpha = 0.18f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = palette.danger,
                    modifier = Modifier.requiredSize(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Can’t resolve that link",
                    style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap to try again",
                    style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }
    }
}

/**
 * Resolver loading card. It reports the *real* phase: while the first-run
 * runtime is still unpacking it says so (previously the card pretended a scan
 * was running, which looked like a hang), then it shows the metadata scan.
 */
@Composable
private fun ResolvingCard(url: String, engineStatus: EngineStatus, palette: VidmaPalette) {
    val (title, subtitle) = when (engineStatus) {
        is EngineStatus.Initializing ->
            "Preparing the download engine…" to
                "First launch unpacks the bundled yt-dlp runtime — this only happens once."
        is EngineStatus.Ready ->
            "Scanning & extracting metadata…" to url.take(90)
        is EngineStatus.Failed ->
            "Engine not ready" to engineStatus.message
    }
    GlassCard(contentPadding = PaddingValues(18.dp), glowing = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (engineStatus) {
                is EngineStatus.Failed -> Box(
                    modifier = Modifier
                        .requiredSize(26.dp)
                        .background(palette.danger.copy(alpha = 0.18f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = palette.danger,
                        modifier = Modifier.requiredSize(16.dp),
                    )
                }
                else -> AuroraRing(size = 26.dp, palette = palette)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EngineStartingCard(engineStatus: EngineStatus, palette: VidmaPalette, onRetry: () -> Unit) {
    val (title, subtitle) = when (engineStatus) {
        is EngineStatus.Initializing ->
            "Preparing the download engine" to
                "First launch prepares the lightweight yt-dlp runtime. Full builds can add FFmpeg for merging and audio conversion."
        is EngineStatus.Failed ->
            "The download engine failed to start" to engineStatus.message
        is EngineStatus.Ready ->
            "Preparing the download engine" to ""
    }
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            when (engineStatus) {
                is EngineStatus.Failed -> Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = palette.danger,
                    modifier = Modifier.requiredSize(30.dp),
                )
                else -> AuroraRing(size = 34.dp, palette = palette)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (engineStatus is EngineStatus.Failed) "Tap to retry" else "Tap to retry if this takes too long",
                style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}

// =====================================================================
//  MediaStudio — format picker shown after a successful resolve
// =====================================================================

@Composable
private fun MediaStudio(
    summary: MediaSummary,
    kind: MediaKind,
    quality: com.vidma.downloader.domain.model.QualityPreset,
    audioFormat: AudioFormatPref,
    container: ContainerPref,
    engineReady: Boolean,
    ffmpegReady: Boolean,
    onKind: (MediaKind) -> Unit,
    onQuality: (com.vidma.downloader.domain.model.QualityPreset) -> Unit,
    onAudio: (AudioFormatPref) -> Unit,
    onContainer: (ContainerPref) -> Unit,
    onOpenFormats: () -> Unit,
    onQuickDownload: () -> Unit,
    palette: VidmaPalette,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // media summary hero
        GlassCard(contentPadding = PaddingValues(14.dp), glowing = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaSquare(
                    kind = MediaKind.Video,
                    cover = summary.thumbnailUrl,
                    size = 92.dp,
                    corner = 18.dp,
                    palette = palette,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary.uploader?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                                maxLines = 1,
                            )
                        }
                        if (summary.durationSec > 0) {
                            StatusPill(text = formatDuration(summary.durationSec), dotColor = palette.tertiary)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(text = summary.platformLabel, dotColor = palette.primary)
                        if (summary.viewCount != null) {
                            Text(
                                text = "${formatCount(summary.viewCount)} views",
                                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }
        }

        // video/audio mode + settings
        GlassCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VidmaModeToggle(
                    options = listOf(
                        "Video" to (kind == MediaKind.Video),
                        "Audio only" to (kind == MediaKind.Audio),
                    ),
                    onSelect = { i -> onKind(if (i == 0) MediaKind.Video else MediaKind.Audio) },
                )
                if (kind == MediaKind.Video) {
                    QualityStudio(quality, container, summary, ffmpegReady, onQuality, onContainer, palette)
                } else {
                    AudioStudio(audioFormat, ffmpegReady, onAudio, palette)
                }
                // Two CTAs:
                //   * Primary "Download" → the easy "best" path (no sheet)
                //   * "Browse all files"  → the Formats sheet with thumbnails
                //     + size for every stream the engine understood.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VidmaButton(
                        text = when {
                            !engineReady -> "Engine starting…"
                            kind == MediaKind.Video -> buildString {
                                append("Download best")
                                if (quality.height != null) append(" ${quality.height}p")
                                append(" · ").append(container.label)
                            }
                            else -> if (ffmpegReady) "Extract ${audioFormat.label} audio" else "Download source audio"
                        },
                        icon = VidmaIcons.Download,
                        enabled = engineReady,
                        onClick = onQuickDownload,
                        modifier = Modifier.weight(1f),
                        height = 54.dp,
                        palette = palette,
                    )
                    VidmaGlassButton(
                        text = "Files",
                        icon = Icons.Rounded.Tune,
                        onClick = onOpenFormats,
                        height = 54.dp,
                        corner = 18.dp,
                        modifier = Modifier.weight(0.7f),
                        palette = palette,
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityStudio(
    quality: com.vidma.downloader.domain.model.QualityPreset,
    container: ContainerPref,
    summary: MediaSummary,
    ffmpegReady: Boolean,
    onQuality: (com.vidma.downloader.domain.model.QualityPreset) -> Unit,
    onContainer: (ContainerPref) -> Unit,
    palette: VidmaPalette,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "QUALITY", style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow))
            Spacer(Modifier.weight(1f))
            val maxH = summary.availableHeights.firstOrNull()
            if (maxH != null) {
                Text(
                    text = "up to ${maxH}p available",
                    style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(qualityChoices()) { preset ->
                val maxH = summary.availableHeights.firstOrNull()
                val unavailable = maxH != null && preset.height != null && preset.height > maxH
                VidmaChoiceChip(
                    text = preset.label,
                    subtitle = if (unavailable) "not offered" else null,
                    selected = quality == preset,
                    onClick = { if (!unavailable) onQuality(preset) },
                    palette = palette,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (ffmpegReady) "CONTAINER" else "DIRECT OUTPUT",
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val containerChoices = if (ffmpegReady) {
                ContainerPref.entries.toList()
            } else {
                listOf(ContainerPref.Mp4)
            }
            items(containerChoices) { c ->
                VidmaChoiceChip(
                    text = c.label,
                    selected = container == c,
                    onClick = { onContainer(c) },
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun AudioStudio(
    audioFormat: AudioFormatPref,
    ffmpegReady: Boolean,
    onAudio: (AudioFormatPref) -> Unit,
    palette: VidmaPalette,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "AUDIO FORMAT", style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow))
            Spacer(Modifier.weight(1f))
            Text(
                text = if (ffmpegReady) "FFmpeg conversion" else "Source audio · lite mode",
                style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AudioFormatPref.entries.toList()) { f ->
                VidmaChoiceChip(
                    text = f.label,
                    selected = audioFormat == f,
                    onClick = { onAudio(f) },
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun LibraryTeaserCard(item: LibraryItem, onClick: () -> Unit, palette: VidmaPalette = LocalVidmaPalette.current) {
    Column(
        modifier = Modifier
            .width(152.dp)
            .clickable(onClick = onClick),
    ) {
        MediaSquare(kind = item.kind, cover = item.coverUri, size = 152.dp, corner = 20.dp, palette = palette)
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.ext.uppercase()} · ${timeAgo(item.addedAtMs)}",
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
            maxLines = 1,
        )
    }
}

/** Player sheet for a finished item + share/open/delete wiring. */
@Composable
private fun PlayerSheetHost(
    item: LibraryItem,
    onDismiss: () -> Unit,
    vm: DownloaderViewModel,
    palette: VidmaPalette,
) {
    val context = LocalContext.current
    val mediaRef = remember(item.filePath) {
        com.vidma.downloader.data.storage.MediaStorage(context).contentUriFor(item.filePath, item.kind, item.ext)
    }
    PlayerSheet(
        item = item,
        onDismiss = onDismiss,
        onShare = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mediaRef.second
                putExtra(Intent.EXTRA_STREAM, mediaRef.first)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TEXT, item.title)
            }
            context.startActivity(Intent.createChooser(send, "Share with…"))
        },
        onOpen = {
            val open = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(mediaRef.first, mediaRef.second)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(open) }
                .onFailure { vm.showMessage("No app can open this file type") }
        },
        onDelete = {
            vm.deleteLibraryItem(item)
            onDismiss()
        },
        palette = palette,
    )
}

// =====================================================================
//  Formats sheet — every stream the engine exposed, with thumbnail /
//  size / codec. Each row has its own download button so the user can
//  queue several formats at once.
// =====================================================================

/**
 * Modal bottom sheet that lists every format the engine understood for the
 * resolved media. Each row shows a kind icon, the format's quality label
 * (with fps / codec when relevant), the estimated file size, and a
 * single-tap "Download" action. Multi-file at once is the default — the
 * user can keep tapping rows and they all enqueue.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FormatsSheet(
    summary: MediaSummary,
    engineReady: Boolean,
    ffmpegReady: Boolean,
    onDismiss: () -> Unit,
    onDownload: (MediaFormat) -> Unit,
) {
    val palette = LocalVidmaPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val formats = summary.availableFormats
    val videos = remember(formats) { formats.filter { it.kind == MediaKind.Video } }
    val audios = remember(formats) { formats.filter { it.kind == MediaKind.Audio } }
    val subtitles = remember(summary) { emptyList<MediaFormat>() } // hook for future

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = VidmaBase.Scrim.copy(alpha = 0.7f),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0E1118), Color(0xFF06080E))),
                )
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            // handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .requiredSize(width = 44.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
            )
            Spacer(Modifier.height(14.dp))

            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaSquare(
                    kind = MediaKind.Video,
                    cover = summary.thumbnailUrl,
                    size = 56.dp,
                    corner = 14.dp,
                    palette = palette,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Available files",
                        style = MaterialTheme.typography.headlineSmall.copy(color = VidmaBase.TextHigh),
                    )
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VidmaIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 38.dp,
                )
            }
            Spacer(Modifier.height(8.dp))
            // legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = if (ffmpegReady) "FFmpeg on" else "Lean build",
                    dotColor = if (ffmpegReady) palette.success else palette.warning,
                )
                Text(
                    text = "${videos.size} video · ${audios.size} audio",
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                )
            }
            Spacer(Modifier.height(14.dp))

            if (formats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AuroraRing(size = 28.dp, palette = palette)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Reading formats…",
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (videos.isNotEmpty()) {
                        item { SectionTitle(text = "Video") }
                        items(videos, key = { "v-${it.id}" }) { format ->
                            FormatRow(
                                format = format,
                                cover = summary.thumbnailUrl,
                                enabled = engineReady,
                                onClick = { onDownload(format) },
                                palette = palette,
                            )
                        }
                    }
                    if (audios.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)) }
                        item { SectionTitle(text = "Audio") }
                        items(audios, key = { "a-${it.id}" }) { format ->
                            FormatRow(
                                format = format,
                                cover = summary.thumbnailUrl,
                                enabled = engineReady,
                                onClick = { onDownload(format) },
                                palette = palette,
                            )
                        }
                    }
                    if (subtitles.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)) }
                        item { SectionTitle(text = "Subtitles") }
                        items(subtitles, key = { "s-${it.id}" }) { format ->
                            FormatRow(
                                format = format,
                                cover = summary.thumbnailUrl,
                                enabled = engineReady,
                                onClick = { onDownload(format) },
                                palette = palette,
                            )
                        }
                    }
                    // Helpful hint if the engine only returned a single muxed
                    // stream (e.g. on the lean build, no FFmpeg).
                    if (formats.size == 1) {
                        item {
                            Text(
                                text = "Only one stream is offered for this link. The lean build " +
                                    "skips FFmpeg merging — get the full build for separate audio.",
                                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: MediaFormat,
    cover: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    palette: VidmaPalette,
) {
    val secondaryText = buildList {
        format.vcodec?.let { add(it.uppercase()) }
        format.acodec?.let { add(it.uppercase()) }
        if (format.fps > 0) add("${format.fps} fps")
        add(format.ext.uppercase())
    }.joinToString(" · ")

    GlassCard(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // small cover or icon tile
            Box(
                modifier = Modifier
                    .requiredSize(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (cover != null) Color.Transparent
                        else Brush.linearGradient(listOf(palette.tertiary.copy(alpha = 0.4f), palette.primary.copy(alpha = 0.3f)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MediaSquare(
                    kind = format.kind,
                    cover = cover,
                    size = 56.dp,
                    corner = 14.dp,
                    palette = palette,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                    )
                    if (format.mediaType == "video") {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "video-only",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.warning),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (secondaryText.isNotBlank()) secondaryText else format.ext.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (format.sizeBytes > 0) {
                        StatusPill(
                            text = formatBytes(format.sizeBytes),
                            dotColor = palette.secondary,
                        )
                    } else {
                        Text(
                            text = "size unknown",
                            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .requiredSize(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary))
                        else Brush.linearGradient(listOf(Color(0xFF2A2E46), Color(0xFF20243B)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VidmaIcons.Download,
                    contentDescription = "Download",
                    tint = if (enabled) Color.White else VidmaBase.TextLow,
                    modifier = Modifier.requiredSize(18.dp),
                )
            }
        }
    }
}
