package com.entretv.app.player

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
 *  - Si el stream se cae, reconecta solo con espera creciente (1 s, 2 s, 4 s… hasta 15 s).
 *  - Si el en vivo se queda atrás de la ventana del servidor, salta al directo sin error.
 *  - Tras cada trabón en vivo se aleja un poco más del directo para ganar colchón.
 *  - OkHttp reutiliza conexiones entre segmentos HLS.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    settings: SettingsRepository
) {
    companion object {
        const val USER_AGENT = "EntreTV/1.0 (Android)"
        private const val CACHE_BYTES = 256L * 1024 * 1024
        private const val LOAD_RETRIES = 6
        private const val MAX_RECONNECTS = 8
        private const val MAX_RECONNECT_DELAY_MS = 15_000L
        private const val LONG_BUFFER_MS = 60_000
        private const val LIVE_OFFSET_LONG_BUFFER_MS = 20_000L
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

    // Comparte el pool de conexiones de la app, pero sin caché HTTP ni log por cada segmento.
    private val streamClient: OkHttpClient = okHttpClient.newBuilder()
        .cache(null)
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        // Un stream que deja de mandar datos se detecta rápido y se reconecta.
        .readTimeout(15, TimeUnit.SECONDS)
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
    private var reconnectAttempts = 0

    private val reconnect = Runnable {
        if (currentIsLive) player.seekToDefaultPosition()
        player.prepare()
        player.playWhenReady = true
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

        val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
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
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                _durationMs.value = if (state == Player.STATE_IDLE) 0L else exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_READY) {
                    reconnectAttempts = 0
                    _reconnecting.value = false
                    _error.value = null
                }
                if (state == Player.STATE_ENDED) onEnded?.invoke()
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

    private fun handleError(error: PlaybackException) {
        mainHandler.removeCallbacks(reconnect)
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        if (!isRecoverable(error) || reconnectAttempts >= MAX_RECONNECTS) {
            _reconnecting.value = false
            _error.value = if (currentIsLive)
                "El canal no responde. Revisa tu conexión o prueba otro canal."
            else
                "No se pudo reproducir. Revisa tu conexión e inténtalo de nuevo."
            return
        }
        reconnectAttempts++
        _reconnecting.value = true
        val delayMs = (1_000L shl (reconnectAttempts - 1)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        mainHandler.postDelayed(reconnect, delayMs)
    }

    private fun isRecoverable(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> false
        // Incluye 403/509 por límite de conexiones del proveedor: se libera al poco rato.
        in 2000..2999 -> true
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        // Los servidores IPTV a veces cortan un segmento a medias.
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_UNSPECIFIED -> currentIsLive
        else -> false
    }

    /** Reintento manual desde la pantalla, después de agotar los automáticos. */
    fun retry() {
        mainHandler.removeCallbacks(reconnect)
        reconnectAttempts = 0
        _error.value = null
        _reconnecting.value = true
        reconnect.run()
    }

    fun play(
        url: String,
        title: String,
        subtitle: String = "",
        startPositionMs: Long = 0L,
        isLive: Boolean = false
    ) {
        mainHandler.removeCallbacks(reconnect)
        reconnectAttempts = 0
        _reconnecting.value = false
        _error.value = null
        currentIsLive = isLive

        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .build()
            )
        // En vivo el búfer no puede pasar de la distancia al borde: con 60 s se va un poco
        // más atrás para tener margen ante cortes de red.
        if (isLive && maxBufferMs >= LONG_BUFFER_MS) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(LIVE_OFFSET_LONG_BUFFER_MS).build()
            )
        }
        val item = builder.build()

        applyPreferences()
        player.setPlaybackSpeed(1f)
        if (isLive) {
            player.setMediaSource(liveSourceFactory.createMediaSource(item))
        } else if (url.startsWith("file:")) {
            player.setMediaSource(localSourceFactory.createMediaSource(item), startPositionMs.coerceAtLeast(0L))
        } else {
            player.setMediaSource(vodSourceFactory.createMediaSource(item), startPositionMs.coerceAtLeast(0L))
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
        mainHandler.removeCallbacks(reconnect)
        _reconnecting.value = false
        _error.value = null
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        mainHandler.removeCallbacks(reconnect)
        player.release()
        cache.release()
    }
}
