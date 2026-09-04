package com.vidma.downloader.ui.components.media

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidma.downloader.domain.model.LibraryItem
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import java.io.File

/**
 * MediaArt — rounded thumbnail with a subtle gradient sheen. Falls back to a
 * premium abstract gradient tile + kind icon when no cover is available.
 */
@Composable
fun MediaArt(
    kind: MediaKind,
    modifier: Modifier = Modifier,
    cover: String? = null,
    icon: ImageVector? = null,
    corner: Dp = 16.dp,
    palette: VidmaPalette = LocalVidmaPalette.current,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(corner)
    val shimmer by rememberInfiniteTransition(label = "art").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "shimmer",
    )
    val model = remember(cover) {
        when {
            cover.isNullOrBlank() -> null
            cover.startsWith("http://") || cover.startsWith("https://") -> cover
            else -> File(cover).takeIf { it.exists() }
        }
    }

    Box(modifier = modifier.clip(shape).background(Color(0xFF0D1024))) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(coil.compose.LocalPlatformContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // soft top sheen for glass depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent),
                        )
                    ),
            )
        } else {
            // abstract gradient tile + icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                palette.primary.copy(alpha = 0.75f),
                                palette.primary.copy(alpha = 0.25f),
                                palette.tertiary.copy(alpha = 0.45f),
                            )
                        )
                    ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(palette.secondary.copy(alpha = 0.6f * (0.7f + 0.3f * shimmer)), Color.Transparent),
                        ),
                        radius = w * 0.5f,
                        center = androidx.compose.ui.geometry.Offset(w * 0.8f, w * 0.2f),
                    )
                }
                Icon(
                    imageVector = icon ?: kindIcon(kind),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(30.dp),
                )
            }
        }
        content()
    }
}

/** Icons used to distinguish audio/video rows. */
fun kindIcon(kind: MediaKind): ImageVector =
    if (kind == MediaKind.Video) {
        Icons.Rounded.PlayArrow
    } else {
        Icons.Rounded.AudioFile
    }

/** Small square tile for queue rows / chips. */
@Composable
fun MediaSquare(kind: MediaKind, cover: String?, size: Dp = 54.dp, corner: Dp = 14.dp, palette: VidmaPalette = LocalVidmaPalette.current) {
    MediaArt(
        kind = kind,
        cover = cover,
        corner = corner,
        palette = palette,
        modifier = Modifier.requiredSize(size),
    )
}
