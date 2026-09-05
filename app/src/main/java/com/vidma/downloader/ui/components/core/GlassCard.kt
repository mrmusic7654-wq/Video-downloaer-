package com.vidma.downloader.ui.components.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.ui.theme.glassBrush
import com.vidma.downloader.ui.theme.LocalVidmaPalette

/**
 * GlassCard — translucent frosted surface with a hairline gradient border and a
 * soft accent glow. The workhorse container of the vidma UI.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    palette: VidmaPalette = LocalVidmaPalette.current,
    elevation: Dp = 0.dp,
    glowing: Boolean = false,
    borderVisible: Boolean = true,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    } else Modifier
    val effectiveAlpha = if (enabled || onClick == null) 1f else 0.55f

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = effectiveAlpha }
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        ambientColor = if (glowing) palette.primary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f),
                        spotColor = if (glowing) palette.primary.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                    )
                } else Modifier
            )
            .clip(shape)
            .background(glassBrush(palette), shape)
            .then(
                if (borderVisible) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = 0.35f),
                                VidmaBase.GlassStroke,
                                palette.secondary.copy(alpha = 0.18f),
                            ),
                        ),
                        shape = shape,
                    )
                } else Modifier
            )
            .then(clickModifier)
            .padding(contentPadding),
    ) {
        content()
    }
}

/**
 * BrandChip — small gradient capsule used for the logo mark and tiny badges.
 */
@Composable
fun BrandChip(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    corner: Dp = 11.dp,
    palette: VidmaPalette = LocalVidmaPalette.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = palette.primary.copy(alpha = 0.6f),
                spotColor = palette.secondary.copy(alpha = 0.5f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary))
            )
            .then(Modifier.requiredSize(size)),
        content = content,
    )
}
