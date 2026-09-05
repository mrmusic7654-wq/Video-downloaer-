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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.PageMediaSource
import com.vidma.downloader.domain.model.QualityPreset
import com.vidma.downloader.domain.model.qualityChoices
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.VidmaButton
import com.vidma.downloader.ui.components.core.VidmaChoiceChip
import com.vidma.downloader.ui.components.core.VidmaIcons
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.hostOf

/**
 * Bottom sheet shown when the browser's download action is tapped.
 *
 * The page's playing media (if any) is offered as a **direct file** — the
 * exact file the page plays, streamed with real progress on any site — while
 * the engine-resolve path offers the best streams on supported platforms.
 * Video / audio-only, quality, container and audio format are all selectable,
 * mirroring the home format studio.
 */
@Composable
fun CaptureSheet(
    request: CaptureRequest,
    sources: List<PageMediaSource>,
    engineReady: Boolean,
    ffmpegReady: Boolean,
    onDismiss: () -> Unit,
    onDownload: (
        useDirect: Boolean,
        kind: MediaKind,
        quality: QualityPreset,
        container: ContainerPref,
        audioFormat: AudioFormatPref,
    ) -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val directSource = sources.firstOrNull { it.url == request.directUrl }
    val hasDirect = directSource != null
    val hasManifest = request.manifestUrl != null

    var useDirect by remember { mutableStateOf(hasDirect) }
    var kind by remember { mutableStateOf(MediaKind.Video) }
    var quality by remember { mutableStateOf(QualityPreset.Auto) }
    var container by remember { mutableStateOf(ContainerPref.Mp4) }
    var audioFormat by remember { mutableStateOf(AudioFormatPref.Mp3) }

    // A direct file is exactly what the element plays — the mode is locked
    // to that element's kind (splitting audio out of one mp4 needs the
    // engine's re-encode path).
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
                        text = "Save this media",
                        style = MaterialTheme.typography.headlineSmall.copy(color = VidmaBase.TextHigh),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = request.title?.takeIf { it.isNotBlank() }
                            ?: hostOf(request.pageUrl).ifBlank { request.pageUrl },
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

            // ---------------- source ----------------
            Text(
                text = "SOURCE",
                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
            )
            Spacer(Modifier.height(8.dp))
            if (hasDirect) {
                SourceOptionRow(
                    selected = useDirect,
                    title = if (directSource!!.kind == MediaKind.Audio) {
                        "Page audio · direct file"
                    } else {
                        "Page video · direct file"
                    },
                    subtitle = "The exact file this page plays — fastest, works on any site",
                    palette = palette,
                    onClick = { useDirect = true },
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
                onClick = { useDirect = false },
            )

            Spacer(Modifier.height(18.dp))

            // ---------------- format ----------------
            Text(
                text = "FORMAT",
                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
            )
            Spacer(Modifier.height(8.dp))

            if (useDirect) {
                LockedModeSegment(kind = directSource?.kind ?: MediaKind.Video, palette = palette)
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
                    onSelect = { kind = if (it == 0) MediaKind.Video else MediaKind.Audio },
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
                                onClick = { quality = preset },
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
                                    onClick = { container = c },
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
                                    onClick = { audioFormat = f },
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
                onClick = {
                    onDownload(useDirect, effectiveKind, quality, container, audioFormat)
                },
                modifier = Modifier.fillMaxWidth(),
                palette = palette,
            )
        }
    }
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
