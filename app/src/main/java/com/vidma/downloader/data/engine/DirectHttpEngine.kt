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
        "mp4", "m4v", "webm", "mov", "mkv", "avi", "3gp", "ts",
        "mp3", "m4a", "aac", "opus", "ogg", "flac", "wav",
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
                val extension = extensionFrom(url, response.header("Content-Type"))
                val base = cleanBase(title).ifBlank { "vidma_direct" }
                val target = uniqueTarget(outputDir, base, extension)
                val partial = File(target.parentFile, "${target.name}.part")
                val total = body.contentLength()
                var read = 0L
                var lastUpdate = 0L
                var lastBytes = 0L
                var lastTime = System.currentTimeMillis()

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
                            if (now - lastUpdate >= 180L || (total > 0 && read == total)) {
                                val speed = if (now > lastTime) {
                                    (read - lastBytes).toDouble() / ((now - lastTime) / 1000.0)
                                } else 0.0
                                val eta = if (total > 0 && speed > 0.0) {
                                    ((total - read) / speed).toLong().coerceAtLeast(0L)
                                } else -1L
                                val percent = if (total > 0) read * 100f / total else -1f
                                onProgress(
                                    percent,
                                    eta,
                                    "Direct media · ${read / 1024} KB",
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
