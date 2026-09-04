package com.vidma.downloader.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.filled.Globe
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidma.downloader.domain.model.percentText
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/** Bottom navigation model. */
enum class VidmaTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Download", Icons.Rounded.Download),
    Library("library", "Library", Icons.Rounded.VideoLibrary),
    Browser("browser", "Browser", Icons.Filled.Globe),
}

/**
 * VidmaDock — floating glass pill with three tabs and an active-download
 * badge on the Download tab.
 */
@Composable
fun VidmaDock(
    selected: VidmaTab,
    onSelect: (VidmaTab) -> Unit,
    modifier: Modifier = Modifier,
    activeDownloads: Int = 0,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.7f), spotColor = palette.primary.copy(alpha = 0.25f))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF71A1E38), Color(0xF70E1128)),
                ),
                shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        palette.primary.copy(alpha = 0.55f),
                        VidmaBase.GlassStroke,
                        palette.secondary.copy(alpha = 0.4f),
                    ),
                ),
                shape = shape,
            )
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VidmaTab.entries.forEach { tab ->
            DockItem(
                tab = tab,
                selected = tab == selected,
                badge = if (tab == VidmaTab.Home) activeDownloads else 0,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
                palette = palette,
            )
        }
    }
}

@Composable
private fun DockItem(
    tab: VidmaTab,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        label = "dock",
    )
    val itemShape = RoundedCornerShape(20.dp)
    val bg by animateColorAsState(
        targetValue = if (selected) palette.primary.copy(alpha = 0.26f) else Color.Transparent,
        label = "dockBg",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) palette.secondary else VidmaBase.TextLow,
        label = "dockTint",
    )
    Column(
        modifier = modifier
            .clip(itemShape)
            .background(bg, itemShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .requiredSize(15.dp)
                        .align(Alignment.TopEnd)
                        .background(
                            Brush.linearGradient(listOf(palette.danger, palette.tertiary)),
                            CircleShape,
                        )
                        .border(1.4.dp, Color(0xFF0C0E22), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 9) "9+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 8.sp,
                        ),
                    )
                }
            }
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier
                    .graphicsLayer { scaleX = if (pressed) 0.85f else iconScale; scaleY = if (pressed) 0.85f else iconScale }
                    .requiredSize(23.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) VidmaBase.TextHigh else VidmaBase.TextLow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small floating panel that keeps the newest active download in sight. */
@Composable
fun ActiveDownloadPill(
    task: com.vidma.downloader.domain.model.DownloadTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        glowing = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.vidma.downloader.ui.components.core.GlowProgressArc(
                progress = task.progress,
                size = 36.dp,
                stroke = 3.dp,
                palette = palette,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloading…",
                    style = MaterialTheme.typography.labelMedium.copy(color = palette.secondary),
                )
                Text(
                    text = task.title ?: com.vidma.downloader.util.hostOf(task.url),
                    style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = task.percentText(),
                style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
            )
        }
    }
}
