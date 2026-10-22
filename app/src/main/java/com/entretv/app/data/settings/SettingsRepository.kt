package com.entretv.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.entretv.app.BuildConfig
import com.entretv.app.ui.isTvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "entre_settings")

/**
 * Ajustes de reproducción de Configuración. Lo que se elige dentro del reproductor (audio,
 * subtítulos) vale solo para ese video y no cambia esto.
 */
data class PlayerPrefs(
    /** Idioma preferido del contenido ("es" o "en"): audio y subtítulos arrancan en él si existen. */
    val contentLanguage: String = SettingsRepository.DEFAULT_CONTENT_LANGUAGE,
    /** Con subtítulos al iniciar, se activan en [contentLanguage]; si no, arranca sin subtítulos. */
    val subtitlesByDefault: Boolean = false,
    val subtitleSize: Int = SettingsRepository.SUBTITLE_NORMAL,
    val resizeMode: Int = SettingsRepository.RESIZE_FIT,
    /** Formato de los canales en vivo de Xtream: [SettingsRepository.LIVE_HLS] o [SettingsRepository.LIVE_TS]. */
    val liveFormat: String = SettingsRepository.LIVE_HLS,
    /** [SettingsRepository.DECODER_AUTO], [SettingsRepository.DECODER_HARDWARE] o [SettingsRepository.DECODER_SOFTWARE]. */
    val decoder: Int = SettingsRepository.DECODER_AUTO,
    /** [SettingsRepository.FRAME_RATE_OFF] o [SettingsRepository.FRAME_RATE_AUTO]. */
    val frameRateMode: Int = SettingsRepository.FRAME_RATE_AUTO,
    /** Reproducción en túnel (solo TV). */
    val tunneling: Boolean = false,
    /** Botón "Saltar intro" al empezar los episodios de series. */
    val skipIntroButton: Boolean = true
)

/** Cómo se muestra el catálogo. */
data class CatalogPrefs(
    /** Nombre de [com.entretv.app.ui.SortOrder]. */
    val defaultSort: String = SettingsRepository.DEFAULT_SORT,
    val cleanNames: Boolean = true
)

/** Datos de la cuenta que manda el servidor al iniciar sesión y en cada actualización. */
data class AccountInfo(
    /** "Active", "Expired"…; vacío si el servidor no lo dice. */
    val status: String = "",
    /** Vencimiento en segundos Unix; 0 = sin vencimiento o desconocido. */
    val expiresAt: Long = 0L,
    val maxConnections: Int = 0,
    val isTrial: Boolean = false,
    /** Cuándo se consultó (milisegundos). */
    val checkedAt: Long = 0L
)

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

        const val DEFAULT_CONTENT_LANGUAGE = "es"
        val CONTENT_LANGUAGES = listOf("es", "en")

        const val LIVE_HLS = "m3u8"
        const val LIVE_TS = "ts"

        const val DECODER_AUTO = 0
        const val DECODER_HARDWARE = 1
        const val DECODER_SOFTWARE = 2

        const val FRAME_RATE_OFF = 0
        const val FRAME_RATE_AUTO = 1

        /** Actualización del catálogo al entrar a la app. */
        const val REFRESH_ALWAYS = 0
        const val REFRESH_DAILY = 1
        const val REFRESH_TWICE_DAILY = 2

        private val KEY_REFRESH_MODE = intPreferencesKey("refresh_mode")
        private val KEY_BUFFER_SECONDS = intPreferencesKey("start_buffer_seconds")
        private val KEY_MAX_BUFFER_SECONDS = intPreferencesKey("max_buffer_seconds")
        private val KEY_CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        private val KEY_SUBTITLES_DEFAULT = booleanPreferencesKey("subtitles_by_default")
        private val KEY_LIVE_FORMAT = stringPreferencesKey("live_format")
        private val KEY_DECODER = intPreferencesKey("decoder_mode")
        private val KEY_FRAME_RATE = intPreferencesKey("frame_rate_mode")
        private val KEY_TUNNELING = booleanPreferencesKey("tunneling")
        private val KEY_SHOW_CLOCK = booleanPreferencesKey("show_clock")
        private val KEY_SUBTITLE_SIZE = intPreferencesKey("subtitle_size")
        private val KEY_RESIZE_MODE = intPreferencesKey("resize_mode")
        private val KEY_DEFAULT_SORT = stringPreferencesKey("default_sort")
        private val KEY_CLEAN_NAMES = booleanPreferencesKey("clean_names")
        private val KEY_PIN_HASH = stringPreferencesKey("parental_pin_hash")
        private val KEY_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        private val KEY_SERVER_OVERRIDE = stringPreferencesKey("remote_server_url")
        private val KEY_REMOTE_TMDB_KEY = stringPreferencesKey("remote_tmdb_key")
        private val KEY_CONTINUE_DISMISSED = stringSetPreferencesKey("continue_dismissed")
        private val KEY_LIST_VIEW_SECTIONS = stringSetPreferencesKey("list_view_sections")
        private val KEY_SKIP_INTRO = booleanPreferencesKey("skip_intro_button")
        private val KEY_INTRO_ENDS = stringSetPreferencesKey("intro_ends")

        private fun lastRefreshKey(playlistId: String) = longPreferencesKey("last_refresh_$playlistId")
        private fun accountKey(playlistId: String) = stringPreferencesKey("account_info_$playlistId")
    }

    /** Estado y vencimiento de la cuenta; null si todavía no se consultó. */
    fun accountInfo(playlistId: String): Flow<AccountInfo?> = context.settingsStore.data.map { prefs ->
        val parts = prefs[accountKey(playlistId)]?.split('|') ?: return@map null
        AccountInfo(
            status = parts.getOrNull(0).orEmpty(),
            expiresAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
            maxConnections = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            isTrial = parts.getOrNull(3) == "1",
            checkedAt = parts.getOrNull(4)?.toLongOrNull() ?: 0L
        )
    }

    suspend fun setAccountInfo(playlistId: String, info: AccountInfo?) {
        context.settingsStore.edit {
            if (info == null) it.remove(accountKey(playlistId))
            else it[accountKey(playlistId)] = listOf(
                info.status.replace("|", ""), info.expiresAt, info.maxConnections,
                if (info.isTrial) 1 else 0, info.checkedAt
            ).joinToString("|")
        }
    }

    /** Servidor de las cuentas: el de update.json si llegó, si no el que trae esta versión. */
    val serverUrl: Flow<String> = context.settingsStore.data.map {
        it[KEY_SERVER_OVERRIDE]?.takeIf { url -> url.isNotBlank() } ?: BuildConfig.DEFAULT_SERVER_URL
    }

    suspend fun setServerOverride(url: String) {
        context.settingsStore.edit { it[KEY_SERVER_OVERRIDE] = url }
    }

    val tmdbApiKey: Flow<String> = context.settingsStore.data.map {
        it[KEY_REMOTE_TMDB_KEY]?.takeIf { key -> key.isNotBlank() } ?: BuildConfig.TMDB_API_KEY
    }

    suspend fun setRemoteTmdbKey(key: String) {
        context.settingsStore.edit { it[KEY_REMOTE_TMDB_KEY] = key }
    }

    suspend fun lastRefreshAt(playlistId: String): Long =
        context.settingsStore.data.first()[lastRefreshKey(playlistId)] ?: 0L

    fun lastRefreshFlow(playlistId: String): Flow<Long> =
        context.settingsStore.data.map { it[lastRefreshKey(playlistId)] ?: 0L }

    val refreshMode: Flow<Int> = context.settingsStore.data.map { it[KEY_REFRESH_MODE] ?: REFRESH_DAILY }

    suspend fun setRefreshMode(mode: Int) {
        context.settingsStore.edit { it[KEY_REFRESH_MODE] = mode }
    }

    suspend fun setLastRefreshAt(playlistId: String, at: Long) {
        context.settingsStore.edit {
            if (at <= 0L) it.remove(lastRefreshKey(playlistId)) else it[lastRefreshKey(playlistId)] = at
        }
    }

    /**
     * Series que el usuario quitó de "Seguir viendo" y cuándo. Vuelven a aparecer si mira
     * algo de esa serie después; el progreso no se borra.
     */
    val continueDismissed: Flow<Map<String, Long>> = context.settingsStore.data.map { prefs ->
        prefs[KEY_CONTINUE_DISMISSED].orEmpty().mapNotNull { entry ->
            val at = entry.substringAfterLast('|').toLongOrNull() ?: return@mapNotNull null
            entry.substringBeforeLast('|') to at
        }.toMap()
    }

    suspend fun dismissContinue(key: String, at: Long) {
        context.settingsStore.edit { prefs ->
            val kept = prefs[KEY_CONTINUE_DISMISSED].orEmpty().filterNot { it.substringBeforeLast('|') == key }
            prefs[KEY_CONTINUE_DISMISSED] = (kept + "$key|$at").takeLast(200).toSet()
        }
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
            contentLanguage = it[KEY_CONTENT_LANGUAGE]?.takeIf { lang -> lang in CONTENT_LANGUAGES } ?: DEFAULT_CONTENT_LANGUAGE,
            subtitlesByDefault = it[KEY_SUBTITLES_DEFAULT] ?: false,
            subtitleSize = it[KEY_SUBTITLE_SIZE] ?: SUBTITLE_NORMAL,
            resizeMode = it[KEY_RESIZE_MODE] ?: RESIZE_FIT,
            liveFormat = it[KEY_LIVE_FORMAT]?.takeIf { f -> f == LIVE_TS } ?: LIVE_HLS,
            decoder = it[KEY_DECODER]?.takeIf { d -> d in DECODER_AUTO..DECODER_SOFTWARE } ?: DECODER_AUTO,
            frameRateMode = it[KEY_FRAME_RATE]?.takeIf { m -> m in FRAME_RATE_OFF..FRAME_RATE_AUTO } ?: defaultFrameRateMode,
            tunneling = isTv && it[KEY_TUNNELING] == true,
            skipIntroButton = it[KEY_SKIP_INTRO] ?: true
        )
    }

    suspend fun setSkipIntroButton(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_SKIP_INTRO] = enabled }
    }

    /**
     * Dónde termina la intro de cada serie (ms), aprendido de los saltos hacia adelante que el
     * usuario hace al empezar un episodio; null si todavía no se sabe.
     */
    suspend fun introEnd(seriesKey: String): Long? =
        context.settingsStore.data.first()[KEY_INTRO_ENDS].orEmpty()
            .firstOrNull { it.substringBeforeLast('|') == seriesKey }
            ?.substringAfterLast('|')?.toLongOrNull()

    suspend fun setIntroEnd(seriesKey: String, endMs: Long) {
        context.settingsStore.edit { prefs ->
            val kept = prefs[KEY_INTRO_ENDS].orEmpty().filterNot { it.substringBeforeLast('|') == seriesKey }
            prefs[KEY_INTRO_ENDS] = (kept + "$seriesKey|$endMs").takeLast(300).toSet()
        }
    }

    private val isTv: Boolean by lazy { isTvDevice(context) }

    // En TV la película pide su frecuencia; en celulares no se toca la pantalla.
    private val defaultFrameRateMode: Int get() = if (isTv) FRAME_RATE_AUTO else FRAME_RATE_OFF

    suspend fun setFrameRateMode(mode: Int) {
        context.settingsStore.edit { it[KEY_FRAME_RATE] = mode }
    }

    suspend fun setTunneling(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_TUNNELING] = enabled }
    }

    /** Fecha y hora pequeña arriba en Inicio y en el reproductor. */
    val showClock: Flow<Boolean> = context.settingsStore.data.map { it[KEY_SHOW_CLOCK] ?: true }

    suspend fun setShowClock(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_SHOW_CLOCK] = enabled }
    }

    suspend fun setContentLanguage(language: String) {
        context.settingsStore.edit { it[KEY_CONTENT_LANGUAGE] = language }
    }

    suspend fun setSubtitlesByDefault(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_SUBTITLES_DEFAULT] = enabled }
    }

    suspend fun setLiveFormat(format: String) {
        context.settingsStore.edit { it[KEY_LIVE_FORMAT] = format }
    }

    suspend fun setDecoder(mode: Int) {
        context.settingsStore.edit { it[KEY_DECODER] = mode }
    }

    val catalogPrefs: Flow<CatalogPrefs> = context.settingsStore.data.map {
        CatalogPrefs(
            defaultSort = it[KEY_DEFAULT_SORT] ?: DEFAULT_SORT,
            cleanNames = it[KEY_CLEAN_NAMES] ?: true
        )
    }

    /**
     * Secciones (nombre de [com.entretv.app.ui.Section]) cuyas categorías se ven en lista, un
     * renglón por título; las demás, en cuadrícula de pósters.
     */
    val listViewSections: Flow<Set<String>> = context.settingsStore.data.map { it[KEY_LIST_VIEW_SECTIONS].orEmpty() }

    suspend fun setListView(section: String, list: Boolean) {
        context.settingsStore.edit { prefs ->
            val current = prefs[KEY_LIST_VIEW_SECTIONS].orEmpty()
            prefs[KEY_LIST_VIEW_SECTIONS] = if (list) current + section else current - section
        }
    }

    /** SHA-256 del PIN parental; vacío = sin PIN. */
    val pinHash: Flow<String> = context.settingsStore.data.map { it[KEY_PIN_HASH].orEmpty() }

    suspend fun setBufferSeconds(seconds: Int) {
        context.settingsStore.edit { it[KEY_BUFFER_SECONDS] = seconds }
    }

    suspend fun setMaxBufferSeconds(seconds: Int) {
        context.settingsStore.edit { it[KEY_MAX_BUFFER_SECONDS] = seconds }
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
