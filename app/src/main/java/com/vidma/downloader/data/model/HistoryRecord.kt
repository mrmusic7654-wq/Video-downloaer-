package com.vidma.downloader.data.model

import kotlinx.serialization.Serializable

/**
 * Persisted, completed download record (library/history entry).
 * Stored as JSON inside DataStore.
 */
@Serializable
data class HistoryRecord(
    val id: String,
    val url: String,
    val title: String,
    val kind: String,          // MediaKind.name
    val ext: String,
    val filePath: String,      // absolute path (private) or content:// URI (public)
    val coverUri: String? = null,
    val sizeBytes: Long = 0L,
    val addedAtMs: Long = 0L,
    val durationSec: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val inPublicStorage: Boolean = true,
)
