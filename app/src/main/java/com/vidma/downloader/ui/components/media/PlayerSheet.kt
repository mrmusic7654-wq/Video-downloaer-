package com.vidma.downloader.ui.components.media

import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ArrowForward
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vidma.downloader.data.storage.MediaStorage
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatBytes
import java.io.File

/**
 * Cinematic glass player sheet (ExoPlayer/Media3). Used from Library rows and
 * from completed queue rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    item: LibraryItem,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = VidmaBase.Scrim.copy(alpha = 0.72f),
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E1118), Color(0xFF06080E)),
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                // handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 5.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(18.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge.copy(color = VidmaBase.TextHigh),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.ext.uppercase())
                        append("  ·  ")
                        append(formatBytes(item.sizeBytes))
                    },
                    style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextMid),
                )
                Spacer(Modifier.height(16.dp))

                // ---- video / audio surface ----
                val context = LocalContext.current
                val media = remember(item.filePath) {
                    MediaStorage(context).contentUriFor(
                        item.filePath,
                        item.kind,
                        item.ext,
                    )
                }
                val uri: Uri? = remember(media) {
                    if (item.filePath.startsWith("content://")) media.first
                    else {
                        val f = File(item.filePath)
                        if (f.exists()) media.first else null
                    }
                }
                if (uri != null) {
                    val player = remember(item.id) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(uri))
                            prepare()
                            playWhenReady = true
                        }
                    }
                    DisposableEffect(player) {
                        onDispose { player.release() }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (item.kind == MediaKind.Video) 16f / 9f else 1.35f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    controllerAutoShow = true
                                    this.player = player
                                }
                            },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                } else {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 42.dp),
                    ) {
                        Text(
                            text = "Media file missing — it was probably moved or deleted.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = VidmaBase.TextMid),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- actions ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionPill(
                        text = "Share",
                        icon = Icons.Rounded.Share,
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        palette = palette,
                    )
                    ActionPill(
                        text = "Open",
                        icon = Icons.Rounded.ArrowForward,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        palette = palette,
                    )
                    ActionPill(
                        text = "Delete",
                        icon = Icons.Rounded.Delete,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        danger = true,
                        palette = palette,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val tint = if (danger) palette.danger else palette.secondary
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
            )
        }
    }
}
