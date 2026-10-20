package com.entretv.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "entre_settings")

/** Lo que el usuario eligió en el reproductor; se aplica al siguiente contenido. */
data class PlayerPrefs(
    /** Código ISO ("es", "en"…); vacío = idioma del equipo. */
    val audioLanguage: String = "",
    /** Código ISO, [SUBTITLES_OFF] o vacío = solo los forzados. */
    val subtitleLanguage: String = "",
    val subtitleSize: Int = SettingsRepository.SUBTITLE_NORMAL,
    val resizeMode: Int = SettingsRepository.RESIZE_FIT
)

/** Cómo se muestra el catálogo. */
data class CatalogPrefs(
    /** Nombre de [com.entretv.app.ui.SortOrder]. */
    val defaultSort: String = SettingsRepository.DEFAULT_SORT,
    val cleanNames: Boolean = true
)

const val SUBTITLES_OFF = "off"

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val BUFFER_OPTIONS = listOf(0, 3, 5, 9)
        const val DEFAULT_BUFFER_SECONDS = 3

        val MAX_BUFFER_OPTIONS = listOf(15, 30, 60)
        const val DEFAULT_MAX_BUFFER_SECONDS = 30

        const val DEFAULT_SORT = "ORIGINAL"

        const val SUBTITLE_SMALL = 0
        const val SUBTITLE_NORMAL = 1
        const val SUBTITLE_LARGE = 2

        const val RESIZE_FIT = 0
        const val RESIZE_ZOOM = 1
        const val RESIZE_FILL = 2

        private val KEY_BUFFER_SECONDS = intPreferencesKey("start_buffer_seconds")
        private val KEY_MAX_BUFFER_SECONDS = intPreferencesKey("max_buffer_seconds")
        private val KEY_AUDIO_LANGUAGE = stringPreferencesKey("audio_language")
        private val KEY_SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        private val KEY_SUBTITLE_SIZE = intPreferencesKey("subtitle_size")
        private val KEY_RESIZE_MODE = intPreferencesKey("resize_mode")
        private val KEY_DEFAULT_SORT = stringPreferencesKey("default_sort")
        private val KEY_CLEAN_NAMES = booleanPreferencesKey("clean_names")
        private val KEY_PIN_HASH = stringPreferencesKey("parental_pin_hash")
        private val KEY_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
    }

    /** Las descargas esperan a una red sin límite de datos (Wi‑Fi o cable). */
    val downloadWifiOnly: Flow<Boolean> = context.settingsStore.data.map { it[KEY_DOWNLOAD_WIFI_ONLY] ?: true }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_DOWNLOAD_WIFI_ONLY] = enabled }
    }

    /** Segundos de video que se juntan antes de empezar (y tras un trabón). */
    val bufferSeconds: Flow<Int> = context.settingsStore.data.map {
        it[KEY_BUFFER_SECONDS] ?: DEFAULT_BUFFER_SECONDS
    }

    /** Hasta cuántos segundos por delante descarga el reproductor. */
    val maxBufferSeconds: Flow<Int> = context.settingsStore.data.map {
        it[KEY_MAX_BUFFER_SECONDS] ?: DEFAULT_MAX_BUFFER_SECONDS
    }

    val playerPrefs: Flow<PlayerPrefs> = context.settingsStore.data.map {
        PlayerPrefs(
            audioLanguage = it[KEY_AUDIO_LANGUAGE].orEmpty(),
            subtitleLanguage = it[KEY_SUBTITLE_LANGUAGE].orEmpty(),
            subtitleSize = it[KEY_SUBTITLE_SIZE] ?: SUBTITLE_NORMAL,
            resizeMode = it[KEY_RESIZE_MODE] ?: RESIZE_FIT
        )
    }

    val catalogPrefs: Flow<CatalogPrefs> = context.settingsStore.data.map {
        CatalogPrefs(
            defaultSort = it[KEY_DEFAULT_SORT] ?: DEFAULT_SORT,
            cleanNames = it[KEY_CLEAN_NAMES] ?: true
        )
    }

    /** SHA-256 del PIN parental; vacío = sin PIN. */
    val pinHash: Flow<String> = context.settingsStore.data.map { it[KEY_PIN_HASH].orEmpty() }

    suspend fun setBufferSeconds(seconds: Int) {
        context.settingsStore.edit { it[KEY_BUFFER_SECONDS] = seconds }
    }

    suspend fun setMaxBufferSeconds(seconds: Int) {
        context.settingsStore.edit { it[KEY_MAX_BUFFER_SECONDS] = seconds }
    }

    suspend fun setAudioLanguage(language: String) {
        context.settingsStore.edit { it[KEY_AUDIO_LANGUAGE] = language }
    }

    suspend fun setSubtitleLanguage(language: String) {
        context.settingsStore.edit { it[KEY_SUBTITLE_LANGUAGE] = language }
    }

    suspend fun setSubtitleSize(size: Int) {
        context.settingsStore.edit { it[KEY_SUBTITLE_SIZE] = size }
    }

    suspend fun setResizeMode(mode: Int) {
        context.settingsStore.edit { it[KEY_RESIZE_MODE] = mode }
    }

    suspend fun setDefaultSort(sort: String) {
        context.settingsStore.edit { it[KEY_DEFAULT_SORT] = sort }
    }

    suspend fun setCleanNames(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_CLEAN_NAMES] = enabled }
    }

    suspend fun setPinHash(hash: String) {
        context.settingsStore.edit {
            if (hash.isEmpty()) it.remove(KEY_PIN_HASH) else it[KEY_PIN_HASH] = hash
        }
    }
}
