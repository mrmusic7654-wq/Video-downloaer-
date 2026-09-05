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
    accent: AccentPreset = AccentPreset.Platinum,
    content: @Composable () -> Unit,
) {
    val palette = VidmaPalette(accent = accent)
    // Dark, cool surfaces with a touch of the accent on the container steps
    // — the look is "premium minimalist" (deep ink, glassy hairline
    // borders) rather than the previous dreamy violet. The accent carries
    // all colour.
    val scheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color(0xFF0A0D14),
        primaryContainer = palette.primary.copy(alpha = 0.18f),
        onPrimaryContainer = Color(0xFFEDF1F9),
        secondary = palette.secondary,
        onSecondary = Color(0xFF0A0D14),
        secondaryContainer = palette.secondary.copy(alpha = 0.14f),
        onSecondaryContainer = Color(0xFFF3F6FB),
        tertiary = palette.tertiary,
        onTertiary = Color(0xFF0A0D14),
        tertiaryContainer = palette.tertiary.copy(alpha = 0.2f),
        onTertiaryContainer = Color(0xFFE7ECF3),
        background = palette.inkBottom,
        onBackground = palette.textHigh,
        surface = Color(0xFF0B0E16),
        onSurface = palette.textHigh,
        surfaceVariant = Color(0xFF14181F),
        onSurfaceVariant = palette.textMid,
        surfaceContainerHighest = Color(0xFF1A1E27),
        surfaceContainerHigh = Color(0xFF151921),
        surfaceContainer = Color(0xFF11151C),
        surfaceContainerLow = Color(0xFF0C0F16),
        surfaceContainerLowest = Color(0xFF06080E),
        outline = Color(0xFF3B4250),
        outlineVariant = palette.glassStrokeSoft,
        error = palette.danger,
        onError = Color.White,
        errorContainer = palette.danger.copy(alpha = 0.18f),
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
