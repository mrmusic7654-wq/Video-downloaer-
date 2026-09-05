package com.vidma.downloader.features.downloads

import com.vidma.downloader.ui.components.core.VidmaIcons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidma.downloader.domain.model.DownloadState
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.GlowProgressArc
import com.vidma.downloader.ui.components.core.SectionTitle
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.components.media.TaskRow
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/**
 * Full-screen download centre. The compact tray is useful for a glance; this
 * screen is the detailed, always-live view for progress, ETA, retries and
 * queue state.
 */
@Composable
fun DownloadProgressScreen(
    vm: DownloaderViewModel,
    onBack: () -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val active = remember(tasks) { tasks.filter { it.isActive } }
    val visibleTasks = remember(tasks) { tasks.take(20) }
    val average = remember(active) {
        if (active.isEmpty()) 0f else active.map { it.progress }.average().toFloat()
    }
    val queued = remember(active) { active.count { it.state == DownloadState.Queued } }
    val finished = remember(tasks) { tasks.count { it.state == DownloadState.Completed } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VidmaIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 42.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium.copy(color = VidmaBase.TextHigh),
                    )
                    Text(
                        text = if (active.isEmpty()) "Your queue is clear" else "Live queue and transfer details",
                        style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                    )
                }
                if (active.isNotEmpty()) {
                    StatusPill(text = "${active.size} live", dotColor = palette.success, pulsing = true)
                }
            }
        }

        item {
            GlassCard(
                contentPadding = PaddingValues(20.dp),
                glowing = active.isNotEmpty(),
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowProgressArc(
                        progress = average,
                        size = 122.dp,
                        stroke = 8.dp,
                        palette = palette,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(average * 100).toInt().coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.headlineSmall.copy(color = VidmaBase.TextHigh),
                            )
                            Text(
                                text = if (active.isEmpty()) "ready" else "overall",
                                style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextMid),
                            )
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (active.isEmpty()) "Nothing downloading" else "Transfers in motion",
                            style = MaterialTheme.typography.titleLarge.copy(color = VidmaBase.TextHigh),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (active.isEmpty()) {
                                "Resolve a link or browse to a video page. Progress will appear here instantly."
                            } else {
                                "The engine reports live percentage, ETA and post-processing status for every item."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DownloadStat(
                    modifier = Modifier.weight(1f),
                    icon = VidmaIcons.Download,
                    value = active.size.toString(),
                    label = "active",
                    tint = palette.secondary,
                )
                DownloadStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.DateRange,
                    value = queued.toString(),
                    label = "queued",
                    tint = palette.warning,
                )
                DownloadStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.CheckCircle,
                    value = finished.toString(),
                    label = "finished",
                    tint = palette.success,
                )
            }
        }

        if (visibleTasks.isEmpty()) {
            item {
                GlassCard(contentPadding = PaddingValues(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .requiredSize(52.dp)
                                .background(palette.primary.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(VidmaIcons.Download, contentDescription = null, tint = palette.secondary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("No transfer history in this session", style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your finished files are kept in Library.",
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                        )
                    }
                }
            }
        } else {
            item {
                SectionTitle(
                    text = if (active.isEmpty()) "Recent activity" else "Live queue",
                    trailing = {
                        Text(
                            text = "${visibleTasks.size} shown",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                        )
                    },
                )
            }
            items(visibleTasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onCancel = vm::cancelTask,
                    onRetry = vm::retryTask,
                    onDismiss = vm::removeTask,
                )
            }
        }
    }
}

@Composable
private fun DownloadStat(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.requiredSize(19.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium.copy(color = VidmaBase.TextHigh))
                Text(label, style = MaterialTheme.typography.labelSmall.copy(color = palette.textLow))
            }
        }
    }
}
