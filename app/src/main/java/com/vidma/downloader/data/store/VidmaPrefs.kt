package com.vidma.downloader.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vidma.downloader.data.model.HistoryRecord
import com.vidma.downloader.domain.model.MediaKind
import com.vidma.downloader.ui.theme.AccentPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.vidmaDataStore by preferencesDataStore(name = "vidma_prefs")

/**
 * Preferences + library persistence backed by DataStore.
 * Library history is one JSON array of [HistoryRecord].
 */
class VidmaPrefs(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val recordListSerializer = ListSerializer(HistoryRecord.serializer())

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    @Volatile
    var publicStorageNow: Boolean = true
        private set

    init {
        // warm caches used by non-suspend call sites
        scope.launch {
            publicStorageNow = publicStorageFlow.first()
        }
    }

    // ---- accent ----
    private val accentKey = intPreferencesKey("accent_preset")

    val accentFlow: Flow<AccentPreset> = context.vidmaDataStore.data.map { prefs ->
        val idx = prefs[accentKey] ?: AccentPreset.Aurora.ordinal
        AccentPreset.entries.getOrElse(idx) { AccentPreset.Aurora }
    }

    suspend fun setAccent(preset: AccentPreset) {
        context.vidmaDataStore.edit { it[accentKey] = preset.ordinal }
    }

    // ---- public vs private storage policy ----
    private val publicStorageKey = intPreferencesKey("public_storage") // 1 default

    val publicStorageFlow: Flow<Boolean> =
        context.vidmaDataStore.data.map { prefs -> (prefs[publicStorageKey] ?: 1) == 1 }

    suspend fun setPublicStorage(enabled: Boolean) {
        publicStorageNow = enabled
        context.vidmaDataStore.edit { it[publicStorageKey] = if (enabled) 1 else 0 }
    }

    // ---- library history ----
    private val libraryKey = stringPreferencesKey("library_records")

    val libraryFlow: Flow<List<HistoryRecord>> =
        context.vidmaDataStore.data.map { prefs ->
            val raw = prefs[libraryKey]
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString(recordListSerializer, raw) }
                .getOrDefault(emptyList())
        }

    suspend fun librarySnapshot(): List<HistoryRecord> = libraryFlow.first()

    suspend fun upsertLibrary(record: HistoryRecord) {
        context.vidmaDataStore.edit { prefs ->
            val current = decode(prefs[libraryKey])
            val next = (current.filterNot { it.id == record.id } + record)
                .sortedByDescending { it.addedAtMs }
                .take(MAX_LIBRARY_ENTRIES)
            prefs[libraryKey] = encode(next)
        }
    }

    suspend fun removeFromLibrary(id: String) {
        context.vidmaDataStore.edit { prefs ->
            val next = decode(prefs[libraryKey]).filterNot { it.id == id }
            prefs[libraryKey] = encode(next)
        }
    }

    suspend fun clearLibrary() {
        context.vidmaDataStore.edit { prefs -> prefs[libraryKey] = encode(emptyList()) }
    }

    private fun decode(raw: String?): List<HistoryRecord> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(recordListSerializer, raw) }
            .getOrDefault(emptyList())

    private fun encode(records: List<HistoryRecord>): String =
        json.encodeToString(recordListSerializer, records)

    companion object {
        private const val MAX_LIBRARY_ENTRIES = 240

        fun kindOf(record: HistoryRecord): MediaKind =
            runCatching { MediaKind.valueOf(record.kind) }.getOrDefault(MediaKind.Video)
    }
}
