package com.vidma.downloader.ui.components.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/**
 * SectionTitle — small eyebrow with a gradient tick, used above card groups.
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .requiredSize(width = 4.dp, height = 16.dp)
                .background(
                    Brush.verticalGradient(listOf(palette.secondary, palette.primary, palette.tertiary)),
                    RoundedCornerShape(4.dp),
                ),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextMid),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * StatusPill — capsule legend used next to download states.
 */
@Composable
fun StatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = dotColor, modifier = Modifier.requiredSize(12.dp))
        } else {
            GlowDot(color = dotColor, pulse = pulsing)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextHigh),
            maxLines = 1,
        )
    }
}

/**
 * EmptyState — layered glass cards + gradient icon for empty screens
 * (library / history). Premium "nothing here yet" moment.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.requiredSize(150.dp)) {
            // stacked glass deck behind the icon tile
            Box(
                modifier = Modifier
                    .requiredSize(120.dp)
                    .rotate(-7f)
                    .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(30.dp)),
            )
            Box(
                modifier = Modifier
                    .requiredSize(120.dp)
                    .rotate(6f)
                    .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(30.dp)),
            )
            Box(
                modifier = Modifier
                    .requiredSize(122.dp)
                    .align(Alignment.Center)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                palette.primary.copy(alpha = 0.5f),
                                palette.tertiary.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.08f),
                            )
                        ),
                        RoundedCornerShape(32.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                GlowDot(palette.secondary, modifier = Modifier.requiredSize(10.dp).align(Alignment.TopEnd).padding(end = 26.dp, top = 22.dp), pulse = true)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.requiredSize(46.dp),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(color = VidmaBase.TextHigh),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(color = VidmaBase.TextMid),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        action?.invoke()
    }
}

/**
 * IconTile — tinted glass square for media meta rows.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .requiredSize(size)
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.requiredSize(size * 0.46f),
        )
    }
}
