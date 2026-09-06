package com.vidma.downloader.features.downloads

import com.vidma.downloader.ui.components.core.VidmaIcons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidma.downloader.domain.model.DownloadState
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.SectionTitle
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.components.media.TaskRow
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatBytes
import kotlin.math.roundToInt

/**
 * Full-screen download centre.
 *
 * The previous overall "meter" arc is gone — it averaged a handful of
 * concurrent tasks and didn't really mean anything. The screen now leads
 * with a slim header (live count, total transferred, speed) and renders
 * every task as its own row with live percent, speed, ETA, pause / resume
 * and per-row actions.
 *
 * The top-right kebab menu drives the bulk actions: pause all, resume
 * failed, batch delete, clear completed. Long-press a row to enter
 * multi-select mode and apply them to a chosen set.
 */
@Composable
fun DownloadProgressScreen(
    vm: DownloaderViewModel,
    onBack: () -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val tasks by vm.downloads.collectAsStateWithLifecycle()
    val active = remember(tasks) { tasks.filter { it.isActive } }
    val visibleTasks = remember(tasks) { tasks }

    val running = remember(active) { active.count { it.state == DownloadState.Downloading } }
    val resolving = remember(active) { active.count { it.state == DownloadState.Resolving } }
    val finished = remember(tasks) { tasks.count { it.state == DownloadState.Completed } }
    val failed = remember(tasks) { tasks.count { it.state == DownloadState.Failed } }

    val totalSpeedBps = remember(active) { active.sumOf { it.speedBytesPerSec } }
    val totalTransferred = remember(active) { active.sumOf { it.bytesDownloaded } }
    val totalSize = remember(active) { active.sumOf { it.totalBytes } }
    val overallPercent = remember(active) {
        if (active.isEmpty()) 0f
        else active.map { it.progress }.average().toFloat().coerceIn(0f, 1f)
    }

    var menuOpen by remember { mutableStateOf(false) }
    val hasActive = active.isNotEmpty()
    val hasFailed = failed > 0
    val hasTerminal = finished + failed + tasks.count { it.state == DownloadState.Cancelled } > 0

    // multi-select state — long-press a row to enter, then tap more rows
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateOf<Set<String>>(emptySet()) }

    fun exitSelection() {
        selectionMode = false
        selectedIds.value = emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ===== top bar =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VidmaIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = {
                    if (selectionMode) exitSelection() else onBack()
                },
                size = 42.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectionMode) "${selectedIds.value.size} selected" else "Downloads",
                    style = MaterialTheme.typography.headlineMedium.copy(color = VidmaBase.TextHigh),
                )
                Text(
                    text = when {
                        selectionMode -> "Tap rows to add or remove"
                        hasActive -> "Live · ${running} downloading · ${resolving} resolving"
                        tasks.isEmpty() -> "Your queue is clear"
                        else -> "All transfers finished"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                )
            }
            if (selectionMode) {
                // bulk delete + clear
                VidmaIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete selected",
                    tint = palette.danger,
                    onClick = {
                        vm.removeTasks(selectedIds.value.toList())
                        exitSelection()
                    },
                    size = 42.dp,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clickable { exitSelection() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium.copy(color = palette.secondary),
                    )
                }
            } else {
                if (hasActive || hasFailed || hasTerminal) {
                    Box {
                        VidmaIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "Bulk actions",
                            onClick = { menuOpen = true },
                            size = 42.dp,
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            BulkAction(
                                icon = VidmaIcons.Pause,
                                text = "Pause all",
                                enabled = hasActive,
                                onClick = {
                                    menuOpen = false
                                    vm.pauseAllActive()
                                },
                            )
                            BulkAction(
                                icon = Icons.Rounded.Refresh,
                                text = "Retry all failed",
                                enabled = hasFailed,
                                onClick = {
                                    menuOpen = false
                                    vm.resumeAllFailed()
                                },
                            )
                            BulkAction(
                                icon = Icons.Rounded.PlayArrow,
                                text = "Enter multi-select",
                                enabled = tasks.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    selectionMode = true
                                },
                            )
                            BulkAction(
                                icon = Icons.Rounded.Delete,
                                text = "Clear finished",
                                enabled = hasTerminal,
                                onClick = {
                                    menuOpen = false
                                    vm.clearTerminal()
                                },
                            )
                        }
                    }
                }
            }
        }

        // ===== live summary =====
        if (hasActive) {
            LiveSummaryBar(
                overallPercent = overallPercent,
                running = running,
                resolving = resolving,
                totalSpeedBps = totalSpeedBps,
                totalTransferred = totalTransferred,
                totalSize = totalSize,
                palette = palette,
            )
        }

        // ===== list =====
        if (visibleTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .requiredSize(60.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.secondary.copy(alpha = 0.30f), palette.primary.copy(alpha = 0.18f)),
                                ),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(VidmaIcons.Download, contentDescription = null, tint = palette.secondary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "No transfers yet",
                        style = MaterialTheme.typography.titleMedium.copy(color = VidmaBase.TextHigh),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Paste a link or browse to a video page — progress will appear here in real time.",
                        style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionTitle(
                        text = if (hasActive) "Live queue" else "Activity",
                        trailing = {
                            Text(
                                text = "${visibleTasks.size} item${if (visibleTasks.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall.copy(color = palette.secondary),
                            )
                        },
                    )
                }
                items(visibleTasks, key = { it.id }) { task ->
                    val selected = task.id in selectedIds.value
                    TaskRow(
                        task = task,
                        onCancel = vm::cancelTask,
                        onPause = if (task.state == DownloadState.Downloading) ({ vm.pauseTask(it) }) else null,
                        onResume = if (task.isFailed || task.state == DownloadState.Cancelled) ({ vm.resumeTask(it) }) else null,
                        onRetry = vm::retryTask,
                        onDismiss = vm::removeTask,
                        selected = selected,
                        onSelectToggle = if (selectionMode) {
                            { t ->
                                selectedIds.value = selectedIds.value.toMutableSet().apply {
                                    if (!add(t.id)) remove(t.id)
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }
}

/** Slim summary band: overall %, running count, live speed, bytes moved. */
@Composable
private fun LiveSummaryBar(
    overallPercent: Float,
    running: Int,
    resolving: Int,
    totalSpeedBps: Long,
    totalTransferred: Long,
    totalSize: Long,
    palette: VidmaPalette,
) {
    val pct = (overallPercent * 100f).roundToInt().coerceIn(0, 100)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(14.dp),
        glowing = running > 0,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleLarge.copy(color = VidmaBase.TextHigh),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "in motion",
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                )
                Spacer(Modifier.weight(1f))
                if (totalSpeedBps > 0) {
                    StatusPill(
                        text = "${formatBytes(totalSpeedBps)}/s",
                        dotColor = palette.secondary,
                        pulsing = true,
                    )
                }
            }
            // a thin progress line — overall progress only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(overallPercent)
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(listOf(palette.secondary, palette.primary)),
                            RoundedCornerShape(50),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "$running downloading",
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextMid),
                )
                if (resolving > 0) {
                    Text(
                        text = "$resolving resolving",
                        style = MaterialTheme.typography.labelSmall.copy(color = palette.warning),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (totalSize > 0) {
                    Text(
                        text = "${formatBytes(totalTransferred)} / ${formatBytes(totalSize)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextLow),
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) VidmaBase.TextHigh else VidmaBase.TextLow,
                    modifier = Modifier.requiredSize(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = text,
                    color = if (enabled) VidmaBase.TextHigh else VidmaBase.TextLow,
                )
            }
        },
        onClick = onClick,
        enabled = enabled,
    )
}
