package com.vidma.downloader.features.browser

import com.vidma.downloader.ui.components.core.VidmaIcons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidma.downloader.domain.model.FabPosition
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.core.GlassCard
import com.vidma.downloader.ui.components.core.GlassTextField
import com.vidma.downloader.ui.components.core.GradientLinearBar
import com.vidma.downloader.ui.components.core.VidmaIconButton
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette
import com.vidma.downloader.util.hostOf
import com.vidma.downloader.util.isWebPageUrl

private val QuickSites = listOf(
    "YouTube" to "https://youtube.com",
    "TikTok" to "https://www.tiktok.com",
    "Instagram" to "https://www.instagram.com",
    "Vimeo" to "https://vimeo.com",
    "X" to "https://x.com",
    "Facebook" to "https://www.facebook.com",
)

/** BROWSER — native WebView tab with a one-tap "download this page" flow. */
@Composable
fun BrowserScreen(
    browserVm: BrowserViewModel,
    downloaderVm: DownloaderViewModel,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val haptics = LocalHapticFeedback.current
    val progress = browserVm.progress
    val isLoading = browserVm.isLoading
    val currentUrl = browserVm.currentUrl
    val pageTitle = browserVm.pageTitle
    val addressText = browserVm.addressText
    val savedFabPosition by browserVm.fabPosition.collectAsStateWithLifecycle()

    // System back walks the WebView history before leaving the app.
    BackHandler(enabled = currentUrl.isNotBlank() && browserVm.canGoBack) {
        browserVm.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ================= browser toolbar =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VidmaIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = { browserVm.goBack() },
                enabled = browserVm.canGoBack,
                size = 40.dp,
                tint = if (browserVm.canGoBack) VidmaBase.TextHigh else VidmaBase.TextLow,
            )
            Spacer(Modifier.width(8.dp))
            VidmaIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Forward",
                onClick = { browserVm.goForward() },
                enabled = browserVm.canGoForward,
                size = 40.dp,
                tint = if (browserVm.canGoForward) VidmaBase.TextHigh else VidmaBase.TextLow,
            )
            Spacer(Modifier.width(10.dp))
            GlassTextField(
                value = addressText,
                onValueChange = { browserVm.addressText = it },
                modifier = Modifier.weight(1f),
                placeholder = "Search or paste a web address",
                leadingIcon = if (currentUrl.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.LocationOn,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { browserVm.onAddressSubmit(addressText) },
                ),
                trailing = {
                    VidmaIconButton(
                        icon = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Reload",
                        onClick = { if (isLoading) browserVm.stopLoading() else browserVm.reload() },
                        size = 34.dp,
                        palette = palette,
                    )
                },
            )
        }

        // page progress
        if (isLoading) {
            GradientLinearBar(
                progress = progress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                palette = palette,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ================= webview surface =================
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (currentUrl.isBlank()) {
                StartPage(
                    onOpen = { browserVm.onAddressSubmit(it) },
                    palette = palette,
                )
            } else {
                AndroidView(
                    factory = { ctx -> browserVm.obtainWebView(ctx) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // The action is intentionally draggable rather than fixed to a
            // corner. Long-press it, move it clear of a site's controls, and
            // release; the normalised position is persisted in DataStore.
            if (isWebPageUrl(currentUrl) && currentUrl.isNotBlank()) {
                val density = LocalDensity.current
                val fabSizePx = with(density) { 62.dp.toPx() }
                val maxX = (constraints.maxWidth.toFloat() - fabSizePx).coerceAtLeast(0f)
                val maxY = (constraints.maxHeight.toFloat() - fabSizePx).coerceAtLeast(0f)
                var fabOffset by remember(savedFabPosition, maxX, maxY) {
                    mutableStateOf(
                        IntOffset(
                            x = (maxX * savedFabPosition.xFraction).toInt(),
                            y = (maxY * savedFabPosition.yFraction).toInt(),
                        ),
                    )
                }

                DownloadPageFab(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        downloaderVm.startUrlDirect(
                            url = currentUrl,
                            kind = MediaKind.Video,
                            requestLabel = "Page video · mp4",
                            title = pageTitle.takeIf { it.isNotBlank() }?.let { "$it — ${hostOf(currentUrl)}" }
                                ?: hostOf(currentUrl),
                            cover = null,
                        )
                    },
                    modifier = Modifier
                        .offset { fabOffset }
                        .pointerInput(maxX, maxY) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    fabOffset = IntOffset(
                                        x = (fabOffset.x + amount.x.toInt()).coerceIn(0, maxX.toInt()),
                                        y = (fabOffset.y + amount.y.toInt()).coerceIn(0, maxY.toInt()),
                                    )
                                },
                                onDragEnd = {
                                    browserVm.saveFabPosition(
                                        FabPosition(
                                            xFraction = if (maxX == 0f) 0f else fabOffset.x / maxX,
                                            yFraction = if (maxY == 0f) 0f else fabOffset.y / maxY,
                                        ),
                                    )
                                },
                                onDragCancel = {
                                    browserVm.saveFabPosition(
                                        FabPosition(
                                            xFraction = if (maxX == 0f) 0f else fabOffset.x / maxX,
                                            yFraction = if (maxY == 0f) 0f else fabOffset.y / maxY,
                                        ),
                                    )
                                },
                            )
                        },
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun DownloadPageFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .shadow(22.dp, shape, ambientColor = palette.primary.copy(alpha = 0.7f), spotColor = palette.secondary.copy(alpha = 0.55f))
            .requiredSize(62.dp)
            .background(
                Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary)),
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VidmaIcons.Download,
            contentDescription = "Download video from this page. Long press and drag to move.",
            tint = Color.White,
            modifier = Modifier.requiredSize(27.dp),
        )
    }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit, palette: VidmaPalette = LocalVidmaPalette.current) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(92.dp)
                .background(
                    Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary)),
                    RoundedCornerShape(30.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.requiredSize(42.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Browse, then grab it",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = VidmaBase.TextHigh,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Chromium is built into Android’s system WebView. Open any site, then hold and drag the glowing action wherever it feels right before tapping to send the page to the same download queue.",
            style = MaterialTheme.typography.bodyMedium.copy(color = VidmaBase.TextMid),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickSites.take(3).forEach { (name, url) ->
                QuickChip(name, url, onOpen, palette)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickSites.drop(3).forEach { (name, url) ->
                QuickChip(name, url, onOpen, palette)
            }
        }
    }
}

@Composable
private fun QuickChip(name: String, url: String, onOpen: (String) -> Unit, palette: VidmaPalette) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
            .clickable { onOpen(url) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.secondary,
            modifier = Modifier.requiredSize(16.dp),
        )
    }
}
