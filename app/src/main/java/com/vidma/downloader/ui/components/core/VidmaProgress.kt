package com.vidma.downloader.ui.components.core

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * AuroraRing — indeterminate loader: a rotating gradient arc.
 * [invert] = true when placed on a saturated gradient (light track).
 */
@Composable
fun AuroraRing(
    size: Dp = 28.dp,
    stroke: Dp = 3.dp,
    modifier: Modifier = Modifier,
    invert: Boolean = false,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(modifier = modifier.requiredSize(size).rotate(angle)) {
        val track = if (invert) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
        val sweep = 270f
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(palette.secondary, palette.primary, palette.tertiary, palette.secondary),
            ),
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * GlowProgressArc — determinate progress ring with gradient arc + glowing head.
 * Renders optional [label] in the middle (percent text etc.).
 */
@Composable
fun GlowProgressArc(
    progress: Float, // 0f..1f
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 7.dp,
    palette: VidmaPalette = LocalVidmaPalette.current,
    label: (@Composable BoxScope.() -> Unit)? = null,
) {
    val p = progress.coerceIn(0f, 1f)
    val strokePx = stroke.toPx()
    Box(modifier = modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val r = (w - strokePx) / 2f
            val c = Offset(w / 2f, w / 2f)
            // track
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (p > 0.001f) {
                // soft halo behind the head
                val headAngle = (-90f + 360f * p) * (kotlin.math.PI.toFloat() / 180f)
                val head = Offset(c.x + r * cos(headAngle), c.y + r * sin(headAngle))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(palette.primary.copy(alpha = 0.5f), Color.Transparent),
                        center = head,
                        radius = strokePx * 3f,
                    ),
                    radius = strokePx * 3f,
                    center = head,
                )
                // gradient arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(palette.secondary, palette.primary, palette.tertiary, palette.secondary),
                        center = c,
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                // bright head dot
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = strokePx * 0.62f,
                    center = head,
                )
            }
        }
        label?.invoke(this)
    }
}

/**
 * GradientLinearBar — thin gradient progress bar with glow.
 * When [progress] is null it runs an indeterminate aurora sweep.
 */
@Composable
fun GradientLinearBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val infinite = rememberInfiniteTransition(label = "bar")
    val sweep by infinite.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val barBrush = Brush.linearGradient(
        listOf(palette.secondary, palette.primary, palette.tertiary, palette.secondary),
    )
    Box(modifier = modifier.height(height)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(8.dp, CircleShape, ambientColor = palette.primary.copy(alpha = 0.45f), spotColor = Color.Transparent),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val h = size.height
                val w = size.width
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    cornerRadius = CornerRadius(h / 2f),
                )
                if (progress == null) {
                    val band = w * 0.4f
                    val x0 = (sweep) * (w + band) - band
                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(x0, 0f),
                        size = Size(band, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                } else {
                    val p = progress.coerceIn(0f, 1f)
                    if (p > 0f) {
                        drawRoundRect(
                            brush = barBrush,
                            size = Size(w * p, h),
                            cornerRadius = CornerRadius(h / 2f),
                        )
                    }
                }
            }
        }
    }
}
