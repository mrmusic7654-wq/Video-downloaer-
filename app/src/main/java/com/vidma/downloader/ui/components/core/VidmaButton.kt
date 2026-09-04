package com.vidma.downloader.ui.components.core

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/**
 * VidmaButton — the premium CTA: gradient fill, breathing glow, sweeping
 * shimmer (while [loading]) and a tactile press-scale.
 */
@Composable
fun VidmaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 58.dp,
    corner: Dp = 20.dp,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "press",
    )
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "sweep",
    )

    val gradient = Brush.linearGradient(palette.brandGradient)
    val dimmed = !enabled

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .shadow(
                elevation = if (enabled) 18.dp else 2.dp,
                shape = shape,
                ambientColor = if (dimmed) Color.Transparent else palette.primary.copy(alpha = 0.55f),
                spotColor = if (dimmed) Color.Transparent else palette.secondary.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(
                brush = if (dimmed) Brush.linearGradient(listOf(Color(0xFF2A2E46), Color(0xFF20243B))) else gradient,
                shape = shape,
            )
            .then(
                if (enabled) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                } else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
            ) { onClick() }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Shimmer sweep across the button face while loading
        if (loading && enabled) {
            Canvas(modifier = Modifier.matchParentSize().clip(shape)) {
                val sweepW = size.height * 0.75f
                val x = (shimmer * (size.width + sweepW * 2f)) - sweepW * 2f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(x - sweepW / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(sweepW, size.height),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                AuroraRing(size = 20.dp, stroke = 2.5.dp, palette = palette, invert = true)
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (dimmed) VidmaBase.TextLow else Color.White,
                    modifier = Modifier.requiredSize(22.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = if (dimmed) VidmaBase.TextLow else Color.White,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Secondary CTA — frosted glass with gradient hairline border. */
@Composable
fun VidmaGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 58.dp,
    corner: Dp = 20.dp,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "press",
    )
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .clip(shape)
            .background(Color.White.copy(alpha = if (enabled) 0.07f else 0.035f), shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        palette.primary.copy(alpha = 0.5f),
                        VidmaBase.GlassStroke,
                        palette.secondary.copy(alpha = 0.35f),
                    ),
                ),
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) palette.secondary else VidmaBase.TextLow,
                modifier = Modifier.requiredSize(20.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (enabled) VidmaBase.TextHigh else VidmaBase.TextLow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Square glass icon button (used in headers & sheets). */
@Composable
fun VidmaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = VidmaBase.TextHigh,
    enabled: Boolean = true,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.88f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "press",
    )
    val shape = RoundedCornerShape(size / 2.6f)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .requiredSize(size)
            .clip(shape)
            .background(
                if (enabled) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.02f),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.12f else 0.05f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.requiredSize(size * 0.46f),
        )
    }
}

/** Small circular glow dot used for status legends. */
@Composable
fun GlowDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse",
    )
    val alpha = if (pulse) glow else 1f
    Box(
        modifier = modifier
            .requiredSize(8.dp)
            .shadow(6.dp, CircleShape, ambientColor = color.copy(alpha = 0.9f * alpha), spotColor = color)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}
