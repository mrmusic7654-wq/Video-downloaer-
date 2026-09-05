package com.vidma.downloader.features.downloader

import com.vidma.downloader.ui.components.core.VidmaIcons
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vidma.downloader.domain.model.LibraryItem
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
import com.vidma.downloader.util.looksLikeUrl
import com.vidma.downloader.util.normalizeUrl
import com.vidma.downloader.util.timeAgo

/**
 * HOME — "Download" tab: the paste-URL hero, media resolver, format studio
 * and live download queue.
 */
@Composable
fun DownloaderScreen(
    vm: DownloaderViewModel,
    onOpenLibrary: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalVidmaPalette.current
    val urlText by vm.urlText.collectAsStateV()
    val kind by vm.kind.collectAsStateV()
    val quality by vm.quality.collectAsStateV()
    val audioFormat by vm.audioFormat.collectAsStateV()
    val container by vm.container.collectAsStateV()
    val videoOnly by vm.videoOnly.collectAsStateV()
    val fetchPhase by vm.fetchPhase.collectAsStateV()
    val downloads by vm.downloads.collectAsStateV()
    val library by vm.library.collectAsStateV()
    val engineReady by vm.engineReady.collectAsStateV()
    val engineError by vm.engineError.collectAsStateV()
    val ffmpegReady by vm.ffmpegReady.collectAsStateV()
    val sharedUrl by vm.lastSharedUrl.collectAsStateV()

    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val active = downloads.filter { it.isActive }
    val recentTasks = downloads.take(6)
    val libraryById = remember(library) { library.associateBy { it.id } }

    var playItem by remember { mutableStateOf<LibraryItem?>(null) }

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
                    text = "YouTube · TikTok · Vimeo · Instagram & 1000+ more sites — paste a link and vidma’s engine does the rest.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = VidmaBase.TextMid),
                )
            }
        }

        // ================= url form =================
        item {
            GlassCard(
                contentPadding = PaddingValues(16.dp),
                glowing = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "ONE-TAP CAPTURE",
                        style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextLow),
                    )
                    Text(
                        text = "Paste a link or browse with Chromium — both use the same queue.",
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
                                VidmaGlassButton(
                                    text = "Paste",
                                    icon = Icons.Rounded.ArrowForward,
                                    onClick = {
                                        val clip = clipboard.getText()?.text
                                        if (clip != null) {
                                            vm.onUrlChange(clip)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } else {
                                            vm.showMessage("Clipboard is empty")
                                        }
                                    },
                                    height = 40.dp,
                                    corner = 13.dp,
                                    palette = palette,
                                )
                            }
                        },
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
                                is FetchPhase.Ready -> "Change format"
                                else -> "Resolve link"
                            },
                            icon = if (fetchPhase is FetchPhase.Ready) null else Icons.Rounded.Search,
                            loading = fetchPhase is FetchPhase.Fetching,
                            onClick = vm::fetch,
                            modifier = Modifier.weight(1f),
                            height = 54.dp,
                            palette = palette,
                        )
                        VidmaGlassButton(
                            text = "Chromium",
                            icon = Icons.Rounded.ArrowForward,
                            onClick = onOpenBrowser,
                            height = 54.dp,
                            corner = 20.dp,
                            modifier = Modifier.weight(0.72f),
                            palette = palette,
                        )
                    }
                }
            }
        }

        // ================= resolver output / errors =================
        when (val phase = fetchPhase) {
            is FetchPhase.Error -> item {
                ErrorBanner(message = phase.message, palette = palette)
            }
            is FetchPhase.Fetching -> item {
                ResolvingCard(url = urlText, engineReady = engineReady, palette = palette)
            }
            is FetchPhase.Ready -> item {
                MediaStudio(
                    summary = phase.summary,
                    kind = kind,
                    quality = quality,
                    audioFormat = audioFormat,
                    container = container,
                    videoOnly = videoOnly,
                    engineReady = engineReady,
                    ffmpegReady = ffmpegReady,
                    onKind = { vm.onKindChange(it) },
                    onQuality = { vm.onQualityChange(it) },
                    onAudio = { vm.onAudioChange(it) },
                    onContainer = { vm.onContainerChange(it) },
                    onVideoOnly = vm::onVideoOnlyChange,
                    onDownload = vm::startDownload,
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
                        text = "Older items live in your Library",
                        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLibrary)
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
                EngineStartingCard(error = engineError, palette = palette, onRetry = vm::retryEngine)
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
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
private fun ErrorBanner(message: String, palette: VidmaPalette) {
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
            }
        }
    }
}

@Composable
private fun ResolvingCard(url: String, engineReady: Boolean, palette: VidmaPalette) {
    GlassCard(contentPadding = PaddingValues(18.dp), glowing = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuroraRing(size = 26.dp, palette = palette)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // First run unpacks the Python/yt-dlp runtime, which can take
                    // a while — tell the user that's what is happening instead of
                    // implying the URL is stuck.
                    text = if (engineReady) "Scanning & extracting metadata…"
                    else "Preparing the download engine (first run)…",
                    style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (engineReady) url.take(90)
                    else "This happens only once. The video is read after the engine is warm.",
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                    maxLines = if (engineReady) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EngineStartingCard(error: String?, palette: VidmaPalette, onRetry: () -> Unit) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AuroraRing(size = 34.dp, palette = palette)
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (error == null) "Preparing the download engine" else "Engine needs attention",
                style = MaterialTheme.typography.titleSmall.copy(color = if (error == null) VidmaBase.TextHigh else palette.danger),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (error == null) {
                    "First launch prepares the lightweight yt-dlp runtime. Full builds can add FFmpeg for merging and audio conversion."
                } else {
                    error
                },
                style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (error == null) "Preparing…" else "Tap to retry",
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
    videoOnly: Boolean,
    engineReady: Boolean,
    ffmpegReady: Boolean,
    onKind: (MediaKind) -> Unit,
    onQuality: (com.vidma.downloader.domain.model.QualityPreset) -> Unit,
    onAudio: (AudioFormatPref) -> Unit,
    onContainer: (ContainerPref) -> Unit,
    onVideoOnly: (Boolean) -> Unit,
    onDownload: () -> Unit,
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
                    options = listOf("Video" to (kind == MediaKind.Video), "Audio" to (kind == MediaKind.Audio)),
                    onSelect = { i -> onKind(if (i == 0) MediaKind.Video else MediaKind.Audio) },
                )
                if (kind == MediaKind.Video) {
                    QualityStudio(quality, container, summary, videoOnly, ffmpegReady, onQuality, onContainer, onVideoOnly, palette)
                } else {
                    AudioStudio(audioFormat, ffmpegReady, onAudio, palette)
                }
                VidmaButton(
                    text = when {
                        !engineReady -> "Engine starting…"
                        kind == MediaKind.Video && videoOnly -> buildString {
                            append("Download")
                            if (quality.height != null) append(" ${quality.height}p")
                            append("  ·  video only")
                        }
                        kind == MediaKind.Video -> buildString {
                            append("Download")
                            if (quality.height != null) append(" ${quality.height}p")
                            append("  ·  ${container.label}")
                        }
                        else -> if (ffmpegReady) "Extract ${audioFormat.label} audio" else "Download source audio"
                    },
                    icon = VidmaIcons.Download,
                    enabled = engineReady,
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun QualityStudio(
    quality: com.vidma.downloader.domain.model.QualityPreset,
    container: ContainerPref,
    summary: MediaSummary,
    videoOnly: Boolean,
    ffmpegReady: Boolean,
    onQuality: (com.vidma.downloader.domain.model.QualityPreset) -> Unit,
    onContainer: (ContainerPref) -> Unit,
    onVideoOnly: (Boolean) -> Unit,
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
                VidmaChoiceChip(
                    text = preset.label,
                    selected = quality == preset,
                    onClick = { onQuality(preset) },
                    palette = palette,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // Video + audio vs video-only stream. Video-only needs no remuxing,
        // so the container row is hidden when it is selected.
        Text(
            text = "OUTPUT",
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VidmaChoiceChip(
                text = "Video + audio",
                selected = !videoOnly,
                onClick = { onVideoOnly(false) },
                palette = palette,
            )
            VidmaChoiceChip(
                text = "Video only",
                selected = videoOnly,
                onClick = { onVideoOnly(true) },
                palette = palette,
            )
        }
        if (!videoOnly) {
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
