package com.vidma.downloader.features.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vidma.downloader.util.normalizeUrl

/**
 * Owns the single, process-lifetime [WebView] and all browser chrome state.
 * Holding the WebView in the ViewModel lets the native browser survive tab
 * switches (it is simply detached/reattached by Compose).
 */
class BrowserViewModel : ViewModel() {

    var addressText by mutableStateOf("")
    var currentUrl by mutableStateOf("")
        private set
    var pageTitle by mutableStateOf("")
        private set
    var progress by mutableStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set

    private var webView: WebView? = null
    private var pendingUrl: String? = null

    /** Called when the browser screen first composes. */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebView(context: Context): WebView {
        webView?.let { return it }
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.supportMultipleWindows()
            webViewClient = BrowserClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(webView: WebView?, newProgress: Int) {
                    this@BrowserViewModel.progress = newProgress
                    isLoading = newProgress < 100
                }
                override fun onReceivedTitle(webView: WebView?, title: String?) {
                    pageTitle = title?.take(120) ?: ""
                }
                override fun onReceivedIcon(webView: WebView?, icon: Bitmap?) = Unit
            }
        }
        webView = view
        // A navigation was requested before the view existed — fire it now.
        pendingUrl?.let {
            pendingUrl = null
            view.loadUrl(it)
            currentUrl = it
            addressText = it
        }
        return view
    }

    fun onAddressSubmit(raw: String) {
        val url = normalizeUrl(raw)
        if (url == null) {
            // not a URL → friendly web search
            val query = java.net.URLEncoder.encode(raw.trim(), "UTF-8")
            load("https://duckduckgo.com/?q=$query")
            return
        }
        addressText = url
        load(url)
    }

    fun load(url: String) {
        val wv = webView
        addressText = url
        if (wv != null) {
            wv.loadUrl(url)
        } else {
            pendingUrl = url
            currentUrl = url
        }
    }

    fun goBack() = webView?.takeIf { it.canGoBack() }?.goBack()
    fun goForward() = webView?.takeIf { it.canGoForward() }?.goForward()
    fun reload() = webView?.reload()
    fun stopLoading() = webView?.stopLoading()

    fun onPageStarted(url: String) {
        currentUrl = url
        addressText = url
        isLoading = true
    }

    fun onPageFinished(url: String) {
        isLoading = false
        webView?.let {
            canGoBack = it.canGoBack()
            canGoForward = it.canGoForward()
        }
        if (pageTitle.isBlank()) pageTitle = com.vidma.downloader.util.hostOf(url)
    }

    private inner class BrowserClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            url?.let { onPageStarted(it) }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val url = request?.url?.toString() ?: return false
            return handleNavigation(view?.context, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val target = url ?: return false
            return handleNavigation(view?.context, target)
        }

        private fun handleNavigation(context: Context?, url: String): Boolean {
            return when {
                url.startsWith("http://") || url.startsWith("https://") -> false // keep in-app
                url.startsWith("about:") -> false
                context == null -> false
                else -> {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: ActivityNotFoundException) {
                    }
                    true
                }
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            url?.let { onPageFinished(it) }
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?,
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
        }
    }

    override fun onCleared() {
        runCatching { webView?.destroy() }
        webView = null
        super.onCleared()
    }
}
