package com.entretv.app.ui.player

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Window
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlin.math.abs
import kotlin.math.roundToInt

/** Error relativo de cadencia que se considera exacto (23,976 en 23,976 Hz). */
private const val EXACT_CADENCE = 0.0005f

/** Error aceptable: 23,976 en 24 Hz (un cuadro repetido cada ~40 s) no se nota. */
private const val NEAR_CADENCE = 0.0015f

private const val MIN_FPS = 10f
private const val MAX_FPS = 121f

/**
 * Ajusta la frecuencia de la TV a la del video mientras se ve una película o un episodio:
 * 23,976 cps → 23,976 / 47,95 / 119,88 Hz; 25 → 50 Hz… Sin esto, una película de cine en una
 * pantalla a 60 Hz avanza a saltitos (cada cuadro dura 2 o 3 refrescos).
 *
 *  - Se decide una sola vez por contenido, con el primer formato de video que trae sus cuadros
 *    por segundo (llega antes del primer cuadro, así que el cambio ocurre al arrancar); si no
 *    los trae, se miden en los primeros ~3 s. Si no dan una cifra estándar
 *    (23,976, 24, 25, 29,97, 30, 48, 50, 59,94 o 60), no se toca nada.
 *  - Lo medido no se aplica mientras [hold] (controles, panel o listas abiertas): la TV se
 *    queda en negro un momento al cambiar y en medio de un menú parece un cierre.
 *  - Solo modos con la misma resolución que la actual; si la actual ya sirve, no se cambia.
 *    Con la misma resolución la configuración no cambia y la actividad no se reinicia.
 *  - Al salir se devuelve la preferencia que tenía la ventana.
 */
@OptIn(UnstableApi::class)
@Composable
fun FrameRateMatchEffect(player: ExoPlayer, activity: Activity?, enabled: Boolean, contentKey: String?, hold: Boolean) {
    val key by rememberUpdatedState(contentKey)
    val holding by rememberUpdatedState(hold)
    val estimator = remember { FrameRateEstimator() }
    val pending = remember { PendingMatch() }
    LaunchedEffect(contentKey) {
        estimator.restart()
        pending.clear()
    }
    LaunchedEffect(hold, contentKey) {
        if (!hold) pending.flush(contentKey)
    }
    DisposableEffect(player, activity, enabled) {
        if (!enabled || activity == null) return@DisposableEffect onDispose {}
        val switcher = DisplayModeSwitcher(activity.window, displayOf(activity))
        pending.switcher = switcher
        val mainHandler = Handler(Looper.getMainLooper())
        fun match(fps: Float, measured: Boolean) {
            val content = key ?: return
            if (fps <= 0f) return
            if (measured && holding) pending.set(content, fps) else switcher.match(content, fps)
        }
        val formatListener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) = match(format.frameRate, measured = false)
        }
        // Muchos MKV y TS no declaran sus cuadros por segundo: se miden en los primeros cuadros.
        val frameListener = VideoFrameMetadataListener { presentationTimeUs, _, format, _ ->
            if (format.frameRate > 0f) return@VideoFrameMetadataListener
            estimator.add(presentationTimeUs)?.let { fps -> mainHandler.post { match(fps, measured = true) } }
        }
        player.addAnalyticsListener(formatListener)
        player.setVideoFrameMetadataListener(frameListener)
        player.videoFormat?.let { match(it.frameRate, measured = false) }
        onDispose {
            player.removeAnalyticsListener(formatListener)
            player.clearVideoFrameMetadataListener(frameListener)
            mainHandler.removeCallbacksAndMessages(null)
            pending.switcher = null
            pending.clear()
            switcher.restore()
        }
    }
}

/** Frecuencia medida que espera a que se cierren los menús. Solo hilo principal. */
private class PendingMatch {
    var switcher: DisplayModeSwitcher? = null
    private var content: String? = null
    private var fps = 0f

    fun set(content: String, fps: Float) {
        this.content = content
        this.fps = fps
    }

    fun clear() {
        content = null
        fps = 0f
    }

    fun flush(current: String?) {
        val target = content
        val rate = fps
        clear()
        if (target == null || target != current || rate <= 0f) return
        switcher?.match(target, rate)
    }
}

/**
 * Mide los cuadros por segundo con las marcas de tiempo de los cuadros que se muestran.
 * Un salto (búsqueda, corte) vuelve a empezar; los cuadros descartados cuentan por su hueco.
 * Lo llama el hilo de reproducción; [restart] llega desde el principal.
 */
private class FrameRateEstimator {
    private val deltas = ArrayList<Long>(SAMPLE_FRAMES)
    private var firstUs = C.TIME_UNSET
    private var lastUs = C.TIME_UNSET
    private var done = false

    @Volatile
    private var restartRequested = false

    fun restart() {
        restartRequested = true
    }

    fun add(presentationTimeUs: Long): Float? {
        if (restartRequested) {
            restartRequested = false
            done = false
            reset()
        }
        if (done) return null
        if (lastUs != C.TIME_UNSET) {
            val delta = presentationTimeUs - lastUs
            if (delta <= 0 || delta > MAX_FRAME_GAP_US) reset() else deltas.add(delta)
        }
        if (firstUs == C.TIME_UNSET) firstUs = presentationTimeUs
        lastUs = presentationTimeUs
        if (deltas.size < SAMPLE_FRAMES) return null
        val base = deltas.sorted()[deltas.size / 2].toFloat()
        val frames = deltas.sumOf { (it / base).roundToInt().coerceAtLeast(1) }
        val fps = frames * 1_000_000f / (lastUs - firstUs)
        reset()
        val standard = STANDARD_FPS.minBy { abs(it - fps) / it }
        if (abs(standard - fps) / standard > SNAP_TOLERANCE) return null
        done = true
        return standard
    }

    private fun reset() {
        deltas.clear()
        firstUs = C.TIME_UNSET
        lastUs = C.TIME_UNSET
    }

    private companion object {
        /** ~3 s a 24 cps: con marcas redondeadas al milisegundo (MKV) distingue 23,976 de 24. */
        const val SAMPLE_FRAMES = 72
        const val MAX_FRAME_GAP_US = 250_000L
        const val SNAP_TOLERANCE = 0.005f
        val STANDARD_FPS = floatArrayOf(
            24_000f / 1_001f, 24f, 25f, 30_000f / 1_001f, 30f,
            48_000f / 1_001f, 48f, 50f, 60_000f / 1_001f, 60f
        )
    }
}

@Suppress("DEPRECATION")
private fun displayOf(activity: Activity): Display? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay
}.getOrNull()

private class DisplayModeSwitcher(private val window: Window, private val display: Display?) {
    private val originalModeId = window.attributes.preferredDisplayModeId
    private val originalMode: Display.Mode? = runCatching { display?.mode }.getOrNull()
    private var matchedKey: String? = null
    private var changed = false

    fun match(contentKey: String, fps: Float) {
        if (contentKey == matchedKey) return
        matchedKey = contentKey
        val d = display ?: return
        val target = runCatching { pickDisplayMode(d.mode, d.supportedModes, fps, originalMode) }.getOrNull() ?: return
        if (window.attributes.preferredDisplayModeId == target.modeId) return
        runCatching {
            window.attributes = window.attributes.apply { preferredDisplayModeId = target.modeId }
            changed = true
        }
    }

    fun restore() {
        if (!changed) return
        changed = false
        runCatching { window.attributes = window.attributes.apply { preferredDisplayModeId = originalModeId } }
    }
}

/**
 * Modo de la misma resolución cuya frecuencia es múltiplo entero de [fps]; null si el actual
 * ya sirve o ninguno sirve. Primero los exactos, luego el modo de antes, luego el múltiplo
 * más bajo (24 Hz antes que 120 Hz).
 */
private fun pickDisplayMode(
    current: Display.Mode,
    modes: Array<Display.Mode>,
    fps: Float,
    original: Display.Mode?
): Display.Mode? {
    if (fps < MIN_FPS || fps > MAX_FPS) return null
    if (cadenceError(current.refreshRate, fps) <= NEAR_CADENCE) return null
    return modes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .map { it to cadenceError(it.refreshRate, fps) }
        .filter { it.second <= NEAR_CADENCE }
        .minWithOrNull(
            compareBy(
                { it.second > EXACT_CADENCE },
                { it.first.modeId != original?.modeId },
                { (it.first.refreshRate / fps).roundToInt() }
            )
        )
        ?.first
}

/** Cuánto se aleja [refreshRate] de un múltiplo entero de [fps], relativo a [fps]. */
private fun cadenceError(refreshRate: Float, fps: Float): Float {
    val multiple = (refreshRate / fps).roundToInt()
    if (multiple < 1) return Float.MAX_VALUE
    return abs(refreshRate / multiple - fps) / fps
}
