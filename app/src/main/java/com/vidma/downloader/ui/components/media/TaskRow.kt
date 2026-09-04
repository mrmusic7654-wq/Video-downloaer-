package com.vidma.downloader.ui.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidma.downloader.domain.model.DownloadState
import com.vidma.downloader.domain.model.DownloadTask
import com.vidma.downloader.domain.model.percentText
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.GlowDot
import com.vidma.downloader.ui.components.core.GlowProgressArc
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatDuration
import com.vidma.downloader.util.hostOf

/** A single row in the queue / tasks list. */
@Composable
fun TaskRow(
    task: DownloadTask,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onPlay: ((DownloadTask) -> Unit)? = null,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- leading visual ----
            when (task.state) {
                DownloadState.Queued, DownloadState.Resolving -> {
                    Box(
                        modifier = Modifier
                            .requiredSize(52.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlowDot(color = palette.secondary, pulse = true)
                    }
                }
                DownloadState.Downloading, DownloadState.Processing, DownloadState.Finishing -> {
                    GlowProgressArc(
                        progress = task.progress,
                        size = 52.dp,
                        stroke = 4.dp,
                        palette = palette,
                    ) {
                        Text(
                            text = task.percentText(),
                            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextHigh),
                        )
                    }
                }
                DownloadState.Completed -> {
                    Box(
                        modifier = Modifier
                            .requiredSize(52.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.secondary.copy(alpha = 0.5f), palette.primary.copy(alpha = 0.7f)),
                                ),
                                RoundedCornerShape(26.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.requiredSize(26.dp),
                        )
                    }
                }
                DownloadState.Failed -> {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = palette.danger,
                        modifier = Modifier
                            .requiredSize(46.dp)
                            .padding(6.dp),
                    )
                }
                DownloadState.Cancelled -> {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = VidmaBase.TextLow,
                        modifier = Modifier
                            .requiredSize(46.dp)
                            .padding(10.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // ---- content ----
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title?.takeIf { it.isNotBlank() } ?: hostOf(task.url),
                    style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = task.requestLabel.ifBlank { task.url },
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (task.state) {
                    DownloadState.Downloading -> {
                        val eta = task.etaSeconds.takeIf { it > 0 }
                        if (eta != null || task.statusLine.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    eta?.let { append("ETA ${formatDuration(it.toInt())}  ") }
                                    append(task.statusLine.take(96))
                                },
                                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextMid),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    DownloadState.Failed -> {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = task.error?.take(120) ?: "Download failed",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.danger),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DownloadState.Completed -> Unit
                    else -> {
                        if (task.statusLine.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = task.statusLine.take(90),
                                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextMid),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // ---- trailing actions ----
            when {
                task.isActive -> TrailingAction(onClick = { onCancel(task.id) }, icon = Icons.Rounded.Close, tint = palette.danger)
                task.state == DownloadState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrailingAction(onClick = { onRetry(task.id) }, icon = Icons.Rounded.Replay, tint = palette.secondary)
                    TrailingAction(onClick = { onDismiss(task.id) }, icon = Icons.Rounded.Close, tint = VidmaBase.TextLow)
                }
                task.state == DownloadState.Cancelled -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrailingAction(onClick = { onRetry(task.id) }, icon = Icons.Rounded.Replay, tint = VidmaBase.TextMid)
                    TrailingAction(onClick = { onDismiss(task.id) }, icon = Icons.Rounded.Close, tint = VidmaBase.TextLow)
                }
                task.state == DownloadState.Completed -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onPlay != null) {
                        TrailingAction(onClick = { onPlay(task) }, icon = Icons.Rounded.PlayArrow, tint = palette.secondary)
                    }
                    TrailingAction(onClick = { onDismiss(task.id) }, icon = Icons.Rounded.Close, tint = VidmaBase.TextLow)
                }
            }
        }
    }
}

@Composable
private fun TrailingAction(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .requiredSize(38.dp)
            .background(Color.White.copy(alpha = 0.06f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.requiredSize(19.dp))
    }
}

/** Legend chip describing a task's current phase. */
@Composable
fun TaskStatusChip(task: DownloadTask, palette: VidmaPalette = LocalVidmaPalette.current) {
    val triple: Triple<String, Color, Boolean> = when (task.state) {
        DownloadState.Queued -> Triple("Queued", palette.textLow, false)
        DownloadState.Resolving -> Triple("Resolving", palette.secondary, true)
        DownloadState.Downloading -> Triple("${task.percentText()} downloaded", palette.primary, true)
        DownloadState.Processing -> Triple("Processing", palette.tertiary, true)
        DownloadState.Finishing -> Triple("Finishing", palette.warning, true)
        DownloadState.Completed -> Triple("Completed", palette.success, false)
        DownloadState.Failed -> Triple("Failed", palette.danger, false)
        DownloadState.Cancelled -> Triple("Cancelled", palette.textLow, false)
    }
    StatusPill(text = triple.first, dotColor = triple.second, pulsing = triple.third)
}
