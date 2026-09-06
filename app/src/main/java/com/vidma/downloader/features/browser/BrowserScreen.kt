package com.vidma.downloader.features.browser

import com.vidma.downloader.ui.components.core.VidmaIcons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.vidma.downloader.domain.model.CaptureRequest
import com.vidma.downloader.domain.model.FabPosition
import com.vidma.downloader.domain.model.MediaSummary
import com.vidma.downloader.domain.model.PageMediaSource
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.core.AuroraRing
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

/** BROWSER — full-screen in-app WebView opened from the home search bar. */
@Composable
fun BrowserScreen(
    browserVm: BrowserViewModel,
    downloaderVm: DownloaderViewModel,
    onClose: (() -> Unit)? = null,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val progress = browserVm.progress
    val isLoading = browserVm.isLoading
    val currentUrl = browserVm.currentUrl
    val pageTitle = browserVm.pageTitle
    val addressText = browserVm.addressText
    val savedFabPosition by browserVm.fabPosition.collectAsStateWithLifecycle()
    val parseState = browserVm.pageParseState
    val engineReady by downloaderVm.engineReady.collectAsStateWithLifecycle()
    val ffmpegReady by downloaderVm.ffmpegReady.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    /** Non-null while the "Save this media" sheet is up. */
    var captureSpec by remember { mutableStateOf<CaptureSheetSpec?>(null) }
    /** True only while we wait for the success tick animation to play
     *  before the save sheet slides up. */
    var openingSheet by remember { mutableStateOf(false) }

    // Parse-first flow: the FAB tap kicks the engine; once it finishes we
    // let the success tick play a beat (so the "done" moment is visible)
    // and then slide up the save sheet with thumbnail / title / files.
    // A failed resolve still opens the sheet (fallback: direct file).
    LaunchedEffect(parseState) {
        if (!openingSheet) return@LaunchedEffect
        when (val state = parseState) {
            is PageParseState.Ready -> {
                kotlinx.coroutines.delay(650)
                val sources = browserVm.capturePageMedia()
                captureSpec = CaptureSheetSpec(
                    request = buildCaptureRequest(currentUrl, pageTitle, sources, state.summary),
                    sources = sources,
                    summary = state.summary,
                )
                openingSheet = false
            }
            is PageParseState.Error -> {
                kotlinx.coroutines.delay(550)
                val sources = browserVm.capturePageMedia()
                captureSpec = CaptureSheetSpec(
                    request = buildCaptureRequest(currentUrl, pageTitle, sources, null),
                    sources = sources,
                    summary = null,
                )
                openingSheet = false
            }
            else -> Unit
        }
    }

    // System back walks the WebView history; once the history runs out,
    // the next press closes the browser.
    BackHandler {
        when {
            captureSpec != null -> {
                captureSpec = null
                browserVm.clearPageParseState()
            }
            browserVm.canGoBack -> browserVm.goBack()
            onClose != null -> onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    if (isLoading) {
                        VidmaIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Stop",
                            onClick = { browserVm.stopLoading() },
                            size = 34.dp,
                            palette = palette,
                        )
                    } else {
                        VidmaIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Reload",
                            onClick = { browserVm.reload() },
                            size = 34.dp,
                            palette = palette,
                        )
                        // Paste straight into the address bar when it's empty.
                        if (addressText.isBlank()) {
                            Spacer(Modifier.width(6.dp))
                            VidmaIconButton(
                                icon = VidmaIcons.ContentPaste,
                                contentDescription = "Paste link",
                                onClick = {
                                    val clip = clipboard.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        browserVm.onAddressSubmit(clip.trim())
                                    } else {
                                        downloaderVm.showMessage("Clipboard is empty")
                                    }
                                },
                                size = 34.dp,
                                palette = palette,
                            )
                        }
                    }
                },
            )
            if (onClose != null) {
                Spacer(Modifier.width(8.dp))
                VidmaIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close browser",
                    onClick = onClose,
                    size = 40.dp,
                )
            }
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
                    state = parseState,
                    openingSheet = openingSheet,
                    onClick = onClick@{
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (captureSpec != null) return@onClick
                        when (parseState) {
                            // Spin the engine on the page: the FAB animates
                            // while parsing, then the sheet opens with the
                            // video's thumbnail, title and every file.
                            is PageParseState.Idle, is PageParseState.Error -> {
                                openingSheet = true
                                browserVm.resolveCurrentPage()
                            }
                            // Already parsed: open the sheet immediately.
                            is PageParseState.Ready -> {
                                openingSheet = true
                                scope.launch {
                                    openCaptureSheet(browserVm, currentUrl, pageTitle) { spec ->
                                        captureSpec = spec
                                    }
                                }
                            }
                            // Mid-parse: ignore (the sheet opens on its own).
                            is PageParseState.Parsing -> Unit
                        }
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

        // ================= "Save this media" sheet =================
        val spec = captureSpec
        if (spec != null) {
            CaptureSheet(
                request = spec.request,
                sources = spec.sources,
                engineReady = engineReady,
                ffmpegReady = ffmpegReady,
                summary = spec.summary,
                onDismiss = {
                    captureSpec = null
                    browserVm.clearPageParseState()
                },
                onDownload = { useDirect, kind, quality, container, audioFormat ->
                    captureSpec = null
                    browserVm.clearPageParseState()
                    downloaderVm.startCapture(
                        request = spec.request,
                        useDirect = useDirect,
                        kind = kind,
                        quality = quality,
                        container = container,
                        audioFormat = audioFormat,
                    )
                },
                onDownloadBest = { resolved, kind, quality, container, audioFormat ->
                    captureSpec = null
                    browserVm.clearPageParseState()
                    downloaderVm.startBrowserCapture(
                        summary = resolved,
                        request = spec.request,
                        kind = kind,
                        format = null,
                        quality = quality,
                        container = container,
                        audioFormat = audioFormat,
                    )
                },
                onDownloadFormat = { resolved, format ->
                    captureSpec = null
                    browserVm.clearPageParseState()
                    downloaderVm.startBrowserCapture(
                        summary = resolved,
                        request = spec.request,
                        kind = format.kind,
                        format = format,
                        quality = com.vidma.downloader.domain.model.QualityPreset.Auto,
                        container = com.vidma.downloader.domain.model.ContainerPref.Mp4,
                        audioFormat = com.vidma.downloader.domain.model.AudioFormatPref.Mp3,
                    )
                },
                palette = palette,
            )
        }
    }
}

/** State of the open capture sheet (request + what the page was playing). */
private data class CaptureSheetSpec(
    val request: CaptureRequest,
    val sources: List<PageMediaSource>,
    val summary: MediaSummary? = null,
)

/**
 * Asks the page what it is actually playing, then builds the hand-off
 * request. [resolved] — when present — fills in a real title/thumbnail the
 * engine already extracted.
 */
private suspend fun openCaptureSheet(
    browserVm: BrowserViewModel,
    pageUrl: String,
    pageTitle: String,
    emit: (CaptureSheetSpec) -> Unit,
) {
    val sources = browserVm.capturePageMedia()
    val state = browserVm.pageParseState as? PageParseState.Ready
    emit(
        CaptureSheetSpec(
            request = buildCaptureRequest(pageUrl, pageTitle, sources, state?.summary),
            sources = sources,
            summary = state?.summary,
        ),
    )
}

private fun buildCaptureRequest(
    pageUrl: String,
    pageTitle: String,
    sources: List<PageMediaSource>,
    resolved: MediaSummary?,
): CaptureRequest = CaptureRequest(
    pageUrl = pageUrl,
    manifestUrl = sources.firstOrNull { it.isManifest }?.url,
    directUrl = sources.firstOrNull { it.isDirectFile }?.url,
    title = resolved?.title?.takeIf { it.isNotBlank() }
        ?: pageTitle.takeIf { it.isNotBlank() }
            ?.let { "$it — ${hostOf(pageUrl)}" }
        ?: hostOf(pageUrl),
    cover = resolved?.thumbnailUrl
        ?: sources.firstOrNull { !it.poster.isNullOrBlank() }?.poster,
)

/**
 * Floating page-download action — parse-first.
 *
 * Tapping it does NOT download straight away: the FAB itself becomes the
 * progress indicator. It spins a ring while the engine parses the page,
 * plays a pop-in success tick when the thumbnail/title/files are ready
 * (after which the save sheet slides up), and turns into a retry warning
 * if the engine could not read the page. Long-press + drag moves it.
 */
@Composable
private fun DownloadPageFab(
    state: PageParseState,
    openingSheet: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = CircleShape
    val parsing = state is PageParseState.Parsing || (openingSheet && state is PageParseState.Idle)
    val ready = state is PageParseState.Ready
    val failed = state is PageParseState.Error

    val label = when {
        parsing -> "Reading the page…"
        ready -> "Ready — tap to see files"
        failed -> "Couldn't read page — tap for options"
        else -> null
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Status bubble that appears above the FAB during/after parsing.
        AnimatedVisibility(
            visible = label != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 230.dp)
                    .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = palette.primary.copy(alpha = 0.4f))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xF2171B36), Color(0xF70B0D20))),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(color = VidmaBase.TextHigh),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .shadow(22.dp, shape, ambientColor = palette.primary.copy(alpha = 0.7f), spotColor = palette.secondary.copy(alpha = 0.55f))
                .requiredSize(62.dp)
                .background(
                    when {
                        failed -> Brush.linearGradient(listOf(palette.danger, palette.danger.copy(alpha = 0.75f)))
                        else -> Brush.linearGradient(listOf(palette.secondary, palette.primary, palette.tertiary))
                    },
                    shape,
                )
                .clickable(enabled = !parsing, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = state, animationSpec = androidx.compose.animation.core.tween(280), label = "fab-icon") { current ->
                when (current) {
                    is PageParseState.Parsing ->
                        AuroraRing(size = 30.dp, stroke = 3.dp, invert = true, palette = palette)
                    is PageParseState.Ready ->
                        FabSuccessTick(palette = palette)
                    is PageParseState.Error ->
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Parsing failed. Tap to retry or download the direct file.",
                            tint = Color.White,
                            modifier = Modifier.requiredSize(27.dp),
                        )
                    is PageParseState.Idle ->
                        Icon(
                            imageVector = VidmaIcons.Download,
                            contentDescription = "Parse this page for downloads. Long press and drag to move.",
                            tint = Color.White,
                            modifier = Modifier.requiredSize(27.dp),
                        )
                }
            }
        }
    }
}

/** Success check that pops in with a bouncy spring + slight rotation. */
@Composable
private fun FabSuccessTick(palette: VidmaPalette) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.2f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        )
    }
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = "Page parsed. Tap to choose a file.",
        tint = Color.White,
        modifier = Modifier
            .requiredSize(34.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = -22f * (1f - scale.value)
            },
    )
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
        StartPagePasteHint(onOpen = onOpen, palette = palette)
    }
}

/** One-tap chip that opens whatever link is currently on the clipboard. */
@Composable
private fun StartPagePasteHint(onOpen: (String) -> Unit, palette: VidmaPalette) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val clip = clipboard.getText()?.text?.trim().orEmpty()
    val looksLink = clip.startsWith("http://") || clip.startsWith("https://") ||
        (!clip.contains(" ") && clip.contains(".") && clip.length > 6)
    if (!looksLink) return
    val host = hostOf(clip)
    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier
            .shadow(14.dp, RoundedCornerShape(50), spotColor = palette.secondary.copy(alpha = 0.35f))
            .background(
                Brush.linearGradient(listOf(palette.secondary.copy(alpha = 0.22f), palette.primary.copy(alpha = 0.18f))),
                RoundedCornerShape(50),
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpen(clip)
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = VidmaIcons.ContentPaste,
            contentDescription = null,
            tint = palette.secondary,
            modifier = Modifier.requiredSize(17.dp),
        )
        Text(
            text = "Open ${host.ifBlank { "copied link" }}",
            style = MaterialTheme.typography.labelLarge.copy(color = VidmaBase.TextHigh),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
