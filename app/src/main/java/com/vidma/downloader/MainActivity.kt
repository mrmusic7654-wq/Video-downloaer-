package com.vidma.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidma.downloader.features.browser.BrowserViewModel
import com.vidma.downloader.features.downloader.DownloaderViewModel
import com.vidma.downloader.ui.components.background.LucidBackdrop
import com.vidma.downloader.ui.navigation.MainScaffold
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaTheme
import com.vidma.downloader.util.normalizeUrl

class MainActivity : ComponentActivity() {

    private val downloaderVm: DownloaderViewModel by viewModels()
    private val browserVm: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            val accent by downloaderVm.accent.collectAsStateWithLifecycle()
            VidmaTheme(accent = accent) {
                val palette = LocalVidmaPalette.current
                LucidBackdrop(palette = palette) {
                    MainScaffold(
                        downloaderVm = downloaderVm,
                        browserVm = browserVm,
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Shared text / opened links land in the Downloader URL field. */
    private fun handleIntent(intent: Intent?) {
        val candidate = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        candidate?.let { raw ->
            downloaderVm.offerSharedUrl(normalizeUrl(raw))
        }
    }
}
