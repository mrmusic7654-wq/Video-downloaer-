package com.vidma.downloader.features.library

import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Delete
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.core.EmptyState
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.GlassTextField
import com.vidma.downloader.ui.components.core.SectionTitle
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.components.media.MediaArt
import com.vidma.downloader.ui.components.media.PlayerSheet
import com.vidma.downloader.ui.components.media.kindIcon
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatBytes
import com.vidma.downloader.util.timeAgo

/** LIBRARY — finished videos/audio, searchable, playable. */
@Composable
fun LibraryScreen(
    vm: DownloaderViewModel,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val items by vm.library.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FilterKind.All) }
    var playItem by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(items, query, filter) {
        items.filter { item ->
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.url.contains(q, ignoreCase = true)
            val matchesFilter = when (filter) {
                FilterKind.All -> true
                FilterKind.Video -> item.kind == MediaKind.Video
                FilterKind.Audio -> item.kind == MediaKind.Audio
            }
            matchesQuery && matchesFilter
        }
    }
    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineLarge.copy(color = VidmaBase.TextHigh),
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = "${items.size} items  ·  ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextMid),
                    )
                }
            }
            if (items.isNotEmpty()) {
                VidmaIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Clear library",
                    onClick = { confirmClear = true },
                    tint = VidmaBase.TextMid,
                )
            }
        }

        // search + filter
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search your downloads…",
                leadingIcon = Icons.Rounded.Search,
                trailing = {
                    if (query.isNotEmpty()) {
                        VidmaIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            onClick = { query = "" },
                            size = 30.dp,
                            palette = palette,
                        )
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterKind.entries.forEach { f ->
                    val selected = filter == f
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) palette.primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = f.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selected) Color.White else VidmaBase.TextMid,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            items.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.List,
                    title = "Nothing here yet",
                    subtitle = "Paste a link on the Download tab and finished media lands in your library — thumbnails, sizes and all.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            filtered.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "No matches",
                    subtitle = "Try a different search or filter.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        LibraryCard(item = item, onClick = { playItem = item })
                    }
                }
            }
        }
    }

    // player + sheets
    playItem?.let { item ->
        val mediaRef = remember(item.filePath) {
            com.vidma.downloader.data.storage.MediaStorage(context).contentUriFor(item.filePath, item.kind, item.ext)
        }
        PlayerSheet(
            item = item,
            onDismiss = { playItem = null },
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
            },
            onDelete = {
                vm.deleteLibraryItem(item)
                playItem = null
            },
            palette = palette,
        )
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF151931),
            titleContentColor = VidmaBase.TextHigh,
            textContentColor = VidmaBase.TextMid,
            title = { Text("Clear entire library?") },
            text = { Text("All ${items.size} downloaded files will be deleted from this device.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.clearLibrary()
                    confirmClear = false
                }) { Text("Delete all", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) {
                    Text("Keep", color = palette.secondary)
                }
            },
        )
    }
}

private enum class FilterKind(val label: String) {
    All("All"),
    Video("Videos"),
    Audio("Audio"),
}

@Composable
private fun LibraryCard(item: LibraryItem, onClick: () -> Unit, palette: VidmaPalette = LocalVidmaPalette.current) {
    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box {
            MediaArt(
                kind = item.kind,
                cover = item.coverUri,
                icon = kindIcon(item.kind),
                corner = 21.dp,
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                // bottom scrim + labels
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Color(0xE6070A18)),
                            )
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${item.ext.uppercase()} · ${formatBytes(item.sizeBytes)} · ${timeAgo(item.addedAtMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.65f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
