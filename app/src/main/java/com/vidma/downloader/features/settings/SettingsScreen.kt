package com.vidma.downloader.features.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.core.AuroraRing
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.SectionTitle
import com.vidma.downloader.ui.components.core.StatusPill
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.theme.AccentPreset
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/** SETTINGS — accent, storage policy, engine & library hygiene. */
@Composable
fun SettingsScreen(
    vm: DownloaderViewModel,
    onBack: () -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val accent by vm.accent.collectAsStateWithLifecycle()
    val publicStorage by vm.publicStorage.collectAsStateWithLifecycle()
    val engineReady by vm.engineReady.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VidmaIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 42.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(color = VidmaBase.TextHigh),
                    )
                    Text(
                        text = "Make vidma yours",
                        style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                    )
                }
            }
        }

        // appearance
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(text = "Appearance")
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingIcon(Icons.Rounded.Palette, palette.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Aurora theme", style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh))
                                Text(
                                    "Every accent recolors the app + backdrop",
                                    style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            AccentPreset.entries.forEach { preset ->
                                AccentSwatch(
                                    preset = preset,
                                    selected = accent == preset,
                                    onClick = { vm.setAccent(preset) },
                                    palette = palette,
                                )
                            }
                        }
                    }
                }
            }
        }

        // downloads
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(text = "Downloads")
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.Storage, palette.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save to Downloads folder", style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh))
                            Text(
                                text = if (publicStorage) "Files appear in the system Downloads app" else "Files stay private inside vidma",
                                style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                            )
                        }
                        VidmaSwitch(checked = publicStorage, onCheckedChange = vm::setPublicStorage, palette = palette)
                    }
                }
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.FolderOpen, palette.warning)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Download engine", style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh))
                            Text(
                                text = if (engineReady) "yt-dlp + FFmpeg ready" else "First-run setup in progress…",
                                style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                            )
                        }
                        if (engineReady) {
                            StatusPill(text = "Ready", dotColor = palette.success)
                        } else {
                            AuroraRing(size = 22.dp, palette = palette)
                        }
                    }
                }
            }
        }

        // library
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(text = "Library")
                GlassCard(
                    contentPadding = PaddingValues(18.dp),
                    onClick = { if (library.isNotEmpty()) confirmClear = true },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.DeleteSweep, palette.danger)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear library", style = MaterialTheme.typography.titleSmall.copy(color = VidmaBase.TextHigh))
                            Text(
                                text = if (library.isEmpty()) "No saved media yet" else "${library.size} files will be deleted",
                                style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                            )
                        }
                    }
                }
            }
        }

        // about
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(text = "About")
                GlassCard(contentPadding = PaddingValues(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .requiredSize(64.dp)
                                .background(
                                    Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary)),
                                    RoundedCornerShape(20.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "v",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "vidma",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = VidmaBase.TextHigh,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 26.sp,
                            ),
                        )
                        Text(
                            text = "Version 1.0.0 · yt-dlp engine · FFmpeg",
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "vidma is an unofficial client. Media belongs to its creators — download only what you have the right to keep. Powered by the open-source yt-dlp project.",
                            style = MaterialTheme.typography.bodySmall.copy(color = VidmaBase.TextLow),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF151931),
            titleContentColor = VidmaBase.TextHigh,
            textContentColor = VidmaBase.TextMid,
            title = { Text("Clear entire library?") },
            text = { Text("All ${library.size} downloaded files will be deleted from this device.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.clearLibrary()
                    confirmClear = false
                }) { Text("Delete all", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) {
                    Text("Keep", color = palette.secondary)
                }
            },
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .requiredSize(40.dp)
            .background(tint.copy(alpha = 0.13f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.requiredSize(19.dp),
        )
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset,
    selected: Boolean,
    onClick: () -> Unit,
    palette: VidmaPalette,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .requiredSize(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(preset.secondary, preset.primary, preset.tertiary),
                    ),
                    CircleShape,
                )
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, Color.White, CircleShape)
                    } else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.label.substringBefore(" "),
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) VidmaBase.TextHigh else VidmaBase.TextLow,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun VidmaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
        label = "track",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 26.dp else 2.dp,
        animationSpec = tween(180),
        label = "knob",
    )
    Box(
        modifier = Modifier
            .requiredSize(width = 52.dp, height = 30.dp)
            .background(trackColor, CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = offset, vertical = 0.dp)
                .requiredSize(26.dp)
                .background(
                    if (checked) Color.White else VidmaBase.TextMid,
                    CircleShape,
                ),
        )
    }
}
