package com.entretv.app.player

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SUBTITLES_OFF
import com.entretv.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Motor de reproducción EntreTV: único reproductor de la app.
 *
 *  - En vivo va directo a la red; solo películas y episodios pasan por la caché en disco.
 *  - En vivo hay un vigilante: si el video se congela, se queda cargando o el servidor corta,
 *    se recupera solo y sin límite de intentos, escalando: saltar al directo → volver a
 *    preparar → reconstruir la fuente, con espera creciente (0 s, 1 s, 2 s… hasta 15 s).
 *  - Si vuelve la red, reintenta de inmediato.
 *  - Películas y episodios reintentan hasta 8 veces desde el mismo minuto.
 *  - Nunca se registran URLs: llevan usuario y contraseña.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    settings: SettingsRepository
) {
    companion object {
        // Algunos proveedores atan la línea al user agent: no cambiarlo sin avisar.
        const val USER_AGENT = "EntreTV/1.0 (Android)"
        private const val CACHE_BYTES = 256L * 1024 * 1024
        private const val LOAD_RETRIES = 6
        private const val MAX_VOD_RECONNECTS = 8
        private const val MAX_RECONNECT_DELAY_MS = 15_000L
        private const val LONG_BUFFER_MS = 60_000
        private const val LIVE_OFFSET_LONG_BUFFER_MS = 20_000L
        private const val WATCHDOG_TICK_MS = 2_000L
        /** Cargando más de esto sin avanzar = trabado. */
        private const val STALL_BUFFERING_MS = 15_000L
        /** "Reproduciendo" pero con la posición quieta más de esto = congelado. */
        private const val STALL_FROZEN_MS = 8_000L
        /** Tras recuperarse, cuánto tiene que andar bien para volver a empezar la escalera. */
        private const val STABLE_MS = 20_000L
        private const val LIVE_MIN_SPEED = 0.97f
        private const val LIVE_MAX_SPEED = 1.03f
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lo lee el hilo de reproducción en cada arranque o trabón.
    @Volatile
    private var startBufferMs = SettingsRepository.DEFAULT_BUFFER_SECONDS * 1_000

    @Volatile
    private var maxBufferMs = SettingsRepository.DEFAULT_MAX_BUFFER_SECONDS * 1_000

    @Volatile
    private var prefs = PlayerPrefs()

    init {
        scope.launch { settings.bufferSeconds.collect { startBufferMs = it * 1_000 } }
        scope.launch { settings.maxBufferSeconds.collect { maxBufferMs = it * 1_000 } }
        scope.launch { settings.playerPrefs.collect { prefs = it } }
    }

    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "entre_media"),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context)
        )
    }

    // Comparte el pool de conexiones de la app, pero sin caché HTTP ni interceptores.
    // followSslRedirects permite redirecciones http ⇄ https, comunes en paneles IPTV.
    private val streamClient: OkHttpClient = okHttpClient.newBuilder()
        .cache(null)
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        // Un stream que deja de mandar datos se detecta rápido y se reconecta.
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    // HTTP por OkHttp; rtmp://, archivos y content:// los resuelve DefaultDataSource.
    private val httpFactory: DataSource.Factory by lazy {
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(streamClient).setUserAgent(USER_AGENT))
    }

    private val vodDataFactory: DataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val errorPolicy = DefaultLoadErrorHandlingPolicy(LOAD_RETRIES)

    private val liveSourceFactory by lazy {
        DefaultMediaSourceFactory(httpFactory).setLoadErrorHandlingPolicy(errorPolicy)
    }

    private val hlsSourceFactory by lazy {
        HlsMediaSource.Factory(httpFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(errorPolicy)
    }

    /**
     * MPEG-TS continuo (lo típico de Xtream: /live/…/123.ts). Se acepta arrancar en un
     * cuadro clave que no es IDR y se detectan unidades sin delimitador: muchos encoders
     * IPTV no los mandan y sin esto el canal tarda o no arranca.
     */
    private val tsSourceFactory by lazy {
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        ProgressiveMediaSource.Factory(httpFactory, extractors).setLoadErrorHandlingPolicy(errorPolicy)
    }

    private val vodSourceFactory by lazy {
        DefaultMediaSourceFactory(vodDataFactory).setLoadErrorHandlingPolicy(errorPolicy)
    }

    /** Descargas: se leen del disco directo, sin pasar por la caché de video. */
    private val localSourceFactory by lazy {
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
    }

    val player: ExoPlayer by lazy { buildPlayer() }

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting

    /** Intento de recuperación en curso (0 si todo va bien). */
    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttempt: StateFlow<Int> = _reconnectAttempt

    /** Sin ninguna red: la pantalla lo dice en vez de "Reconectando…". */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    /** Mensaje cuando ya no se pudo recuperar solo; la pantalla ofrece "Reintentar". */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Pistas de audio, subtítulos y video del contenido actual. */
    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    /** Se dispara cuando un contenido VOD termina (para auto-next de episodios). */
    var onEnded: (() -> Unit)? = null

    private var currentIsLive = false
    private var currentItem: MediaItem? = null
    private var currentUrl: String = ""
    private var reconnectAttempts = 0
    private var reconnectPending = false
    private var lastPositionMs = C.TIME_UNSET
    private var lastProgressAt = 0L
    private var recoveredAt = 0L

    private val reconnect = Runnable {
        reconnectPending = false
        recover()
    }

    private val watchdog = object : Runnable {
        override fun run() {
            checkStall()
            mainHandler.postDelayed(this, WATCHDOG_TICK_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post { onNetworkAvailable() }
        }

        override fun onLost(network: Network) {
            mainHandler.post {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                _offline.value = cm?.activeNetwork == null
            }
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val loadControl = UserBufferLoadControl(targetBufferBytes(), { startBufferMs }, { maxBufferMs })

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoMixedDecoderSupportAdaptiveness(true)
                .setForceHighestSupportedBitrate(false)
                .build()
        }

        // Corrige la distancia al directo con cambios de velocidad que no se notan (±3 %).
        val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(LIVE_MIN_SPEED)
            .setFallbackMaxPlaybackSpeed(LIVE_MAX_SPEED)
            .setTargetLiveOffsetIncrementOnRebufferMs(1_000)
            .build()

        // Primero el decodificador del equipo (mantiene el passthrough a receptores);
        // FFmpeg entra solo si el equipo no sabe decodificar ese audio.
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val exoPlayer = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(liveSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setLivePlaybackSpeedControl(liveSpeedControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlayingFlow.value = isPlaying
                if (isPlaying) {
                    _reconnecting.value = false
                    lastProgressAt = SystemClock.elapsedRealtime()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                _durationMs.value = if (state == Player.STATE_IDLE) 0L else exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_READY) {
                    _error.value = null
                    if (!currentIsLive) {
                        reconnectAttempts = 0
                        _reconnectAttempt.value = 0
                        _reconnecting.value = false
                    }
                }
                if (state == Player.STATE_ENDED) {
                    // Un canal en vivo no "termina": el servidor cortó la conexión.
                    if (currentIsLive) scheduleRecovery() else onEnded?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }

            override fun onTracksChanged(tracks: Tracks) {
                _tracks.value = tracks
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _speed.value = playbackParameters.speed
            }
        })

        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        }
        return exoPlayer
    }

    // ============================ Pistas y ajustes ============================

    /** Idiomas guardados por el usuario; se aplican al empezar cada contenido. */
    private fun applyPreferences() {
        val p = prefs
        val audio = p.audioLanguage.ifEmpty { Locale.getDefault().language }
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguages(audio)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, p.subtitleLanguage == SUBTITLES_OFF)
        when (p.subtitleLanguage) {
            "", SUBTITLES_OFF -> builder.setPreferredTextLanguages()
            else -> builder.setPreferredTextLanguages(p.subtitleLanguage)
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Fija una pista concreta de audio, subtítulos o video. */
    fun selectTrack(group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** Calidad automática: vuelve a adaptar la resolución a la conexión. */
    fun autoQuality() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    /** Datos técnicos para el panel de información. */
    fun techInfo(): TechInfo {
        val video = player.videoFormat
        val audio = player.audioFormat
        return TechInfo(
            resolution = video?.let { if (it.width > 0) "${it.width}×${it.height}" else null },
            videoCodec = video?.sampleMimeType ?: video?.codecs,
            frameRate = video?.frameRate?.takeIf { it > 0 },
            videoBitrate = video?.bitrate?.takeIf { it > 0 } ?: video?.averageBitrate?.takeIf { it > 0 },
            audioCodec = audio?.sampleMimeType,
            audioChannels = audio?.channelCount?.takeIf { it > 0 },
            bufferedMs = player.totalBufferedDuration
        )
    }

    /** Techo del búfer según la memoria de la app: ~30 s de 4K en un equipo medio. */
    private fun targetBufferBytes(): Int {
        val am = context.getSystemService(ActivityManager::class.java)
        val heapMb = am?.largeMemoryClass ?: 128
        val mb = when {
            am?.isLowRamDevice == true || heapMb <= 128 -> 48
            heapMb <= 256 -> 96
            else -> 160
        }
        return mb * 1024 * 1024
    }

    // ============================ Recuperación ============================

    private fun handleError(error: PlaybackException) {
        cancelRecovery()
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        if (currentIsLive) {
            if (!isRecoverableLive(error)) {
                showFatal()
                return
            }
            // Un decodificador roto no se arregla volviendo a preparar: directo a reconstruir.
            if (error.errorCode in 4000..4999) reconnectAttempts = maxOf(reconnectAttempts, 2)
            scheduleRecovery()
            return
        }
        if (!isRecoverableVod(error) || reconnectAttempts >= MAX_VOD_RECONNECTS) {
            showFatal()
            return
        }
        scheduleRecovery()
    }

    private fun showFatal() {
        _reconnecting.value = false
        _reconnectAttempt.value = 0
        _error.value = if (currentIsLive)
            "El canal no responde. Revisa tu conexión o prueba otro canal."
        else
            "No se pudo reproducir. Revisa tu conexión e inténtalo de nuevo."
    }

    /** En vivo casi todo se reintenta: 403/404/509 suelen ser límites momentáneos del panel. */
    private fun isRecoverableLive(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> false
        else -> true
    }

    private fun isRecoverableVod(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> false
        // Incluye 403/509 por límite de conexiones del proveedor: se libera al poco rato.
        in 2000..2999 -> true
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    /** Programa el siguiente escalón; el primero es inmediato, luego 1 s, 2 s, 4 s… hasta 15 s. */
    private fun scheduleRecovery() {
        if (reconnectPending || currentItem == null) return
        reconnectAttempts++
        _reconnectAttempt.value = reconnectAttempts
        _reconnecting.value = true
        val delayMs = if (reconnectAttempts <= 1) 0L
        else (1_000L shl (reconnectAttempts - 2).coerceAtMost(4)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectPending = true
        mainHandler.postDelayed(reconnect, delayMs)
    }

    private fun cancelRecovery() {
        mainHandler.removeCallbacks(reconnect)
        reconnectPending = false
    }

    private fun recover() {
        val item = currentItem ?: return
        val now = SystemClock.elapsedRealtime()
        lastProgressAt = now
        recoveredAt = now
        lastPositionMs = C.TIME_UNSET
        when {
            currentIsLive && reconnectAttempts <= 1 -> {
                player.seekToDefaultPosition()
                player.prepare()
            }
            reconnectAttempts <= 2 -> {
                if (player.playerError == null) player.stop()
                player.prepare()
            }
            else -> {
                val position = player.currentPosition
                if (currentIsLive) player.setMediaSource(sourceFor(item, currentUrl))
                else player.setMediaSource(sourceFor(item, currentUrl), position.coerceAtLeast(0L))
                player.prepare()
            }
        }
        player.playWhenReady = true
    }

    /** Vigilante del en vivo: congelado o cargando demasiado → siguiente escalón. */
    private fun checkStall() {
        if (!currentIsLive || currentItem == null || _error.value != null || reconnectPending) return
        val now = SystemClock.elapsedRealtime()
        if (!player.playWhenReady) {
            lastProgressAt = now
            return
        }
        when (player.playbackState) {
            Player.STATE_READY -> {
                val position = player.currentPosition
                if (!player.isPlaying || position != lastPositionMs) {
                    // Pausado por el sistema (llamada, foco de audio) no cuenta como trabado.
                    lastPositionMs = position
                    lastProgressAt = now
                    if (player.isPlaying && reconnectAttempts > 0 && now - recoveredAt > STABLE_MS) {
                        reconnectAttempts = 0
                        _reconnectAttempt.value = 0
                        _reconnecting.value = false
                    }
                } else if (now - lastProgressAt > STALL_FROZEN_MS) {
                    scheduleRecovery()
                }
            }
            Player.STATE_BUFFERING -> if (now - lastProgressAt > STALL_BUFFERING_MS) scheduleRecovery()
            else -> lastProgressAt = now
        }
    }

    private fun onNetworkAvailable() {
        _offline.value = false
        if (currentItem == null) return
        when {
            _error.value != null -> retry()
            _reconnecting.value -> {
                cancelRecovery()
                reconnect.run()
            }
        }
    }

    /** Reintento manual desde la pantalla, después de agotar los automáticos. */
    fun retry() {
        cancelRecovery()
        _error.value = null
        _reconnecting.value = true
        reconnectAttempts = 1
        _reconnectAttempt.value = 1
        recover()
    }

    private fun sourceFor(item: MediaItem, url: String): MediaSource {
        if (url.startsWith("file:")) return localSourceFactory.createMediaSource(item)
        if (!currentIsLive) return vodSourceFactory.createMediaSource(item)
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> hlsSourceFactory.createMediaSource(item)
            path.endsWith(".ts") -> tsSourceFactory.createMediaSource(item)
            else -> liveSourceFactory.createMediaSource(item)
        }
    }

    fun play(
        url: String,
        title: String,
        subtitle: String = "",
        startPositionMs: Long = 0L,
        isLive: Boolean = false
    ) {
        cancelRecovery()
        reconnectAttempts = 0
        _reconnectAttempt.value = 0
        _reconnecting.value = false
        _error.value = null
        currentIsLive = isLive
        currentUrl = url
        lastPositionMs = C.TIME_UNSET
        lastProgressAt = SystemClock.elapsedRealtime()

        // El id no lleva la URL: la sesión multimedia lo comparte con otras apps.
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url.hashCode().toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .build()
            )
        // En vivo el búfer no puede pasar de la distancia al borde: con 60 s se va un poco
        // más atrás para tener margen ante cortes de red.
        if (isLive) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(LIVE_MIN_SPEED)
                    .setMaxPlaybackSpeed(LIVE_MAX_SPEED)
                    .apply { if (maxBufferMs >= LONG_BUFFER_MS) setTargetOffsetMs(LIVE_OFFSET_LONG_BUFFER_MS) }
                    .build()
            )
        }
        val item = builder.build()
        currentItem = item

        applyPreferences()
        player.setPlaybackSpeed(1f)
        if (isLive) player.setMediaSource(sourceFor(item, url))
        else player.setMediaSource(sourceFor(item, url), startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
        mainHandler.removeCallbacks(watchdog)
        mainHandler.postDelayed(watchdog, WATCHDOG_TICK_MS)
    }

    fun stop() {
        cancelRecovery()
        mainHandler.removeCallbacks(watchdog)
        currentItem = null
        _reconnecting.value = false
        _reconnectAttempt.value = 0
        _error.value = null
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        cancelRecovery()
        mainHandler.removeCallbacks(watchdog)
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        player.release()
        cache.release()
    }
}
