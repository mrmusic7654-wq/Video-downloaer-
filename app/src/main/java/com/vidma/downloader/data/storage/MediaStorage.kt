package com.vidma.downloader.data.storage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vidma.downloader.domain.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * All file placement logic. Downloads are first written by yt-dlp into an
 * app-private staging dir, then "published": public storage uses MediaStore's
 * Downloads collection (scoped-storage safe on every API level); private
 * storage keeps files in the app's own external files dir.
 */
class MediaStorage(private val context: Context) {

    /** Raw output dir for yt-dlp (always app-private; published afterwards). */
    fun stagingDir(): File =
        File(context.cacheDir, "vidma_staging").apply { mkdirs() }

    /** Private media shelf used when the user disables public storage. */
    fun privateMediaDir(): File =
        File(context.getExternalFilesDir(null), "Vidma").apply { mkdirs() }

    /** Local cover art cache (app-private, persistent). */
    fun coversDir(): File =
        File(context.filesDir, "covers").apply { mkdirs() }

    fun cleanStaging(): Boolean = stagingDir().deleteRecursively()

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(MAX_NAME_LENGTH).ifBlank { "vidma_media" }
    }

    /**
     * Publishes a finished staged media file into public Downloads storage
     * (preferred) or the private shelf. Returns the final location + size.
     */
    suspend fun publish(
        stagedMedia: File,
        kind: MediaKind,
        title: String,
        toPublic: Boolean,
    ): PublishResult = withContext(Dispatchers.IO) {
        if (!stagedMedia.exists()) {
            PublishResult.Failure("Output file disappeared before publishing")
        } else {
            publishBlocking(stagedMedia, kind, title, toPublic)
        }
    }

    private fun publishBlocking(
        stagedMedia: File,
        kind: MediaKind,
        title: String,
        toPublic: Boolean,
    ): PublishResult {
        val sizeBefore = stagedMedia.length()

        if (toPublic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = publishViaMediaStore(stagedMedia, kind, title)
            if (uri != null) {
                stagedMedia.delete()
                return PublishResult.Public(uri.toString(), sizeBefore)
            }
        } else if (toPublic && legacyCanWritePublic()) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Vidma",
            ).apply { mkdirs() }
            val dest = uniqueFile(dir, sanitizeFileName(title), stagedMedia.extension)
            runCatching {
                stagedMedia.copyTo(dest, overwrite = false)
                stagedMedia.delete()
                return PublishResult.LegacyPublic(dest.absolutePath, sizeBefore)
            }
        }
        // fallback: private shelf
        val dest = uniqueFile(
            privateMediaDir(),
            "vidma_" + System.currentTimeMillis().toString().takeLast(6),
            stagedMedia.extension,
        )
        runCatching {
            stagedMedia.copyTo(dest, overwrite = false)
            stagedMedia.delete()
            return PublishResult.Private(dest.absolutePath, sizeBefore)
        }.onFailure {
            return PublishResult.Failure("Could not store file: ${it.message}")
        }
        PublishResult.Failure("Could not store file")
    }

    private fun publishViaMediaStore(file: File, kind: MediaKind, title: String): Uri? =
        runCatching {
            val resolver = context.contentResolver
            val displayName = uniqueDisplayName(sanitizeFileName(title), file.extension)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(kind, file.extension))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vidma")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out, DEFAULT_BUFFER_SIZE) }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
            uri
        }.getOrNull()

    private fun legacyCanWritePublic(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        val cleanExt = ext.ifBlank { "mp4" }
        var candidate = File(dir, "$base.$cleanExt")
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i).$cleanExt")
            i++
        }
        return candidate
    }

    private fun uniqueDisplayName(base: String, ext: String): String {
        var name = "$base.${ext.ifBlank { "mp4" }}"
        var i = 1
        while (displayNameExists(name) && i <= 100) {
            name = "$base ($i).${ext.ifBlank { "mp4" }}"
            i++
        }
        return name
    }

    private fun displayNameExists(name: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(name, "%${Environment.DIRECTORY_DOWNLOADS}/Vidma/%")
        context.contentResolver.query(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            projection, selection, args, null,
        )?.use { return it.count > 0 } ?: return false
    }

    /** Move the cover yt-dlp wrote next to the media into the private cover cache. */
    fun takeCover(stagedMedia: File, taskId: String): String? {
        val cover = findStagedCover(stagedMedia) ?: return null
        val ext = cover.extension.ifBlank { "jpg" }
        val dest = File(coversDir(), "$taskId.$ext")
        runCatching {
            cover.copyTo(dest, overwrite = true)
            cover.delete()
        }
        return dest.absolutePath
    }

    private fun findStagedCover(media: File): File? {
        if (!media.exists() || media.parentFile == null) return null
        val base = media.name.substringBeforeLast('.')
        return media.parentFile.listFiles()
            ?.filter { it.isFile && it.absolutePath != media.absolutePath }
            ?.filter { it.name.startsWith("$base.") }
            ?.filter { it.extension.lowercase() in COVER_EXTS }
            ?.maxByOrNull { it.length() }
    }

    /** Delete a library item's media + cover from wherever it lives. */
    suspend fun deleteMedia(itemPath: String, coverUri: String?): Boolean =
        withContext(Dispatchers.IO) {
            val mediaDeleted = when {
                itemPath.startsWith("content://") -> {
                    runCatching {
                        context.contentResolver.delete(Uri.parse(itemPath), null, null) > 0
                    }.getOrDefault(false)
                }
                else -> File(itemPath).delete()
            }
            coverUri?.let { runCatching { File(it).delete() } }
            mediaDeleted
        }

    fun mimeFor(kind: MediaKind, ext: String): String {
        val lower = ext.lowercase()
        if (lower in VIDEO_EXTS) {
            return when (lower) {
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                else -> "video/mp4"
            }
        }
        return when (lower) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "opus", "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            else -> if (kind == MediaKind.Video) "video/mp4" else "audio/mpeg"
        }
    }

    /** Shareable/openable uri for an item plus its mime type. */
    fun contentUriFor(itemPath: String, kind: MediaKind, ext: String): Pair<Uri, String> =
        if (itemPath.startsWith("content://")) {
            Uri.parse(itemPath) to mimeFor(kind, ext)
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(itemPath))
            uri to mimeFor(kind, ext)
        }

    sealed interface PublishResult {
        data class Public(val uri: String, val sizeBytes: Long) : PublishResult
        data class LegacyPublic(val path: String, val sizeBytes: Long) : PublishResult
        data class Private(val path: String, val sizeBytes: Long) : PublishResult
        data class Failure(val reason: String) : PublishResult
    }

    companion object {
        private const val MAX_NAME_LENGTH = 90
        private val COVER_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp")
        private val VIDEO_EXTS = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "flv")
    }
}
