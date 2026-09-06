package com.vidma.downloader.features.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidma.downloader.domain.model.AudioFormatPref
import com.vidma.downloader.domain.model.CaptureRequest
import com.vidma.downloader.domain.model.ContainerPref
import com.vidma.downloader.domain.model.MediaFormat
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.model.PageMediaSource
import com.vidma.downloader.domain.model.QualityPreset
import com.vidma.downloader.domain.model.qualityChoices
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.components.core.VidmaButton
import com.vidma.downloader.ui.components.core.VidmaChoiceChip
import com.vidma.downloader.ui.components.core.VidmaIcons
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.components.media.MediaFormatRow
import com.vidma.downloader.ui.components.media.MediaSquare
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatCount
import com.vidma.downloader.util.formatDuration
import com.vidma.downloader.util.hostOf

/**
 * Bottom sheet shown when the browser's floating download action is tapped.
 *
 * The page is **parsed first** (FAB animation), and when that succeeds
 * [summary] is present: the sheet opens with the video's thumbnail, title,
 * channel, duration and *every available file* (quality · codec · size),
 * plus the direct file the page plays as a one-tap option. Picking a row
 * downloads exactly that stream; the primary CTA grabs the best match.
 *
 * When the engine could not resolve the page ([summary] == null) the sheet
 * falls back to the original flow: direct file vs. engine resolve with the
 * full quality/container/audio controls.
 */
@Composable
fun CaptureSheet(
    request: CaptureRequest,
    sources: List<PageMediaSource>,
    engineReady: Boolean,
    ffmpegReady: Boolean,
    onDismiss: () -> Unit,
    summary: MediaSummary? = null,
    onDownload: (
        useDirect: Boolean,
        kind: MediaKind,
        quality: QualityPreset,
        container: ContainerPref,
        audioFormat: AudioFormatPref,
    ) -> Unit,
    onDownloadBest: (
        summary: MediaSummary,
        kind: MediaKind,
        quality: QualityPreset,
        container: ContainerPref,
        audioFormat: AudioFormatPref,
    ) -> Unit,
    onDownloadFormat: (summary: MediaSummary, format: MediaFormat) -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val directSource = sources.firstOrNull { it.url == request.directUrl }
    val hasDirect = directSource != null

    var useDirect by remember(hasDirect, summary != null) { mutableStateOf(hasDirect && summary == null) }
    var kind by remember { mutableStateOf(MediaKind.Video) }
    var quality by remember { mutableStateOf(QualityPreset.Auto) }
    var container by remember { mutableStateOf(ContainerPref.Mp4) }
    var audioFormat by remember { mutableStateOf(AudioFormatPref.Mp3) }

    // A direct file is exactly what the element plays — its mode is locked
    // (splitting audio out of an mp4 needs the engine's re-encode path).
    val effectiveKind = if (useDirect) directSource?.kind ?: MediaKind.Video else kind
    val canDownload = useDirect || engineReady

    Box(modifier = Modifier.fillMaxSize()) {
        // scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onDismiss),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.9f)
                .shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    spotColor = palette.primary.copy(alpha = 0.35f),
                )
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF2191038), Color(0xF70A0618)),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // grab handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .requiredSize(width = 44.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
            )
            Spacer(Modifier.height(14.dp))

            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (summary != null) "Ready to download" else "Save this media",
                        style = MaterialTheme.typography.headlineSmall.copy(color = VidmaBase.TextHigh),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = (summary?.title ?: request.title?.takeIf { it.isNotBlank() }
                            ?: hostOf(request.pageUrl).ifBlank { request.pageUrl }),
                        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VidmaIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 40.dp,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (summary != null) {
                ResolvedSheetBody(
                    summary = summary,
                    request = request,
                    hasDirect = hasDirect,
                    directLabel = if (directSource?.kind == MediaKind.Audio) {
                        "Page audio · direct file"
                    } else {
                        "Page video · direct file"
                    },
                    useDirect = useDirect,
                    kind = effectiveKind,
                    quality = quality,
                    container = container,
                    audioFormat = audioFormat,
                    ffmpegReady = ffmpegReady,
                    canDownload = canDownload,
                    onUseDirect = { useDirect = it },
                    onKind = { kind = it },
                    onQuality = { quality = it },
                    onContainer = { container = it },
                    onAudio = { audioFormat = it },
                    onDownloadDirect = {
                        onDownload(
                            true,
                            directSource?.kind ?: MediaKind.Video,
                            quality,
                            container,
                            audioFormat,
                        )
                    },
                    onDownloadBest = {
                        onDownloadBest(summary, effectiveKind, quality, container, audioFormat)
                    },
                    onDownloadFormat = { format -> onDownloadFormat(summary, format) },
                    palette = palette,
                )
            } else {
                FallbackSheetBody(
                    request = request,
                    hasDirect = hasDirect,
                    hasManifest = request.manifestUrl != null,
                    directIsAudio = directSource?.kind == MediaKind.Audio,
                    useDirect = useDirect,
                    effectiveKind = effectiveKind,
                    quality = quality,
                    container = container,
                    audioFormat = audioFormat,
                    engineReady = engineReady,
                    ffmpegReady = ffmpegReady,
                    canDownload = canDownload,
                    onUseDirect = { useDirect = it },
                    onKind = { kind = it },
                    onQuality = { quality = it },
                    onContainer = { container = it },
                    onAudio = { audioFormat = it },
                    onCta = {
                        onDownload(useDirect, effectiveKind, quality, container, audioFormat)
                    },
                    palette = palette,
                )
            }
        }
    }
}

// =====================================================================
//  Resolved body — thumbnail / title / all files (the parse-first sheet)
// =====================================================================

@Composable
private fun ResolvedSheetBody(
    summary: MediaSummary,
    request: CaptureRequest,
    hasDirect: Boolean,
    directLabel: String,
    useDirect: Boolean,
    kind: MediaKind,
    quality: QualityPreset,
    container: ContainerPref,
    audioFormat: AudioFormatPref,
    ffmpegReady: Boolean,
    canDownload: Boolean,
    onUseDirect: (Boolean) -> Unit,
    onKind: (MediaKind) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onContainer: (ContainerPref) -> Unit,
    onAudio: (AudioFormatPref) -> Unit,
    onDownloadDirect: () -> Unit,
    onDownloadBest: () -> Unit,
    onDownloadFormat: (MediaFormat) -> Unit,
    palette: VidmaPalette,
) {
    val cover = summary.thumbnailUrl ?: request.cover
    val videos = remember(summary) { summary.availableFormats.filter { it.kind == MediaKind.Video } }
    val audios = remember(summary) { summary.availableFormats.filter { it.kind == MediaKind.Audio } }

    // ---------------- hero: thumbnail + title ----------------
    GlassCard(contentPadding = PaddingValues(14.dp), glowing = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaSquare(
                kind = MediaKind.Video,
                cover = cover,
                size = 84.dp,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    summary.uploader?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (summary.durationSec > 0) {
                        StatusPill(text = formatDuration(summary.durationSec), dotColor = palette.tertiary)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(text = summary.platformLabel, dotColor = palette.primary)
                    if (summary.viewCount != null && summary.viewCount > 0) {
                        Text(
                            text = "${formatCount(summary.viewCount)} views",
                            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // ---------------- direct file (if the page plays one) ----------------
    if (hasDirect) {
        SourceOptionRow(
            selected = useDirect,
            title = directLabel,
            subtitle = "The exact file this page plays — fastest, works on any site",
            palette = palette,
            onClick = { onUseDirect(true) },
        )
        Spacer(Modifier.height(8.dp))
    }

    // ---------------- engine files ----------------
    SourceOptionRow(
        selected = !useDirect,
        title = "Engine files · ${videos.size} video · ${audios.size} audio",
        subtitle = "Every quality the site offers, with codec and estimated size",
        palette = palette,
        onClick = { onUseDirect(false) },
    )

    if (useDirect) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (ffmpegReady) {
                "Direct files are saved as-is. Want MP3 audio or a specific quality? Pick an engine file below."
            } else {
                "Direct files are saved exactly as the page plays them — no re-encoding, no waiting."
            },
            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
        )
        Spacer(Modifier.height(18.dp))
        VidmaButton(
            text = "Download direct file",
            icon = VidmaIcons.Download,
            enabled = canDownload,
            onClick = onDownloadDirect,
            modifier = Modifier.fillMaxWidth(),
            palette = palette,
        )
        return
    }

    Spacer(Modifier.height(16.dp))

    // ---------------- mode + quality chips ----------------
    ModeSegments(
        videoSelected = kind == MediaKind.Video,
        audioSelected = kind == MediaKind.Audio,
        onSelect = { onKind(if (it == 0) MediaKind.Video else MediaKind.Audio) },
        palette = palette,
    )
    Spacer(Modifier.height(14.dp))

    if (kind == MediaKind.Video) {
        Text(
            text = "PREFERENCE",
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
        )
        Spacer(Modifier.height(8.dp))
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
        if (ffmpegReady) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "CONTAINER",
                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ContainerPref.entries.toList()) { c ->
                    VidmaChoiceChip(
                        text = c.label,
                        selected = container == c,
                        onClick = { onContainer(c) },
                        palette = palette,
                    )
                }
            }
        }
    } else {
        Text(
            text = "AUDIO FORMAT",
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
        )
        Spacer(Modifier.height(8.dp))
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

    Spacer(Modifier.height(20.dp))

    // ---------------- every available file ----------------
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (kind == MediaKind.Video) "AVAILABLE VIDEO FILES" else "AVAILABLE AUDIO FILES",
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "tap a file to download it",
            style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
        )
    }
    Spacer(Modifier.height(10.dp))

    val list = if (kind == MediaKind.Video) videos else audios
    if (list.isEmpty()) {
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = palette.warning,
                    modifier = Modifier.requiredSize(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (kind == MediaKind.Audio) {
                        "No separate audio streams were listed for this link — use the main download button."
                    } else {
                        "The site did not list separate streams — use the main download button below."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                )
            }
        }
    } else {
        // The sheet itself scrolls (verticalScroll); rows are plain Column
        // children to avoid nested same-direction scrollables. Show at most
        // the first 12 streams — the primary CTA covers "best of the rest".
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.take(12).forEach { format ->
                MediaFormatRow(
                    format = format,
                    cover = cover,
                    enabled = canDownload,
                    onClick = { onDownloadFormat(format) },
                    palette = palette,
                )
            }
            if (list.size > 12) {
                Text(
                    text = "+${list.size - 12} more files — use “Download best” to grab the highest quality.",
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    // ---------------- primary CTA ----------------
    VidmaButton(
        text = when {
            !canDownload -> "Engine preparing…"
            kind == MediaKind.Video -> buildString {
                append("Download best")
                if (quality.height != null) append(" ${quality.height}p")
                if (ffmpegReady) append(" · ${container.label}")
            }
            else -> if (ffmpegReady) "Extract ${audioFormat.label} audio" else "Download source audio"
        },
        icon = VidmaIcons.Download,
        enabled = canDownload,
        onClick = onDownloadBest,
        modifier = Modifier.fillMaxWidth(),
        height = 56.dp,
        palette = palette,
    )
}

// =====================================================================
//  Fallback body — engine could not read the page (original flow)
// =====================================================================

@Composable
private fun FallbackSheetBody(
    request: CaptureRequest,
    hasDirect: Boolean,
    hasManifest: Boolean,
    directIsAudio: Boolean,
    useDirect: Boolean,
    effectiveKind: MediaKind,
    quality: QualityPreset,
    container: ContainerPref,
    audioFormat: AudioFormatPref,
    engineReady: Boolean,
    ffmpegReady: Boolean,
    canDownload: Boolean,
    onUseDirect: (Boolean) -> Unit,
    onKind: (MediaKind) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onContainer: (ContainerPref) -> Unit,
    onAudio: (AudioFormatPref) -> Unit,
    onCta: () -> Unit,
    palette: VidmaPalette,
) {
    // ---------------- source ----------------
    Text(
        text = "SOURCE",
        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
    )
    Spacer(Modifier.height(8.dp))
    if (hasDirect) {
        SourceOptionRow(
            selected = useDirect,
            title = if (directIsAudio) "Page audio · direct file" else "Page video · direct file",
            subtitle = "The exact file this page plays — fastest, works on any site",
            palette = palette,
            onClick = { onUseDirect(true) },
        )
        Spacer(Modifier.height(8.dp))
    }
    SourceOptionRow(
        selected = !useDirect,
        title = "Engine resolve · best quality",
        subtitle = buildString {
            append("yt-dlp extracts the best streams")
            if (hasManifest) append(" — an HLS/DASH stream was found on this page")
        },
        palette = palette,
        onClick = { onUseDirect(false) },
    )

    Spacer(Modifier.height(18.dp))

    // ---------------- format ----------------
    Text(
        text = "FORMAT",
        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
    )
    Spacer(Modifier.height(8.dp))

    if (useDirect) {
        LockedModeSegment(kind = effectiveKind, palette = palette)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (ffmpegReady) {
                "Direct files are saved as-is. Want MP3 audio or a specific quality? Use the engine source above."
            } else {
                "Direct files are saved exactly as the page plays them — no re-encoding, no waiting."
            },
            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
        )
    } else {
        ModeSegments(
            videoSelected = effectiveKind == MediaKind.Video,
            audioSelected = effectiveKind == MediaKind.Audio,
            onSelect = { onKind(if (it == 0) MediaKind.Video else MediaKind.Audio) },
            palette = palette,
        )
        Spacer(Modifier.height(14.dp))
        if (effectiveKind == MediaKind.Video) {
            Text(
                text = "QUALITY",
                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
            )
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(14.dp))
            if (ffmpegReady) {
                Text(
                    text = "CONTAINER",
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ContainerPref.entries.toList()) { c ->
                        VidmaChoiceChip(
                            text = c.label,
                            selected = container == c,
                            onClick = { onContainer(c) },
                            palette = palette,
                        )
                    }
                }
            } else {
                Text(
                    text = "MP4 single-stream output (the lean build skips merging)",
                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                )
            }
        } else {
            Text(
                text = "AUDIO FORMAT",
                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
            )
            Spacer(Modifier.height(8.dp))
            if (ffmpegReady) {
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
            } else {
                Text(
                    text = "Source audio (m4a/opus) — the lean build skips conversion",
                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // ---------------- cta ----------------
    VidmaButton(
        text = when {
            !canDownload -> "Engine preparing…"
            useDirect -> "Download direct file"
            effectiveKind == MediaKind.Video -> buildString {
                append("Download ")
                append(if (quality == QualityPreset.Auto) "best" else "${quality.height}p")
                if (ffmpegReady) append(" · ${container.label}")
            }
            else -> if (ffmpegReady) "Extract ${audioFormat.label} audio" else "Download source audio"
        },
        icon = VidmaIcons.Download,
        enabled = canDownload,
        onClick = onCta,
        modifier = Modifier.fillMaxWidth(),
        palette = palette,
    )
}

/** Radio-style source selection row. */
@Composable
private fun SourceOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    palette: VidmaPalette,
) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
        borderVisible = selected,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .requiredSize(20.dp)
                    .background(Color.White.copy(alpha = 0.06f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(if (selected) 12.dp else 0.dp)
                        .background(if (selected) palette.secondary else Color.Transparent, CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = if (selected) Color.White else VidmaBase.TextHigh,
                        fontWeight = FontWeight.SemiBold,
                    ),
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

/** Interactive Video / Audio-only segmented control. */
@Composable
private fun ModeSegments(
    videoSelected: Boolean,
    audioSelected: Boolean,
    onSelect: (Int) -> Unit,
    palette: VidmaPalette,
) {
    ModeToggleContent(
        options = listOf("Video" to videoSelected, "Audio only" to audioSelected),
        onSelect = onSelect,
        palette = palette,
        enabled = true,
    )
}

/** Static segmented control shown when the mode is locked by the source. */
@Composable
private fun LockedModeSegment(kind: MediaKind, palette: VidmaPalette) {
    ModeToggleContent(
        options = listOf(
            "Video" to (kind == MediaKind.Video),
            "Audio only" to (kind == MediaKind.Audio),
        ),
        onSelect = { },
        palette = palette,
        enabled = false,
    )
}

@Composable
private fun ModeToggleContent(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    palette: VidmaPalette,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = if (enabled) 0.05f else 0.03f), shape)
            .border(1.dp, VidmaBase.GlassStrokeSoft, shape)
            .padding(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (label, selected) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected && enabled) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                            RoundedCornerShape(14.dp),
                        )
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    palette.primary.copy(alpha = if (enabled) 0.65f else 0.3f),
                                    palette.secondary.copy(alpha = if (enabled) 0.4f else 0.2f),
                                ),
                            ),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .then(if (enabled) Modifier.clickable { onSelect(index) } else Modifier)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = when {
                                selected && enabled -> palette.secondary
                                selected -> VidmaBase.TextMid
                                else -> VidmaBase.TextLow
                            },
                        ),
                    )
                }
            }
        }
    }
}
