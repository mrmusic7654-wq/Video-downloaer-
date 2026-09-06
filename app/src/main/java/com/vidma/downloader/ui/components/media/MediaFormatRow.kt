package com.vidma.downloader.ui.components.media

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidma.downloader.domain.model.MediaFormat
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.components.core.VidmaIcons
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.formatBytes

/**
 * MediaFormatRow — one tappable row in an "Available files" list:
 * thumbnail tile, quality label, codecs/fps/ext, estimated size and a
 * circular gradient download action. Shared by the home formats sheet and
 * the browser's parse-first save sheet so both screens pick files the
 * exact same way (thumbnail + size, no guessing).
 */
@Composable
fun MediaFormatRow(
    format: MediaFormat,
    cover: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaSquare(
                kind = format.kind,
                cover = cover,
                size = 56.dp,
                corner = 14.dp,
                palette = palette,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (format.mediaType == "video") {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "video-only",
                            style = MaterialTheme.typography.labelSmall.copy(color = palette.warning),
                            maxLines = 1,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        if (enabled) {
                            Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF2A2E46), Color(0xFF20243B)))
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VidmaIcons.Download,
                    contentDescription = "Download ${format.label}",
                    tint = if (enabled) Color.White else VidmaBase.TextLow,
                    modifier = Modifier.requiredSize(18.dp),
                )
            }
        }
    }
}

/**
 * Skeleton placeholder shown while formats are being read — a shimmering
 * bar so the list area never jumps in size.
 */
@Composable
fun MediaFormatRowSkeleton(
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse",
    )
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .requiredSize(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.primary.copy(alpha = 0.18f * pulse)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(13.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.14f * pulse)),
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .width(190.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.08f * pulse)),
                )
            }
        }
    }
}
