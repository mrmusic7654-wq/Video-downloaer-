package com.vidma.downloader.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vidma.downloader.features.browser.BrowserScreen
import com.vidma.downloader.features.browser.BrowserViewModel
import com.vidma.downloader.features.downloader.DownloaderScreen
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.features.library.LibraryScreen
import com.vidma.downloader.features.settings.SettingsScreen
import com.vidma.downloader.ui.components.media.TaskQueueSheet
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase

/**
 * App shell: bottom dock + active-download tray + NavHost + global toast.
 */
@Composable
fun MainScaffold(
    downloaderVm: DownloaderViewModel,
    browserVm: BrowserViewModel,
) {
    val navController = rememberNavController()
    val palette = LocalVidmaPalette.current
    val tasks by downloaderVm.downloads.collectAsStateWithLifecycle()
    val active = remember(tasks) { tasks.filter { it.isActive } }
    var queueOpen by remember { mutableStateOf(false) }

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val selectedTab = VidmaTab.entries.firstOrNull { it.route == route }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = active.isNotEmpty() && !queueOpen,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f),
                ) {
                    Column {
                        ActiveDownloadPill(
                            task = active.first(),
                            onClick = { queueOpen = true },
                            palette = palette,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                VidmaDock(
                    selected = selectedTab ?: VidmaTab.Home,
                    onSelect = { tab ->
                        if (route == "settings") navController.popBackStack()
                        navController.navigateTo(tab)
                    },
                    activeDownloads = active.size,
                    palette = palette,
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
            NavHost(
                navController = navController,
                startDestination = VidmaTab.Home.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(VidmaTab.Home.route) {
                    DownloaderScreen(
                        vm = downloaderVm,
                        onOpenLibrary = { navController.navigateTo(VidmaTab.Library) },
                        onOpenBrowser = { navController.navigateTo(VidmaTab.Browser) },
                        onOpenSettings = {
                            navController.navigate("settings") { launchSingleTop = true }
                        },
                    )
                }
                composable(VidmaTab.Library.route) {
                    LibraryScreen(vm = downloaderVm)
                }
                composable(VidmaTab.Browser.route) {
                    BrowserScreen(browserVm = browserVm, downloaderVm = downloaderVm)
                }
                composable("settings") {
                    SettingsScreen(vm = downloaderVm, onBack = { navController.popBackStack() })
                }
            }

            // global toast overlay
            VidmaToast(viewModel = downloaderVm, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    if (queueOpen) {
        TaskQueueSheet(
            tasks = active,
            onDismiss = { queueOpen = false },
            onCancel = downloaderVm::cancelTask,
            onRetry = downloaderVm::retryTask,
            onDismissTask = { id ->
                downloaderVm.removeTask(id)
                if (active.size <= 1) queueOpen = false
            },
        )
    }
}

/** One-press tab navigation with state preservation. */
private fun NavController.navigateTo(tab: VidmaTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Transient glass toast shown for global confirmations/errors. */
@Composable
fun VidmaToast(
    viewModel: DownloaderViewModel,
    modifier: Modifier = Modifier,
) {
    val message by viewModel.transient.collectAsStateWithLifecycle()
    val alpha by animateFloatAsState(targetValue = if (message != null) 1f else 0f, label = "toast")
    val palette = LocalVidmaPalette.current

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.consumeTransient()
        }
    }

    if (message != null) {
        Box(
            modifier = modifier
                .statusBarsPadding()
                .padding(top = 10.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(50))
                .graphicsLayer { this.alpha = alpha },
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xF71A1E38),
                                Color(0xF70E1128),
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(color = VidmaBase.TextHigh),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
