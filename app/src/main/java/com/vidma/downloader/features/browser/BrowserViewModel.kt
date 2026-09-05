package com.vidma.downloader.features.browser

import android.annotation.SuppressLint
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidma.downloader.VidmaApp
import com.vidma.downloader.domain.model.FabPosition
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.domain.model.PageMediaSource
import com.vidma.downloader.util.hostOf
import com.vidma.downloader.util.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Owns the single, process-lifetime WebView and all browser chrome state.
 * Android's WebView provider is Chromium-based, so this uses the device's
 * maintained Chromium runtime instead of embedding a second browser in the
 * APK. The browser and URL resolver both feed the same downloader queue.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as VidmaApp).container.prefs

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

    val fabPosition: StateFlow<FabPosition> = prefs.downloadFabPositionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, FabPosition())

    private var webView: WebView? = null
    private var pendingUrl: String? = null

    /** Called when the browser screen first composes. */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebView(context: Context): WebView {
        webView?.let { return it }
        val view = WebView(context).apply {
            // This is the system Chromium implementation, not an embedded
            // browser binary. Keep its network/cache lifecycle in WebView.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }
            webViewClient = BrowserClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(webView: WebView?, newProgress: Int) {
                    this@BrowserViewModel.progress = newProgress.coerceIn(0, 100)
                    isLoading = newProgress < 100
                    refreshNavigationState(webView)
                }

                override fun onReceivedTitle(webView: WebView?, title: String?) {
                    pageTitle = title?.take(120).orEmpty()
                }
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
        val input = raw.trim()
        val url = normalizeUrl(input)
        if (url == null) {
            if (input.isBlank()) return
            // Chromium is the browser engine. Use a conventional search page
            // for text input; the app does not ship a separate search/browser
            // runtime and stays on the device Chromium provider.
            val query = URLEncoder.encode(input, "UTF-8")
            load("https://www.google.com/search?q=$query")
            return
        }
        addressText = url
        load(url)
    }

    /**
     * Used by the home screen to open a URL or a search query in the
     * in-app browser. Accepts the same input the address bar would
     * accept: a normalised URL, a raw URL or a free-text query.
     */
    fun open(target: String) {
        onAddressSubmit(target)
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
    fun stopLoading() {
        webView?.stopLoading()
        isLoading = false
    }

    /**
     * Asks the loaded page which media elements it is playing.
     *
     * The returned URLs are what the user actually sees/hears on screen, so
     * "play a video → tap download" can grab the real file with plain HTTP
     * (works on any site, real progress) instead of hoping the extractor
     * knows the site. Blob/MSE streams are skipped — they cannot be fetched
     * outside the WebView, and the engine-resolve path covers those.
     */
    suspend fun capturePageMedia(): List<PageMediaSource> {
        val view = webView
        if (view == null || currentUrl.isBlank()) return emptyList()
        return try {
            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    view.evaluateJavascript(CAPTURE_JS) { raw ->
                        if (!cont.isCancelled) cont.resume(parseCaptureResult(raw))
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            // A JS/context hiccup must never break the download flow.
            emptyList()
        }
    }

    /**
     * WebView returns the JS string as a JSON-encoded string literal, i.e.
     * our JSON double-encoded: `"{"title":"…","sources":[…]}"`. Strip the
     * outer quoting and unescape.
     */
    private fun parseCaptureResult(raw: String?): List<PageMediaSource> {
        var json = raw.orEmpty()
        if (json.length >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
            json = json.substring(1, json.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\/", "/")
        }
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val title = root.optString("title").trim().take(160)
        val arr = root.optJSONArray("sources") ?: return emptyList()
        val result = mutableListOf<PageMediaSource>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("url").trim()
            if (url.length < 8) continue
            val kind = if (obj.optString("kind") == "audio") MediaKind.Audio else MediaKind.Video
            result += PageMediaSource(
                kind = kind,
                url = url,
                title = title.takeIf { it.isNotBlank() },
                poster = obj.optString("poster").takeIf { it.isNotBlank() },
            )
        }
        return result
    }

    fun saveFabPosition(position: FabPosition) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setDownloadFabPosition(position.clamped())
        }
    }

    fun resetFabPosition() = saveFabPosition(FabPosition())

    fun onPageStarted(url: String) {
        currentUrl = url
        addressText = url
        pageTitle = ""
        progress = 0
        isLoading = true
    }

    fun onPageFinished(url: String) {
        currentUrl = url
        addressText = url
        progress = 100
        isLoading = false
        refreshNavigationState(webView)
        if (pageTitle.isBlank()) pageTitle = hostOf(url)
    }

    private fun refreshNavigationState(view: WebView?) {
        val target = view ?: webView ?: return
        canGoBack = target.canGoBack()
        canGoForward = target.canGoForward()
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

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val target = url ?: return false
            return handleNavigation(view?.context, target)
        }

        private fun handleNavigation(context: Context?, url: String): Boolean {
            return when {
                url.startsWith("http://") || url.startsWith("https://") -> false
                url.startsWith("about:") -> false
                context == null -> false
                else -> {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: ActivityNotFoundException) {
                        // An unsupported intent should not break the page.
                    }
                    true
                }
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            url?.let { onPageFinished(it) }
        }
    }

    override fun onCleared() {
        runCatching {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
        }
        webView = null
        super.onCleared()
    }

    companion object {
        private const val CAPTURE_TIMEOUT_MS = 6_000L

        /**
         * Returns JSON: {title, sources:[{kind:"video"|"audio", url, poster}]}
         * - videos: only elements large enough to be the "main" player,
         * - audios: all of them,
         * - og:video fallback when the page exposes no player elements.
         */
        private const val CAPTURE_JS = """
(function(){
  try {
    var out = [];
    function push(kind, url, poster){
      if (!url) return;
      url = String(url);
      if (url.length < 8) return;
      if (url.indexOf('blob:') === 0) return;
      if (url.indexOf('http') !== 0) {
        try { url = new URL(url, location.href).href; } catch (e) { return; }
      }
      for (var i = 0; i < out.length; i++) { if (out[i].url === url) return; }
      out.push({kind: kind, url: url, poster: poster || ''});
    }
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try {
        var v = vids[i];
        var r = v.getBoundingClientRect();
        if (r.width < 160 || r.height < 90) continue;
        var src = v.currentSrc || v.src || '';
        if (!src) {
          var s = v.querySelector('source');
          src = s ? (s.currentSrc || s.src || '') : '';
        }
        if (src) push('video', src, v.poster || '');
      } catch (e) {}
    }
    var auds = document.querySelectorAll('audio');
    for (var j = 0; j < auds.length; j++) {
      try {
        var a = auds[j];
        push('audio', a.currentSrc || a.src || '', '');
      } catch (e) {}
    }
    if (out.length === 0) {
      var og = document.querySelector(
        'meta[property="og:video:secure_url"], meta[property="og:video:url"], meta[property="og:video"]'
      );
      if (og) push('video', og.content || '', '');
    }
    return JSON.stringify({title: document.title || '', sources: out});
  } catch (e) {
    return JSON.stringify({title: document.title || '', sources: []});
  }
})()
"""
    }
}
