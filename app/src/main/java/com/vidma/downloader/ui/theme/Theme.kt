package com.vidma.downloader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Current app palette, provided under the Material theme. */
val LocalVidmaPalette = staticCompositionLocalOf { VidmaPalette(accent = AccentPreset.Dream) }

/** Deep glass look used by every card / dock / sheet in the app. */
val VidmaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VidmaTheme(
    accent: AccentPreset = AccentPreset.Dream,
    content: @Composable () -> Unit,
) {
    val palette = VidmaPalette(accent = accent)
    // Purple-black surfaces: every container step lifts toward violet,
    // so layered glass reads as "dreamy depth" instead of grey.
    val scheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color(0xFF0B0620),
        primaryContainer = palette.primary.copy(alpha = 0.22f),
        onPrimaryContainer = Color(0xFFEDE9FF),
        secondary = palette.secondary,
        onSecondary = Color(0xFF191026),
        secondaryContainer = palette.secondary.copy(alpha = 0.18f),
        onSecondaryContainer = Color(0xFFF3EFFC),
        tertiary = palette.tertiary,
        onTertiary = Color(0xFF1B0A2E),
        tertiaryContainer = palette.tertiary.copy(alpha = 0.2f),
        onTertiaryContainer = Color(0xFFEFE7FF),
        background = palette.inkBottom,
        onBackground = palette.textHigh,
        surface = Color(0xFF0D0918),
        onSurface = palette.textHigh,
        surfaceVariant = Color(0xFF1A1430),
        onSurfaceVariant = palette.textMid,
        surfaceContainerHighest = Color(0xFF221A3E),
        surfaceContainerHigh = Color(0xFF1B1534),
        surfaceContainer = Color(0xFF151027),
        surfaceContainerLow = Color(0xFF100C1E),
        surfaceContainerLowest = Color(0xFF080611),
        outline = Color(0xFF443A6E),
        outlineVariant = palette.glassStrokeSoft,
        error = palette.danger,
        onError = Color.White,
        errorContainer = palette.danger.copy(alpha = 0.2f),
        onErrorContainer = Color(0xFFFFDCE3),
        scrim = VidmaBase.Scrim,
    )

    CompositionLocalProvider(LocalVidmaPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VidmaTypography,
            shapes = VidmaShapes,
            content = content,
        )
    }
}

/** Convenience: the horizontal brand gradient used by CTAs and brand text. */
fun brandBrush(palette: VidmaPalette): Brush =
    Brush.linearGradient(palette.brandGradient)

/** Convenience: a subtle vertical glass gradient used on cards. */
fun glassBrush(palette: VidmaPalette): Brush =
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.085f),
            Color.White.copy(alpha = 0.03f),
        )
    )
