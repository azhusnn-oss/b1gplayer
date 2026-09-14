package com.b1g.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.b1g.player.core.model.SourceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists saved accounts.
 *
 * Xtream credentials and playlist URLs are effectively passwords — a playlist URL
 * grants the same access the login does — so everything goes into
 * [EncryptedSharedPreferences] rather than plain preferences or a database.
 */
class SourceStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _sources = MutableStateFlow(read())
    val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    /** The source to open on launch, so a returning user skips the login screen. */
    var lastUsedId: String?
        get() = prefs.getString(KEY_LAST_USED, null)
        set(value) = prefs.edit().putString(KEY_LAST_USED, value).apply()

    fun lastUsed(): SourceConfig? = lastUsedId?.let { id -> _sources.value.firstOrNull { it.id == id } }

    /** Adds [config], replacing any existing entry with the same id. */
    fun save(config: SourceConfig) {
        val updated = _sources.value.filterNot { it.id == config.id } + config
        write(updated)
    }

    fun remove(id: String) {
        write(_sources.value.filterNot { it.id == id })
        if (lastUsedId == id) lastUsedId = null
    }

    private fun write(sources: List<SourceConfig>) {
        prefs.edit().putString(KEY_SOURCES, json.encodeToString(serializer, sources)).apply()
        _sources.value = sources
    }

    private fun read(): List<SourceConfig> {
        val raw = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        // A stored blob written by an older schema must not crash the launch path.
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        /** Named explicitly rather than reified, so the sealed hierarchy is used. */
        val serializer = ListSerializer(SourceConfig.serializer())

        const val FILE_NAME = "b1g_sources"
        const val KEY_SOURCES = "sources"
        const val KEY_LAST_USED = "last_used"
    }
}
