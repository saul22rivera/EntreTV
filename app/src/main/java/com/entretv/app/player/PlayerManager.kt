package com.entretv.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.entretv.app.R
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.UiText
import com.entretv.app.ui.isLowRamDevice
import com.entretv.app.ui.isTvDevice
import com.entretv.app.ui.uiText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/** Imagen fija metida como pista de video (portada en MKV/MP4): no es la película. */
fun Format.isCoverArt(): Boolean =
    MimeTypes.isImage(sampleMimeType) || sampleMimeType == MimeTypes.VIDEO_MJPEG

/**
 * Motor de reproducción EntreTV: único reproductor de la app.
 *
 *  - En vivo va directo a la red; solo películas y episodios pasan por la caché en disco.
 *  - En vivo hay un vigilante: si el video se congela, se queda cargando o el servidor corta,
 *    se recupera solo y sin límite de intentos, escalando: saltar al directo → volver a
 *    preparar → reconstruir la fuente, con espera creciente (0 s, 1 s, 2 s… hasta 15 s).
 *  - Si vuelve la red, reintenta de inmediato.
 *  - Películas y episodios reintentan desde el mismo minuto sin límite mientras haya red (el
 *    error aparece tras 30 min seguidos fallando); sin red esperan y siguen solos al volver.
 *  - Con red no medida, la película o episodio se baja entero al disco por delante de la
 *    reproducción con una sola conexión ([VodAheadDownloader]); si no se puede, se guardan
 *    hasta 5 min en memoria.
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
        private const val LOW_RAM_CACHE_BYTES = 96L * 1024 * 1024
        private const val LOAD_RETRIES = 6
        /** Película fallando sin parar (con red) durante esto: se muestra el error con "Reintentar". */
        private const val VOD_GIVE_UP_MS = 30 * 60_000L
        /** Película cargando sin avanzar nada (con red) durante esto = trabada. */
        private const val VOD_STALL_BUFFERING_MS = 20_000L
        /** Espacio libre mínimo para intentar bajar la película al disco (el chequeo fino lo hace la descarga). */
        private const val AHEAD_MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024
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
        /** ~3 s de una película 1080p típica: alcanza para la cabecera y el primer cuadro. */
        private const val PRELOAD_BYTES = 2L * 1024 * 1024
        /** Lo que "Reproducir" espera a que la precarga suelte la conexión y la caché. */
        private const val PRELOAD_RELEASE_WAIT_MS = 250L
        /** Sonido avanzando sin ningún cuadro de video durante esto = imagen trabada. */
        private const val NO_PICTURE_MS = 3_000L
        /** Al empezar, tras un salto o un reinicio del video se vigila más seguido y con menos espera. */
        private const val FAST_PICTURE_WINDOW_MS = 15_000L
        private const val FAST_PICTURE_TICK_MS = 500L
        private const val NO_PICTURE_FAST_MS = 1_500L
        /** "Reparar imagen" sin otro toque durante esto = funcionó; se recuerda para ese tipo de video. */
        private const val REPAIR_SETTLE_MS = 20_000L
        private const val MAX_PICTURE_FIXES = 3
        /** Pausa entre quitar y volver a poner la pista de video: si no, el cambio no llega a soltar el decodificador. */
        private const val VIDEO_RESTART_GAP_MS = 300L
        private val XTREAM_LIVE = Regex("/live/[^/]+/[^/]+/\\d+\\.(m3u8|ts)$", RegexOption.IGNORE_CASE)
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

    private val lowRam: Boolean by lazy { isLowRamDevice(context) }

    // Una sola base para las dos cachés de video.
    private val databaseProvider by lazy { StandaloneDatabaseProvider(context) }

    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "entre_media"),
            // Los equipos con poca memoria suelen traer poco almacenamiento.
            LeastRecentlyUsedCacheEvictor(if (lowRam) LOW_RAM_CACHE_BYTES else CACHE_BYTES),
            databaseProvider
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

    private val vodDataFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val errorPolicy = DefaultLoadErrorHandlingPolicy(LOAD_RETRIES)
    private val vodErrorPolicy = VodLoadErrorPolicy(LOAD_RETRIES)

    // Cliente propio, como el de la precarga: cancelar sus llamadas corta la conexión al instante.
    private val aheadClient: OkHttpClient by lazy {
        streamClient.newBuilder().dispatcher(Dispatcher()).build()
    }

    // Crearlo no abre nada: la caché y el hilo nacen con la primera película.
    private val ahead: VodAheadDownloader by lazy {
        VodAheadDownloader(File(context.noBackupFilesDir, "entre_vod_ahead"), databaseProvider, aheadClient, USER_AGENT)
    }

    private val aheadSourceFactory by lazy {
        ProgressiveMediaSource.Factory(AheadCacheDataSource.Factory(ahead)).setLoadErrorHandlingPolicy(vodErrorPolicy)
    }

    // Cliente propio para poder cortar su conexión al instante: interrumpir el hilo no
    // desbloquea una lectura de red y la línea quedaría ocupada al abrir el reproductor.
    private val preloadClient: OkHttpClient by lazy {
        streamClient.newBuilder().dispatcher(Dispatcher()).build()
    }

    private val preloadDataFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(preloadClient).setUserAgent(USER_AGENT))
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val preloadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EntrePreload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    @Volatile
    private var preloadWriter: CacheWriter? = null
    private var preloadTask: Future<*>? = null
    /** Cambia en cada precarga o cancelación; una tarea vieja que despierta tarde no hace nada. */
    @Volatile
    private var preloadGeneration = 0

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
        DefaultMediaSourceFactory(vodDataFactory).setLoadErrorHandlingPolicy(vodErrorPolicy)
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
    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error

    /** Película o episodio por la red (no en vivo ni descarga). */
    private val _vodActive = MutableStateFlow(false)

    /**
     * Película o episodio parado por falta de red: la pantalla muestra "Sin internet · se reanuda
     * solo" en vez de "Reconectando…". Sigue solo al volver la red.
     */
    val waitingForNetwork: StateFlow<Boolean> =
        combine(_vodActive, _offline, _isBuffering, _reconnecting) { vod, offline, buffering, reconnecting ->
            vod && offline && (buffering || reconnecting)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Parte de la película ya bajada al disco (0..1); null si no se está bajando adelantada. */
    val aheadDownloaded: StateFlow<Float?> get() = ahead.progress

    /** Pistas de audio, subtítulos y video del contenido actual. */
    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks

    /** Lo elegido a mano (pistas fijas, audio o video apagados) además de lo que trae [tracks]. */
    private val _trackParams = MutableStateFlow(TrackSelectionParameters.DEFAULT)
    val trackParams: StateFlow<TrackSelectionParameters> = _trackParams

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    /** Clave del contenido cargado (id del canal, película o episodio); null si no hay nada. */
    private val _currentKey = MutableStateFlow<String?>(null)
    val currentKey: StateFlow<String?> = _currentKey

    /** Se dispara cuando un contenido VOD termina (para auto-next de episodios). */
    var onEnded: (() -> Unit)? = null

    // También lo lee el búfer desde el hilo de reproducción.
    @Volatile
    private var currentIsLive = false
    private var currentItem: MediaItem? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private val isTv: Boolean by lazy { isTvDevice(context) }
    private var currentUrl: String = ""
    private var reconnectAttempts = 0
    private var reconnectPending = false
    private var lastPositionMs = C.TIME_UNSET
    private var lastBufferedMs = C.TIME_UNSET
    private var lastProgressAt = 0L
    private var recoveredAt = 0L
    /** Desde cuándo la película falla sin volver a reproducir (0 si no falla); sin red no cuenta. */
    private var vodFailingSince = 0L
    /** Título que se baja adelantado al disco; null si va por el camino normal. */
    private var aheadUrl: String? = null

    // Lo lee el búfer desde el hilo de reproducción.
    @Volatile
    private var vodLongBuffer = false

    // Vigilante de imagen, por contenido.
    private var firstFrameShown = false
    private var pictureWaitFromMs = C.TIME_UNSET
    private var pictureFixes = 0
    private var coverArtChecked = false
    /** Cuenta los reinicios de video: uno pendiente de otro contenido no se aplica. */
    private var videoRestartToken = 0
    /** Hasta cuándo se vigila la imagen cada [FAST_PICTURE_TICK_MS]. */
    private var fastPictureUntil = 0L
    private var surfaceHasSize = false
    private var surfaceSizedAt = 0L
    /** El decodificador de video nació antes de que la pantalla tuviera tamaño (TCL/MediaTek: sonido sin imagen). */
    private var decoderBeforeSurface = false
    /** Reinicio automático del video al empezar: uno por contenido. */
    private var startRestartDone = false
    private var proactiveRestartPending = false
    private var fixMemoryChecked = false
    /** Tipo del video actual para [VideoFixMemory]. */
    private var videoFixKey: String? = null
    private var repairKey: String? = null

    // Lo lee el hilo de reproducción al crear cada decodificador.
    @Volatile
    private var softwareVideo = false
    private var manualRepairs = 0

    private val videoFixes by lazy { VideoFixMemory(context) }

    private val reconnect = Runnable {
        reconnectPending = false
        recover()
    }

    private val watchdog = object : Runnable {
        override fun run() {
            checkStall()
            checkPicture()
            mainHandler.postDelayed(this, WATCHDOG_TICK_MS)
        }
    }

    private val fastPicture = object : Runnable {
        override fun run() {
            checkPicture()
            if (!firstFrameShown && currentItem != null && SystemClock.elapsedRealtime() < fastPictureUntil) {
                mainHandler.postDelayed(this, FAST_PICTURE_TICK_MS)
            }
        }
    }

    private val rememberRepair = Runnable {
        val key = repairKey ?: return@Runnable
        if (currentItem != null) {
            videoFixes.remember(key, if (softwareVideo) VideoFixMemory.LEVEL_SOFTWARE else VideoFixMemory.LEVEL_RESTART)
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
        val loadControl = UserBufferLoadControl(
            targetBufferBytes(), longVodBufferBytes(), lowRam,
            { startBufferMs }, { maxBufferMs }, { currentIsLive }, { vodLongBuffer }
        )

        trackSelector = DefaultTrackSelector(context).apply {
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
        // El decodificador de video de Configuración se consulta al crear cada decodificador:
        // vale desde el siguiente video sin rehacer el reproductor.
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(::decoderInfos)

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
                    vodFailingSince = 0L
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                _durationMs.value = if (state == Player.STATE_IDLE) 0L else exoPlayer.duration.coerceAtLeast(0L)
                if (aheadUrl != null && _durationMs.value > 0) ahead.durationMs = _durationMs.value
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
                skipCoverArt(tracks)
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                _trackParams.value = parameters
            }

            override fun onRenderedFirstFrame() {
                firstFrameShown = true
                if (proactiveRestartPending) {
                    proactiveRestartPending = false
                    autoRestartVideo()
                }
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                onSurfaceSize(width > 0 && height > 0)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                pictureWaitFromMs = C.TIME_UNSET
                // Tras un salto Media3 no siempre vuelve a avisar del primer cuadro: solo se vigila si aún no hubo imagen.
                if (!firstFrameShown) armFastPicture()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _speed.value = playbackParameters.speed
            }
        })
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                onVideoDecoderReady(initializedTimestampMs)
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                onVideoFormat(format)
            }
        })
        _trackParams.value = exoPlayer.trackSelectionParameters

        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        }
        return exoPlayer
    }

    // ============================ Pistas y ajustes ============================

    /**
     * Idioma y subtítulos de Configuración; se aplican al empezar cada contenido y borran lo
     * que se eligió a mano en el anterior.
     */
    private fun applyPreferences() {
        val p = prefs
        // Las imágenes del contenedor (portadas) nunca se muestran: solo taparían el video.
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguages(p.contentLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !p.subtitlesByDefault)
        if (p.subtitlesByDefault) {
            builder.setPreferredTextLanguages(p.contentLanguage).setSelectUndeterminedTextLanguage(false)
        } else {
            builder.setPreferredTextLanguages()
        }
        player.trackSelectionParameters = builder.build()
    }

    /**
     * Frecuencia y modo túnel de Configuración. En "Automático" Media3 ajusta la superficie solo
     * si el cambio no se nota; el cambio de modo de la TV lo pide la pantalla del reproductor.
     */
    private fun applyVideoOutput() {
        val p = prefs
        player.setVideoChangeFrameRateStrategy(
            if (p.frameRateMode == SettingsRepository.FRAME_RATE_AUTO) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        )
        val tunnel = isTv && p.tunneling
        if (trackSelector.parameters.tunnelingEnabled != tunnel) {
            trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(tunnel))
        }
    }

    /**
     * Canales en vivo de Xtream (/live/usuario/clave/123.m3u8): se pide el formato elegido en
     * Configuración. Las URLs de listas M3U se dejan como vienen.
     */
    private fun liveUrl(url: String): String {
        val match = XTREAM_LIVE.find(url.substringBefore('?')) ?: return url
        val ext = match.groupValues[1]
        val wanted = prefs.liveFormat
        if (ext.equals(wanted, ignoreCase = true)) return url
        return url.replaceRange(match.groups[1]!!.range, wanted)
    }

    /**
     * Decodificadores de video en el orden del ajuste: hardware o software primero. Los otros
     * quedan detrás para que el video igual arranque si el preferido no puede (con
     * setEnableDecoderFallback, uno que falla al iniciar pasa al siguiente de esta lista).
     * [softwareVideo] lo pone el vigilante de imagen o un error del decodificador.
     */
    private fun decoderInfos(mimeType: String, secure: Boolean, tunneling: Boolean): List<MediaCodecInfo> {
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
        if (!MimeTypes.isVideo(mimeType)) return infos
        if (softwareVideo) return infos.sortedByDescending { it.softwareOnly }
        return when (prefs.decoder) {
            SettingsRepository.DECODER_HARDWARE -> infos.sortedByDescending { it.hardwareAccelerated }
            SettingsRepository.DECODER_SOFTWARE -> infos.sortedByDescending { it.softwareOnly }
            else -> infos
        }
    }

    /** Fija una pista concreta de audio o subtítulos. */
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

    fun disableAudio() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
    }

    /**
     * Fija una pista de video. Elegir la que ya se ve la reinicia (suelta y vuelve a crear el
     * decodificador): es el arreglo a mano de "se oye pero no se ve".
     */
    fun selectVideoTrack(group: Tracks.Group, trackIndex: Int) {
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
        val params = player.trackSelectionParameters
        val alreadyShown = C.TRACK_TYPE_VIDEO !in params.disabledTrackTypes &&
            player.currentTracks.groups.any { it.mediaTrackGroup == group.mediaTrackGroup && it.isTrackSelected(trackIndex) }
        resetPictureWait()
        if (alreadyShown) restartVideo(override)
        else player.trackSelectionParameters = params.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setOverrideForType(override)
            .build()
    }

    /** Video automático: vuelve a adaptar la resolución a la conexión. */
    fun autoVideo() {
        resetPictureWait()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
    }

    /**
     * Botón "Reparar imagen": suelta y vuelve a crear el decodificador de video. Desde la
     * segunda vez en el mismo contenido alterna entre el de hardware y el de software (este
     * sin modo túnel). Devuelve true si quedó el de software.
     * Si no se vuelve a tocar en [REPAIR_SETTLE_MS], lo que quedó se recuerda para ese tipo de video.
     */
    fun repairVideo(): Boolean {
        repairKey = player.videoFormat?.takeUnless { it.isCoverArt() }?.let { VideoFixMemory.keyFor(it) } ?: videoFixKey ?: repairKey
        manualRepairs++
        if (manualRepairs >= 2) {
            if (softwareVideo) softwareVideo = false else useSoftwareVideo()
        }
        restartVideo()
        mainHandler.removeCallbacks(rememberRepair)
        if (repairKey != null) mainHandler.postDelayed(rememberRepair, REPAIR_SETTLE_MS)
        return softwareVideo
    }

    /** Borra los arreglos de imagen recordados por [repairVideo]. */
    fun forgetVideoFixes() {
        mainHandler.removeCallbacks(rememberRepair)
        repairKey = null
        videoFixes.clear()
    }

    /** Decodificador por software desde el próximo que se cree; el modo túnel no lo admite. */
    private fun useSoftwareVideo() {
        softwareVideo = true
        if (trackSelector.parameters.tunnelingEnabled) {
            trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(false))
        }
    }

    fun disableVideo() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
    }

    /**
     * Quita la pista de video y la vuelve a poner ([override] si se pasa) un momento después.
     * Los dos cambios juntos se resolverían en uno solo y el decodificador no se soltaría.
     */
    private fun restartVideo(override: TrackSelectionOverride? = null) {
        val token = ++videoRestartToken
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        mainHandler.postDelayed({
            if (token != videoRestartToken || currentItem == null) return@postDelayed
            resetPictureWait()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .apply { if (override != null) setOverrideForType(override) }
                .build()
        }, VIDEO_RESTART_GAP_MS)
    }

    private fun resetPictureWait() {
        firstFrameShown = false
        pictureWaitFromMs = C.TIME_UNSET
        armFastPicture()
    }

    private fun armFastPicture() {
        fastPictureUntil = SystemClock.elapsedRealtime() + FAST_PICTURE_WINDOW_MS
        mainHandler.removeCallbacks(fastPicture)
        mainHandler.postDelayed(fastPicture, FAST_PICTURE_TICK_MS)
    }

    /** Reinicio del video al empezar (pantalla tardía o arreglo recordado): uno por contenido. */
    private fun autoRestartVideo() {
        if (startRestartDone || currentItem == null) return
        startRestartDone = true
        proactiveRestartPending = false
        restartVideo()
    }

    /**
     * La pantalla recibió tamaño: con la superficie nueva llega otro primer cuadro, así que se
     * vuelve a esperar. Si el decodificador ya existía (en TCL/MediaTek suele quedar sin imagen),
     * se reinicia una vez.
     */
    private fun onSurfaceSize(hasSize: Boolean) {
        if (hasSize && !surfaceHasSize) {
            surfaceSizedAt = SystemClock.elapsedRealtime()
            if (currentItem != null) {
                resetPictureWait()
                if (decoderBeforeSurface && isTv) autoRestartVideo()
            }
        }
        surfaceHasSize = hasSize
    }

    /** [initializedAtMs] es del reloj de arranque: el aviso llega al hilo principal un poco después. */
    private fun onVideoDecoderReady(initializedAtMs: Long) {
        val surface = player.surfaceSize
        val sized = surface.width > 0 && surface.height > 0
        decoderBeforeSurface = !sized || initializedAtMs < surfaceSizedAt
        if (decoderBeforeSurface && sized && isTv) autoRestartVideo()
    }

    /** Primer video de cada contenido: aplica lo que [VideoFixMemory] recuerda para su tipo. */
    private fun onVideoFormat(format: Format) {
        if (format.isCoverArt()) return
        val key = VideoFixMemory.keyFor(format) ?: return
        videoFixKey = key
        if (fixMemoryChecked || currentItem == null) return
        fixMemoryChecked = true
        when (videoFixes.levelFor(key)) {
            // El decodificador ya se creó con el formato: hay que soltarlo para que nazca el de software.
            // No gasta el reinicio por pantalla tardía: el nuevo también puede nacer sin pantalla.
            VideoFixMemory.LEVEL_SOFTWARE -> {
                if (!softwareVideo) {
                    useSoftwareVideo()
                    restartVideo()
                }
            }
            VideoFixMemory.LEVEL_RESTART -> {
                if (firstFrameShown) autoRestartVideo() else proactiveRestartPending = !startRestartDone
            }
        }
    }

    /**
     * Si el reproductor eligió una portada (imagen fija) y el archivo trae una pista de video
     * de verdad, se cambia a esa. Una vez por contenido: lo que elija el usuario después manda.
     */
    private fun skipCoverArt(tracks: Tracks) {
        if (coverArtChecked) return
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (video.isEmpty()) return
        coverArtChecked = true
        val shown = video.firstOrNull { it.isSelected } ?: return
        val shownIndex = (0 until shown.length).firstOrNull { shown.isTrackSelected(it) } ?: return
        if (!shown.getTrackFormat(shownIndex).isCoverArt()) return
        val real = realVideoTrack(video, exclude = shown) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(real)
            .build()
    }

    /** La pista de video soportada de mayor resolución que no sea una imagen fija. */
    private fun realVideoTrack(video: List<Tracks.Group>, exclude: Tracks.Group?): TrackSelectionOverride? =
        video.asSequence()
            .filter { it !== exclude && it.mediaTrackGroup != exclude?.mediaTrackGroup }
            .flatMap { group -> (0 until group.length).asSequence().map { group to it } }
            .filter { (group, i) -> group.isTrackSupported(i) && !group.getTrackFormat(i).isCoverArt() }
            .maxWithOrNull(
                compareBy({ (group, i) -> group.getTrackFormat(i).height }, { (group, i) -> group.getTrackFormat(i).bitrate })
            )
            ?.let { (group, i) -> TrackSelectionOverride(group.mediaTrackGroup, i) }

    /**
     * Vigilante de "se oye pero no se ve": el sonido avanzó [NO_PICTURE_MS] con una pista de
     * video elegida y una pantalla donde dibujar, y no llegó ningún cuadro. Escala: reiniciar la
     * pista → otra pista de video si la hay → decodificador por software (y sin modo túnel).
     * Al empezar basta [NO_PICTURE_FAST_MS]; tras un primer arreglo se da el margen normal para
     * que el decodificador nuevo se enganche.
     */
    private fun checkPicture() {
        if (firstFrameShown || currentItem == null || pictureFixes >= MAX_PICTURE_FIXES) return
        if (!player.isPlaying || _error.value != null || reconnectPending) {
            pictureWaitFromMs = C.TIME_UNSET
            return
        }
        val surface = player.surfaceSize
        if (surface.width <= 0 || surface.height <= 0) return
        if (!player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) return
        val position = player.currentPosition
        val from = pictureWaitFromMs
        if (from == C.TIME_UNSET || position < from) {
            pictureWaitFromMs = position
            return
        }
        val fast = pictureFixes == 0 && SystemClock.elapsedRealtime() < fastPictureUntil
        val limitMs = if (fast) NO_PICTURE_FAST_MS else NO_PICTURE_MS
        if (position - from < limitMs) return
        fixPicture()
    }

    private fun fixPicture() {
        pictureFixes++
        startRestartDone = true
        proactiveRestartPending = false
        val video = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val other = if (pictureFixes == 2) realVideoTrack(video, exclude = video.firstOrNull { it.isSelected }) else null
        if (pictureFixes >= 2 && other == null) useSoftwareVideo()
        restartVideo(other)
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

    /**
     * Techo del búfer según el heap real de la app (el búfer vive ahí): ~30 s de 4K en un
     * equipo medio.
     */
    private fun targetBufferBytes(): Int {
        val heapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        val mb = when {
            lowRam || heapMb <= 128 -> 48
            heapMb <= 256 -> 96
            else -> 160
        }
        return mb * 1024 * 1024
    }

    /** Techo del búfer de 5 min de película sin descarga al disco. */
    private fun longVodBufferBytes(): Int {
        val heapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        return if (!lowRam && heapMb >= 512) 256 * 1024 * 1024 else targetBufferBytes()
    }

    /**
     * Hasta dónde está la película en disco de corrido desde la posición actual, en ms, para
     * dibujarlo en la barra; [C.TIME_UNSET] si no se está bajando adelantada.
     */
    fun aheadBufferedPositionMs(): Long {
        val url = aheadUrl ?: return C.TIME_UNSET
        if (currentItem == null || currentIsLive) return C.TIME_UNSET
        return ahead.bufferedEndMs(url, player.currentPosition, player.duration)
    }

    // ============================ Recuperación ============================

    private fun handleError(error: PlaybackException) {
        cancelRecovery()
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        if (!currentIsLive && error.hasCause<AheadUnavailableException>()) {
            leaveAhead()
            return
        }
        // Película que el decodificador de video no pudo seguir: un reintento con el de software
        // (el fallback de Media3 solo cubre el arranque del decodificador, no fallas a mitad).
        if (!currentIsLive && !softwareVideo && error is ExoPlaybackException &&
            error.type == ExoPlaybackException.TYPE_RENDERER && MimeTypes.isVideo(error.rendererFormat?.sampleMimeType)
        ) {
            softwareVideo = true
            scheduleRecovery()
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
        if (!isRecoverableVod(error)) {
            showFatal()
            return
        }
        if (isOffline()) {
            waitForNetwork()
            return
        }
        scheduleRecovery()
    }

    /** Sin red no se gastan intentos ni se muestra el error: al volver la red sigue sola. */
    private fun waitForNetwork() {
        vodFailingSince = 0L
        _reconnecting.value = true
    }

    /** Lee el estado de la red (por si se perdió un aviso) y lo publica. */
    private fun isOffline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val offline = cm.activeNetwork == null
        _offline.value = offline
        return offline
    }

    private fun showFatal() {
        _reconnecting.value = false
        _reconnectAttempt.value = 0
        _error.value = if (currentIsLive)
            uiText(R.string.player_error_live)
        else
            uiText(R.string.player_error_vod)
    }

    /** En vivo casi todo se reintenta: 403/404/509 suelen ser límites momentáneos del panel. */
    private fun isRecoverableLive(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> false
        else -> true
    }

    private fun isRecoverableVod(error: PlaybackException): Boolean {
        // 401/404/410: el título no existe o la cuenta no tiene acceso; reintentar no lo arregla.
        if (error.findCause<HttpDataSource.InvalidResponseCodeException>()?.isPermanent() == true) return false
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> false
            // Incluye 403/509 por límite de conexiones del proveedor: se libera al poco rato.
            in 2000..2999 -> true
            PlaybackException.ERROR_CODE_TIMEOUT -> true
            else -> false
        }
    }

    /**
     * Programa el siguiente escalón; el primero es inmediato, luego 1 s, 2 s, 4 s… hasta 15 s.
     * Una película que lleva [VOD_GIVE_UP_MS] fallando con red muestra el error.
     */
    private fun scheduleRecovery() {
        if (reconnectPending || currentItem == null) return
        if (!currentIsLive) {
            val now = SystemClock.elapsedRealtime()
            if (vodFailingSince == 0L) {
                vodFailingSince = now
            } else if (now - vodFailingSince > VOD_GIVE_UP_MS) {
                showFatal()
                return
            }
        }
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
        lastBufferedMs = C.TIME_UNSET
        // La conexión de la descarga adelantada puede estar colgada: se abre otra ya.
        if (!currentIsLive && aheadUrl == currentUrl) ahead.reconnectNow()
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
        if (currentItem == null || _error.value != null || reconnectPending) return
        if (!currentIsLive) {
            checkVodStall()
            return
        }
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

    /**
     * Vigilante de películas y episodios por la red: cargando [VOD_STALL_BUFFERING_MS] sin que
     * avance la posición ni lo cargado → se reconstruye la fuente en el mismo punto. Sin red solo
     * se espera.
     */
    private fun checkVodStall() {
        if (currentUrl.startsWith("file:")) return
        val now = SystemClock.elapsedRealtime()
        val offline = isOffline()
        if (player.playerError != null) {
            // Esperando red tras un error: si la red ya está y el aviso no llegó, se reintenta igual.
            if (_reconnecting.value && !offline) scheduleRecovery()
            return
        }
        if (offline || !player.playWhenReady || player.playbackState != Player.STATE_BUFFERING) {
            lastProgressAt = now
            lastPositionMs = C.TIME_UNSET
            lastBufferedMs = C.TIME_UNSET
            return
        }
        val position = player.currentPosition
        val buffered = player.bufferedPosition
        if (position != lastPositionMs || buffered != lastBufferedMs) {
            lastPositionMs = position
            lastBufferedMs = buffered
            lastProgressAt = now
            return
        }
        if (now - lastProgressAt > VOD_STALL_BUFFERING_MS) scheduleRecovery()
    }

    private fun onNetworkAvailable() {
        val wasOffline = _offline.value
        _offline.value = false
        // El tiempo sin red no cuenta para darse por vencido.
        vodFailingSince = 0L
        if (currentItem == null) return
        if (aheadUrl != null) ahead.onNetworkAvailable()
        when {
            _error.value != null -> retry()
            _reconnecting.value -> {
                cancelRecovery()
                reconnect.run()
            }
            // Película trabada cargando durante el corte: se retoma ya en vez de esperar el próximo reintento.
            wasOffline && !currentIsLive && aheadUrl == null && player.playWhenReady &&
                player.playbackState == Player.STATE_BUFFERING -> rebuildVodSource()
        }
    }

    /** Vuelve a armar la fuente de la película en la posición actual. */
    private fun rebuildVodSource() {
        val item = currentItem ?: return
        player.setMediaSource(sourceFor(item, currentUrl), player.currentPosition.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    /** La descarga adelantada no sirve para este título: sigue por el camino normal desde el mismo punto. */
    private fun leaveAhead() {
        aheadUrl = null
        rebuildVodSource()
    }

    /** Reintento manual desde la pantalla, después de agotar los automáticos. */
    fun retry() {
        cancelRecovery()
        vodFailingSince = 0L
        _error.value = null
        _reconnecting.value = true
        reconnectAttempts = 1
        _reconnectAttempt.value = 1
        recover()
    }

    /**
     * Bajar la película entera al disco: solo por http, archivo único (no listas HLS/DASH), con
     * red no medida y espacio libre. Se decide al empezar el título para no cambiar de camino a mitad.
     */
    private fun aheadAllowed(url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        if (path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd")) return false
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        if (cm.activeNetwork == null || cm.isActiveNetworkMetered) return false
        return runCatching { context.noBackupFilesDir.usableSpace >= AHEAD_MIN_FREE_BYTES }.getOrDefault(false)
    }

    private fun sourceFor(item: MediaItem, url: String): MediaSource {
        // Nunca dos conexiones al mismo proveedor: fuera del camino adelantado, su descarga se corta.
        val useAhead = !currentIsLive && url == aheadUrl
        if (useAhead) ahead.attach(url) else ahead.stop()
        vodLongBuffer = !currentIsLive && !useAhead && !url.startsWith("file:")
        if (url.startsWith("file:")) return localSourceFactory.createMediaSource(item)
        if (useAhead) return aheadSourceFactory.createMediaSource(item)
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
        isLive: Boolean = false,
        contentKey: String = ""
    ) {
        // El mismo canal que ya suena (del mini reproductor a pantalla completa): no se recarga.
        val target = if (isLive) liveUrl(url) else url
        if (isLive && currentIsLive && currentItem != null && target == currentUrl &&
            player.playerError == null && player.playbackState != Player.STATE_IDLE
        ) {
            _currentKey.value = contentKey.ifEmpty { null }
            player.playWhenReady = true
            return
        }
        cancelPreload(PRELOAD_RELEASE_WAIT_MS)
        cancelRecovery()
        reconnectAttempts = 0
        _reconnectAttempt.value = 0
        _reconnecting.value = false
        _error.value = null
        currentIsLive = isLive
        videoRestartToken++
        resetPictureWait()
        pictureFixes = 0
        coverArtChecked = false
        softwareVideo = false
        manualRepairs = 0
        decoderBeforeSurface = false
        startRestartDone = false
        proactiveRestartPending = false
        fixMemoryChecked = false
        videoFixKey = null
        repairKey = null
        mainHandler.removeCallbacks(rememberRepair)
        val url = target
        currentUrl = url
        _currentKey.value = contentKey.ifEmpty { null }
        _vodActive.value = !isLive && !url.startsWith("file:")
        aheadUrl = if (!isLive && aheadAllowed(url)) url else null
        ahead.durationMs = C.TIME_UNSET
        vodFailingSince = 0L
        lastPositionMs = C.TIME_UNSET
        lastBufferedMs = C.TIME_UNSET
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
        applyVideoOutput()
        player.setPlaybackSpeed(1f)
        if (isLive) player.setMediaSource(sourceFor(item, url))
        else player.setMediaSource(sourceFor(item, url), startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
        mainHandler.removeCallbacks(watchdog)
        mainHandler.postDelayed(watchdog, WATCHDOG_TICK_MS)
    }

    // ============================ Precarga ============================

    /**
     * Desde la ficha: baja a la caché de video los primeros [PRELOAD_BYTES] de la película o
     * episodio (unos segundos) y deja la conexión abierta, así "Reproducir" arranca antes.
     * No se precarga si algo está sonando (muchos proveedores permiten una sola conexión),
     * con datos móviles o en equipos con poca memoria. Nunca canales en vivo.
     */
    fun preload(url: String) {
        if (currentItem != null || !url.startsWith("http", ignoreCase = true)) return
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null || cm.activeNetwork == null || cm.isActiveNetworkMetered) return
        if (lowRam) return
        cancelPreload()
        val spec = DataSpec.Builder().setUri(url).setPosition(0).setLength(PRELOAD_BYTES).build()
        val generation = ++preloadGeneration
        preloadTask = preloadExecutor.submit(Runnable {
            if (generation != preloadGeneration) return@Runnable
            // La caché se abre aquí y no en el hilo principal.
            val writer = CacheWriter(preloadDataFactory.createDataSource(), spec, null, null)
            preloadWriter = writer
            if (generation != preloadGeneration) return@Runnable
            runCatching { writer.cache() }
        })
    }

    /**
     * Corta la precarga: al reproducir (libera la conexión) o al salir de la ficha. Sin
     * interrumpir el hilo: se cierra la conexión y la escritura termina sola, soltando la
     * caché. Con [waitMs] espera ese tope a que suelte la caché y la línea del proveedor.
     */
    fun cancelPreload(waitMs: Long = 0L) {
        val task = preloadTask ?: return
        preloadTask = null
        preloadGeneration++
        preloadWriter?.cancel()
        preloadWriter = null
        preloadClient.dispatcher.cancelAll()
        if (waitMs > 0 && !task.isDone) runCatching { task.get(waitMs, TimeUnit.MILLISECONDS) }
    }

    fun stop() {
        cancelRecovery()
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(fastPicture)
        mainHandler.removeCallbacks(rememberRepair)
        currentItem = null
        _currentKey.value = null
        _vodActive.value = false
        _reconnecting.value = false
        _reconnectAttempt.value = 0
        _error.value = null
        player.stop()
        player.clearMediaItems()
        // Corta la conexión; lo bajado queda para volver a entrar al mismo título.
        aheadUrl = null
        ahead.stop()
        vodLongBuffer = false
    }

    fun release() {
        cancelPreload()
        preloadExecutor.shutdownNow()
        cancelRecovery()
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(fastPicture)
        mainHandler.removeCallbacks(rememberRepair)
        ahead.release()
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        player.release()
        cache.release()
    }
}
