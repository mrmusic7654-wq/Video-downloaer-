package com.vidma.downloader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VIDMA — "Dream Violet" design tokens.
 *
 * The app is dark-only by design: premium near-black with a violet cast,
 * vivid purple accents, misty lavender (white-violet) highlights and
 * glassy surfaces. Everything an accent can tint flows from [AccentPreset],
 * so switching presets re-skins the whole app.
 */

/** User-selectable accent themes. */
enum class AccentPreset(
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    /**
     * Default — premium near-black ink with a shiny silver accent. The
     * secondary is a soft platinum white, the tertiary is a deeper gunmetal
     * for the bottom of the brand gradient. Minimalist, professional, and
     * easy on the eyes at night.
     */
    Platinum(
        label = "Midnight Silver",
        primary = Color(0xFFB7C2D0),     // shiny silver
        secondary = Color(0xFFEAF1FB),   // platinum white
        tertiary = Color(0xFF5B6577),    // gunmetal
    ),
    /** Premium black · purple · white, dreamy glow. */
    Dream(
        label = "Dream Violet",
        primary = Color(0xFF8B5CF6),   // vivid violet
        secondary = Color(0xFFC9BCFF), // lavender mist (white-violet)
        tertiary = Color(0xFF5B21B6),  // deep royal violet
    ),
    Noir(
        label = "Noir",
        primary = Color(0xFFA78BFA),   // soft violet
        secondary = Color(0xFFF3EFFC), // near-white
        tertiary = Color(0xFF4C1D95),  // ink violet
    ),
    Lavender(
        label = "Lavender Haze",
        primary = Color(0xFFC4B5FD),
        secondary = Color(0xFFEDE9FE),
        tertiary = Color(0xFFA855F7),
    ),
    Aurora(
        label = "Aurora",
        primary = Color(0xFF7C5CFF),   // electric violet
        secondary = Color(0xFF2FD8FF), // lucid cyan
        tertiary = Color(0xFFFF5CB8),  // orchid pink
    ),
}

/** Fixed semantic / neutral palette (independent of the accent). */
object VidmaBase {
    // ink gradient — drawn top → bottom by LucidBackdrop.
    // Cool ink (slight blue cast) to feel premium + minimal. Everything
    // that uses this is the *surface* — the accent provides the colour.
    val InkTop = Color(0xFF0A0D14)
    val InkMid = Color(0xFF06080E)
    val InkBottom = Color(0xFF020307)

    val TextHigh = Color(0xFFF1F4FA) // soft white
    val TextMid = Color(0xFFA2AABE)
    val TextLow = Color(0xFF6A7285)

    val Success = Color(0xFF65E0B6)
    val Warning = Color(0xFFFFC24D)
    val Danger = Color(0xFFFF6E8A)

    val GlassFill = Color.White.copy(alpha = 0.055f)
    val GlassFillStrong = Color.White.copy(alpha = 0.09f)
    val GlassStroke = Color.White.copy(alpha = 0.13f)
    val GlassStrokeSoft = Color.White.copy(alpha = 0.07f)

    val White = Color.White
    val Black = Color.Black
    val Scrim = Color(0xCC020307)
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
