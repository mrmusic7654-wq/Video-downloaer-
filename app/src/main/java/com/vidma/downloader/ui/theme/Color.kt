package com.vidma.downloader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VIDMA — Aurora "lucid fluid" design tokens.
 *
 * The app is dark-only by design: deep ink backgrounds + vivid aurora accents
 * (per [AccentPreset]) + translucent glass surfaces. Everything an accent can
 * tint flows from [AccentPreset], so switching presets re-skins the whole app.
 */

/** User-selectable accent themes. */
enum class AccentPreset(
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    Aurora(
        label = "Aurora",
        primary = Color(0xFF7C5CFF),   // electric violet
        secondary = Color(0xFF2FD8FF), // lucid cyan
        tertiary = Color(0xFFFF5CB8),  // orchid pink
    ),
    Ocean(
        label = "Deep Ocean",
        primary = Color(0xFF00B4FF),
        secondary = Color(0xFF3DF2A5),
        tertiary = Color(0xFF5C7CFF),
    ),
    Ember(
        label = "Ember",
        primary = Color(0xFFFF7A3D),
        secondary = Color(0xFFFFC24D),
        tertiary = Color(0xFFFF3D6E),
    ),
    Orchid(
        label = "Orchid",
        primary = Color(0xFFB45CFF),
        secondary = Color(0xFFFF5CB8),
        tertiary = Color(0xFF5CB8FF),
    ),
}

/** Fixed semantic / neutral palette (independent of the accent). */
object VidmaBase {
    // ink gradient — drawn top → bottom by LucidBackdrop
    val InkTop = Color(0xFF0C0F26)
    val InkMid = Color(0xFF080A1A)
    val InkBottom = Color(0xFF04050D)

    val TextHigh = Color(0xFFF5F7FF)
    val TextMid = Color(0xFFA6AECF)
    val TextLow = Color(0xFF5F688F)

    val Success = Color(0xFF3DF2A5)
    val Warning = Color(0xFFFFC24D)
    val Danger = Color(0xFFFF5D7A)

    val GlassFill = Color.White.copy(alpha = 0.055f)
    val GlassFillStrong = Color.White.copy(alpha = 0.09f)
    val GlassStroke = Color.White.copy(alpha = 0.13f)
    val GlassStrokeSoft = Color.White.copy(alpha = 0.07f)

    val White = Color.White
    val Black = Color.Black
    val Scrim = Color(0xCC03040A)
}

/** All colors the Material theme is built from. */
data class VidmaPalette(
    val accent: AccentPreset,
    val textHigh: Color = VidmaBase.TextHigh,
    val textMid: Color = VidmaBase.TextMid,
    val textLow: Color = VidmaBase.TextLow,
    val success: Color = VidmaBase.Success,
    val warning: Color = VidmaBase.Warning,
    val danger: Color = VidmaBase.Danger,
    val inkTop: Color = VidmaBase.InkTop,
    val inkMid: Color = VidmaBase.InkMid,
    val inkBottom: Color = VidmaBase.InkBottom,
    val glassStrokeSoft: Color = VidmaBase.GlassStrokeSoft,
) {
    val primary: Color get() = accent.primary
    val secondary: Color get() = accent.secondary
    val tertiary: Color get() = accent.tertiary

    /** Ordered gradient stops used by CTAs / brand text. */
    val brandGradient: List<Color>
        get() = listOf(accent.secondary, accent.primary, accent.tertiary)

    /** Aurora orb colors for the fluid backdrop. */
    val orbGradient: List<Color>
        get() = listOf(accent.primary, accent.secondary, accent.tertiary)
}
