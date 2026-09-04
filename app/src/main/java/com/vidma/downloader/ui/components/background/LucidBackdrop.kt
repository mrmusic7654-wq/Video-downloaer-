package com.vidma.downloader.ui.components.background

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vidma.downloader.ui.theme.AccentPreset
import com.vidma.downloader.ui.theme.VidmaPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * LUCID FLUID BACKDROP — a fully procedural, animated "liquid aurora" layer.
 *
 * Rendered with pure Compose graphics (no bitmaps → any density, any size):
 *  1. deep ink vertical gradient,
 *  2. breathing aurora orbs (radial gradients that slowly drift & pulse),
 *  3. a fine animated starfield with twinkle,
 *  4. subtle flowing light streaks,
 *  5. vignette for cinematic depth.
 *
 * Everything is driven by one infinite transition so the whole scene breathes
 * in sync. Screens put their content inside [content].
 */
@Composable
fun LucidBackdrop(
    modifier: Modifier = Modifier,
    palette: VidmaPalette,
    content: @Composable BoxScope.() -> Unit,
) {
    // px-per-dp scale (e.g. 2.75 on a 440 dpi screen) — used to convert the
    // orb/star dp sizes into canvas pixels.
    val density = LocalDensity.current.density
    Box(modifier = modifier.fillMaxSize()) {
        val transition = rememberInfiniteTransition(label = "lucid")
        val breath by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        )
        val driftA by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 19_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ), label = "driftA",
        )
        val driftB by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ), label = "driftB",
        )
        // phase offset for streak flow
        val flow by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ), label = "flow",
        )

        val orbs = remember(palette.accent) {
            buildOrbSpecs(palette)
        }
        val stars = remember {
            List(90) { i ->
                StarSpec(
                    x = Random(i * 7 + 1).nextFloat(),
                    y = Random(i * 13 + 5).nextFloat(),
                    r = 0.35f + Random(i * 31 + 3).nextFloat() * 1.15f,
                    phase = Random(i * 53 + 9).nextFloat() * (2f * PI).toFloat(),
                    speed = 0.8f + Random(i * 17 + 2).nextFloat() * 1.6f,
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = breath * (2f * PI).toFloat()

            // 1 — ink gradient (drawn slightly oversized so orbs never clip)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.inkTop, palette.inkMid, palette.inkBottom),
                ),
            )

            // 2 — aurora orbs (soft radial blooms with breathing alpha)
            orbs.forEach { orb ->
                val px = orb.cx + (driftA - 0.5f) * w * orb.driftRange
                val py = orb.cy + (driftB - 0.5f) * h * orb.driftRange
                val pulse = 0.82f + 0.18f * sin(t * orb.pulseFreq + orb.phase)
                val radius = orb.radiusDp * density * pulse
                val alpha = orb.alpha * (0.86f + 0.14f * sin(t * orb.pulseFreq * 0.9f + orb.phase + 1.2f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orb.color.copy(alpha = alpha), orb.color.copy(alpha = 0f)),
                        center = Offset(px, py),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(px, py),
                )
            }

            // 3 — starfield twinkle
            stars.forEach { s ->
                val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * s.speed + s.phase))
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f * tw),
                    radius = s.r * density,
                    center = Offset(s.x * w, s.y * h),
                )
            }

            // 4 — flowing light streaks (very subtle "fluid" ribbons)
            drawStreaks(w, h, density, flow, palette)

            // 5 — vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x80020512)),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = w * 0.78f,
                ),
            )
        }
        content()
    }
}

private fun DrawScope.drawStreaks(
    w: Float,
    h: Float,
    density: Float,
    flow: Float,
    palette: VidmaPalette,
) {
    val colors = palette.orbGradient
    val yBase = h * 0.30f
    // two diagonal gossamer ribbons drifting horizontally
    repeat(2) { i ->
        val yOff = yBase + i * h * 0.22f
        val phase = flow * (2f * PI).toFloat() + i * 2.4f
        val start = Offset(-0.2f * w + ((phase * 0.5f + 0.5f) % 1f) * w * 0.4f, yOff)
        val end = Offset(start.x + w * 0.72f, yOff + h * 0.045f * (if (i == 0) 1 else -1))
        val stroke = 26f * density
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    colors[i % colors.size].copy(alpha = 0f),
                    colors[(i + 1) % colors.size].copy(alpha = 0.10f),
                    colors[i % colors.size].copy(alpha = 0f),
                ),
                start = start,
                end = end,
            ),
            start = start,
            end = end,
            strokeWidth = stroke,
        )
    }
}

private data class OrbSpec(
    val cx: Float,   // fraction of width
    val cy: Float,   // fraction of height
    val radiusDp: Float,
    val color: Color,
    val alpha: Float,
    val driftRange: Float,
    val pulseFreq: Float,
    val phase: Float,
)

private data class StarSpec(
    val x: Float,
    val y: Float,
    val r: Float,
    val phase: Float,
    val speed: Float,
)

private fun buildOrbSpecs(palette: VidmaPalette): List<OrbSpec> {
    val (a, b, c) = palette.orbGradient
    return listOf(
        OrbSpec(0.16f, 0.06f, 340f, a, 0.42f, 0.10f, 1.0f, 0.4f),
        OrbSpec(0.88f, 0.18f, 300f, b, 0.34f, 0.14f, 0.8f, 2.1f),
        OrbSpec(0.74f, 0.72f, 380f, c, 0.26f, 0.12f, 0.7f, 4.2f),
        OrbSpec(0.10f, 0.85f, 260f, a.copy(alpha = 1f), 0.20f, 0.16f, 1.2f, 1.1f),
        OrbSpec(0.5f, 0.38f, 190f, b.copy(alpha = 1f), 0.14f, 0.20f, 0.9f, 5.3f),
    )
}

/** Tiny helper to run a backdrop used by previews/tests without theme access. */
val PreviewPalette = VidmaPalette(accent = AccentPreset.Aurora)
