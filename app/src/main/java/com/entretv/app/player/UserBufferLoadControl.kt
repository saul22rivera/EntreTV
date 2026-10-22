package com.entretv.app.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import kotlin.math.max
import kotlin.math.min

/**
 * Como [DefaultLoadControl], pero los dos límites salen de Configuración y se leen en
 * cada decisión, ajustados según sea en vivo o película/episodio:
 *  - búfer de arranque: segundos que se juntan antes de empezar o tras un trabón. En vivo se
 *    usa la mitad (entra antes al canal); en VOD tras un trabón se pide 1,5 veces (no se
 *    vuelve a cortar enseguida).
 *  - búfer máximo: hasta cuántos segundos por delante se descarga (15, 30 o 60). En VOD se
 *    guarda el doble y se mantiene lleno; en vivo el borde del directo lo limita igual.
 *
 * El búfer además tiene techo en bytes: en 4K, 30 s pueden pasar de 150 MB y en equipos
 * con poca memoria eso provoca tirones del recolector o cierres por falta de RAM.
 *
 * Las descargas (archivos locales) usan los mismos valores que la red.
 */
@OptIn(UnstableApi::class)
class UserBufferLoadControl private constructor(
    private val bufferAllocator: DefaultAllocator,
    private val targetBufferBytes: Int,
    private val startBufferMs: () -> Int,
    private val maxBufferMs: () -> Int,
    private val isLive: () -> Boolean
) : DefaultLoadControl(
    bufferAllocator,
    /* minBufferMs = */ MIN_BUFFER_MS,
    /* minBufferForLocalPlaybackMs = */ MIN_BUFFER_MS,
    /* maxBufferMs = */ MAX_BUFFER_MS,
    /* maxBufferForLocalPlaybackMs = */ MAX_BUFFER_MS,
    /* bufferForPlaybackMs = */ 2_500,
    /* bufferForPlaybackForLocalPlaybackMs = */ 2_500,
    /* bufferForPlaybackAfterRebufferMs = */ 5_000,
    /* bufferForPlaybackAfterRebufferForLocalPlaybackMs = */ 5_000,
    targetBufferBytes,
    /* prioritizeTimeOverSizeThresholds = */ false,
    /* prioritizeTimeOverSizeThresholdsForLocalPlayback = */ false,
    DEFAULT_BACK_BUFFER_DURATION_MS,
    DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME
) {
    constructor(
        targetBufferBytes: Int,
        startBufferMs: () -> Int,
        maxBufferMs: () -> Int,
        isLive: () -> Boolean
    ) : this(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), targetBufferBytes, startBufferMs, maxBufferMs, isLive)

    private companion object {
        const val MIN_BUFFER_MS = 15_000
        const val MAX_USER_BUFFER_MS = 60_000
        const val MAX_BUFFER_MS = 2 * MAX_USER_BUFFER_MS

        // Con 0 s, tras un trabón se exige 1 s para no entrar en un ciclo de cortes.
        const val MIN_AFTER_REBUFFER_US = 1_000_000L
        const val MAX_VOD_AFTER_REBUFFER_US = 10_000_000L
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed
        )
        val userUs = startBufferMs() * 1_000L
        val live = isLive()
        var neededUs = when {
            parameters.rebuffering && live -> max(userUs, MIN_AFTER_REBUFFER_US)
            parameters.rebuffering -> (userUs * 3 / 2).coerceIn(MIN_AFTER_REBUFFER_US, MAX_VOD_AFTER_REBUFFER_US)
            live -> userUs / 2
            else -> userUs
        }
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) {
            neededUs = min(parameters.targetLiveOffsetUs / 2, neededUs)
        }
        return neededUs <= 0 || bufferedUs >= neededUs
    }

    /** Corta la descarga al llegar al búfer máximo; el resto (memoria) lo decide la base. */
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed
        )
        val userCapMs = maxBufferMs().coerceIn(MIN_BUFFER_MS, MAX_USER_BUFFER_MS)
        if (isLive()) {
            if (bufferedUs >= userCapMs * 1_000L) return false
            return super.shouldContinueLoading(parameters)
        }
        if (bufferedUs >= userCapMs * 2_000L) return false
        if (bufferedUs < MIN_BUFFER_MS * 1_000L) return super.shouldContinueLoading(parameters)
        // Entre 15 s y el tope sigue cargando mientras la memoria alcance: el búfer no baja a 15 s
        // antes de volver a pedir datos.
        return bufferAllocator.totalBytesAllocated < targetBufferBytes
    }
}
