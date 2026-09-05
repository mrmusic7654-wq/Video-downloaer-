package com.vidma.downloader.data.engine

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Small OkHttp fallback for already-direct media URLs.
 *
 * yt-dlp remains the primary extractor. This path is deliberately narrow: it
 * only handles a URL whose path is clearly a media file, never a webpage or a
 * protected stream. That gives the app a dependable alternative for CDN links
 * while avoiding a second extractor with another large native runtime.
 */
object DirectHttpEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val calls = ConcurrentHashMap<String, Call>()

    private val extensions = setOf(
        "mp4", "m4v", "webm", "mov", "mkv", "avi", "3gp", "3g2", "ts",
        "ogv", "flv", "wmv", "mpg", "mpeg", "f4v",
        "mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav",
    )

    fun canHandle(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase(Locale.US) ?: return false
        if (scheme !in setOf("http", "https")) return false
        val extension = parsed.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?: return false
        return extension in extensions
    }

    suspend fun download(
        url: String,
        outputDir: File,
        title: String?,
        processId: String,
        onProgress: (Float, Long, String) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        if (!canHandle(url)) return@withContext null
        outputDir.mkdirs()
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
            )
            .header("Accept", "video/*,audio/*,*/*;q=0.8")
            .build()
        val call = client.newCall(request)
        calls[processId] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                // Some servers answer "blocked / expired / login wall" with a
                // 200 + an HTML page. Never write that into the library as a
                // video file. (The path extension is already known to be a
                // media type — download() gated on canHandle() — so any HTML
                // body here is a block/login-wall page, never real media.)
                val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.US)
                if (contentType.contains("text/html")) {
                    return@withContext null
                }
                val extension = extensionFrom(url, response.header("Content-Type"))
                val base = cleanBase(title).ifBlank { "vidma_direct" }
                val target = uniqueTarget(outputDir, base, extension)
                val partial = File(target.parentFile, "${target.name}.part")
                val total = body.contentLength()
                var read = 0L
                var lastUpdate = 0L
                var lastBytes = 0L
                var lastTime = System.currentTimeMillis()
                var lastSpeed = 0L

                onProgress(0f, -1L, "Direct media fallback")
                body.byteStream().use { input ->
                    partial.outputStream().buffered(BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 120L || (total > 0 && read == total)) {
                                val speed = if (now > lastTime) {
                                    ((read - lastBytes).toDouble() / ((now - lastTime) / 1000.0)).toLong()
                                } else 0L
                                val eta = if (total > 0 && speed > 0L) {
                                    ((total - read) / speed).coerceAtLeast(0L)
                                } else -1L
                                val percent = if (total > 0) read * 100f / total else -1f
                                lastSpeed = speed
                                onProgress(
                                    percent,
                                    eta,
                                    formatLine("Direct media", read, total, speed, eta),
                                )
                                lastUpdate = now
                                lastBytes = read
                                lastTime = now
                            }
                        }
                    }
                }
                if (!partial.exists() || partial.length() <= 0L) {
                    partial.delete()
                    return@withContext null
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                onProgress(100f, 0L, "Direct media ready")
                target
            }
        } catch (_: IOException) {
            null
        } finally {
            calls.remove(processId)
        }
    }

    fun cancel(processId: String) {
        calls.remove(processId)?.cancel()
    }

    private fun formatLine(
        prefix: String,
        done: Long,
        total: Long,
        speed: Long,
        eta: Long,
    ): String {
        val speedText = if (speed > 0) humanBytes(speed) + "/s" else "—"
        val pct = if (total > 0) " · ${(done * 100 / total).coerceIn(0, 100)}%" else ""
        val etaText = if (eta >= 0) " · ETA ${humanDuration(eta)}" else ""
        return "$prefix · $speedText$pct$etaText"
    }

    private fun humanBytes(b: Long): String {
        if (b <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.lastIndex) {
            v /= 1024.0; i++
        }
        return if (i == 0) "$b B" else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    private fun humanDuration(sec: Long): String {
        val s = sec.toInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%d:%02d", m, ss)
    }

    private fun extensionFrom(url: String, contentType: String?): String {
        val pathExtension = Uri.parse(url).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            .orEmpty()
        if (pathExtension in extensions) return pathExtension
        return when (contentType?.substringBefore(';')?.lowercase(Locale.US)) {
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/flac" -> "flac"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "mp4"
        }
    }

    private fun cleanBase(title: String?): String = title.orEmpty()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_NAME_LENGTH)

    private fun uniqueTarget(dir: File, base: String, extension: String): File {
        var candidate = File(dir, "$base.$extension")
        var index = 1
        while (candidate.exists() || File(candidate.parentFile, "${candidate.name}.part").exists()) {
            candidate = File(dir, "$base (${index++}).$extension")
        }
        return candidate
    }

    private const val BUFFER_SIZE = 32 * 1024
    private const val MAX_NAME_LENGTH = 90
}
