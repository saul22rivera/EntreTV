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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
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
import kotlinx.coroutines.flow.StateFlow
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
        private const val LOW_RAM_CACHE_BYTES = 96L * 1024 * 1024
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
        /** ~3 s de una película 1080p típica: alcanza para la cabecera y el primer cuadro. */
        private const val PRELOAD_BYTES = 2L * 1024 * 1024
        /** Lo que "Reproducir" espera a que la precarga suelte la conexión y la caché. */
        private const val PRELOAD_RELEASE_WAIT_MS = 250L
        /** Sonido avanzando sin ningún cuadro de video durante esto = imagen trabada. */
        private const val NO_PICTURE_MS = 3_000L
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

    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "entre_media"),
            // Los equipos con poca memoria suelen traer poco almacenamiento.
            LeastRecentlyUsedCacheEvictor(if (lowRam) LOW_RAM_CACHE_BYTES else CACHE_BYTES),
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

    private val vodDataFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val errorPolicy = DefaultLoadErrorHandlingPolicy(LOAD_RETRIES)

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
    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error

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
    private var lastProgressAt = 0L
    private var recoveredAt = 0L

    // Vigilante de imagen, por contenido.
    private var firstFrameShown = false
    private var pictureWaitFromMs = C.TIME_UNSET
    private var pictureFixes = 0
    private var coverArtChecked = false
    /** Cuenta los reinicios de video: uno pendiente de otro contenido no se aplica. */
    private var videoRestartToken = 0

    // Lo lee el hilo de reproducción al crear cada decodificador.
    @Volatile
    private var softwareVideo = false

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
        val loadControl = UserBufferLoadControl(targetBufferBytes(), lowRam, { startBufferMs }, { maxBufferMs }, { currentIsLive })

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
                skipCoverArt(tracks)
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                _trackParams.value = parameters
            }

            override fun onRenderedFirstFrame() {
                firstFrameShown = true
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) pictureWaitFromMs = C.TIME_UNSET
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _speed.value = playbackParameters.speed
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
        if (position - from < NO_PICTURE_MS) return
        fixPicture()
    }

    private fun fixPicture() {
        pictureFixes++
        val video = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val other = if (pictureFixes == 2) realVideoTrack(video, exclude = video.firstOrNull { it.isSelected }) else null
        if (pictureFixes >= 2 && other == null) {
            softwareVideo = true
            if (trackSelector.parameters.tunnelingEnabled) {
                trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(false))
            }
        }
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

    // ============================ Recuperación ============================

    private fun handleError(error: PlaybackException) {
        cancelRecovery()
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
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
        val url = target
        currentUrl = url
        _currentKey.value = contentKey.ifEmpty { null }
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
        currentItem = null
        _currentKey.value = null
        _reconnecting.value = false
        _reconnectAttempt.value = 0
        _error.value = null
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        cancelPreload()
        preloadExecutor.shutdownNow()
        cancelRecovery()
        mainHandler.removeCallbacks(watchdog)
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        player.release()
        cache.release()
    }
}
