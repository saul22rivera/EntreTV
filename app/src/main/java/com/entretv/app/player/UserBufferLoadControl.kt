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
 *  - búfer de arranque: segundos que se juntan antes de empezar o tras un trabón. En vivo y en
 *    una película la imagen entra en cuanto hay un instante (tope 0,6 s en vivo y 0,5 s en VOD).
 *    Tras un trabón, el VOD pide 1,5 veces el búfer elegido (no se vuelve a cortar enseguida).
 *  - búfer máximo: hasta cuántos segundos por delante se descarga (15, 30 o 60). En VOD se
 *    guarda el doble y se mantiene lleno; en vivo el borde del directo lo limita igual.
 *
 * El búfer además tiene techo en bytes: en 4K, 30 s pueden pasar de 150 MB y en equipos
 * con poca memoria eso provoca tirones del recolector o cierres por falta de RAM. En esos
 * equipos ([lowRam]) VOD no guarda el doble y el máximo no pasa de 50 s.
 *
 * Película o episodio por la red ([longVodBuffer]): se guarda por delante en memoria hasta 5 min,
 * con techo [longVodBufferBytes] para que un 4K no llene el heap. El tiempo manda sobre ese techo
 * en los primeros segundos, así un bitrate alto no corta la carga antes de tener video.
 *
 * Las descargas (archivos locales) usan los mismos valores que la red.
 *
 * Película con internet justo ([vodTargetUs]): antes de empezar o de seguir tras un trabón se junta
 * lo que pide el reproductor (precarga según velocidad y escalera de trabones). Nunca más de lo que
 * entra en memoria: con el techo lleno arranca igual, si no se quedaría esperando para siempre.
 */
@OptIn(UnstableApi::class)
class UserBufferLoadControl private constructor(
    private val bufferAllocator: DefaultAllocator,
    private val targetBufferBytes: Int,
    private val longVodBufferBytes: Int,
    private val lowRam: Boolean,
    private val startBufferMs: () -> Int,
    private val maxBufferMs: () -> Int,
    private val isLive: () -> Boolean,
    private val longVodBuffer: () -> Boolean,
    private val vodTargetUs: () -> Long
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
    /* prioritizeTimeOverSizeThresholds = */ !lowRam,
    /* prioritizeTimeOverSizeThresholdsForLocalPlayback = */ !lowRam,
    DEFAULT_BACK_BUFFER_DURATION_MS,
    DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME
) {
    constructor(
        targetBufferBytes: Int,
        longVodBufferBytes: Int,
        lowRam: Boolean,
        startBufferMs: () -> Int,
        maxBufferMs: () -> Int,
        isLive: () -> Boolean,
        longVodBuffer: () -> Boolean,
        vodTargetUs: () -> Long
    ) : this(
        DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), targetBufferBytes, longVodBufferBytes, lowRam,
        startBufferMs, maxBufferMs, isLive, longVodBuffer, vodTargetUs
    )

    /** Lo que se espera juntar antes de arrancar o seguir (la última decisión); 0 si nada. */
    @Volatile
    var neededUs: Long = 0L
        private set

    private companion object {
        const val MIN_BUFFER_MS = 15_000
        const val MAX_USER_BUFFER_MS = 60_000
        const val LOW_RAM_MAX_BUFFER_MS = 50_000
        const val MAX_BUFFER_MS = 2 * MAX_USER_BUFFER_MS
        const val LONG_VOD_BUFFER_MS = 300_000L

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
            // El vivo arranca en cuanto hay un instante de imagen (tope 0,6 s). El búfer
            // elegido sigue valiendo para aguantar un corte, no para retrasar el zap.
            live -> if (userUs == 0L) 0L else min(userUs / 2, 600_000L)
            // Igual la película: no se esperan los 3 o 9 s de Configuración para el primer
            // cuadro. Esos segundos se siguen guardando por delante y tras un corte.
            else -> if (userUs == 0L) 0L else min(userUs, 500_000L)
        }
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) {
            neededUs = min(parameters.targetLiveOffsetUs / 2, neededUs)
        }
        if (!live) neededUs = max(neededUs, vodTargetUs())
        this.neededUs = neededUs
        if (!live && (bufferAllocator.totalBytesAllocated >= vodBytesCap() * 9L / 10 || bufferedUs >= vodTimeCapUs())) return true
        return neededUs <= 0 || bufferedUs >= neededUs
    }

    private fun vodBytesCap(): Int = if (!lowRam && longVodBuffer()) longVodBufferBytes else targetBufferBytes

    /** Donde [shouldContinueLoading] deja de cargar una película. */
    private fun vodTimeCapUs(): Long {
        if (!lowRam && longVodBuffer()) return LONG_VOD_BUFFER_MS * 1_000L
        val userCapMs = maxBufferMs().coerceIn(MIN_BUFFER_MS, if (lowRam) LOW_RAM_MAX_BUFFER_MS else MAX_USER_BUFFER_MS)
        return userCapMs * (if (lowRam) 1_000L else 2_000L)
    }

    /** Corta la descarga al llegar al búfer máximo; el resto (memoria) lo decide la base. */
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed
        )
        val userCapMs = maxBufferMs().coerceIn(MIN_BUFFER_MS, if (lowRam) LOW_RAM_MAX_BUFFER_MS else MAX_USER_BUFFER_MS)
        if (isLive()) {
            if (bufferedUs >= userCapMs * 1_000L) return false
            return super.shouldContinueLoading(parameters)
        }
        if (!lowRam && longVodBuffer()) {
            if (bufferedUs >= LONG_VOD_BUFFER_MS * 1_000L) return false
            if (bufferedUs < MIN_BUFFER_MS * 1_000L) return super.shouldContinueLoading(parameters)
            return bufferAllocator.totalBytesAllocated < longVodBufferBytes
        }
        if (bufferedUs >= userCapMs * (if (lowRam) 1_000L else 2_000L)) return false
        if (bufferedUs < MIN_BUFFER_MS * 1_000L) return super.shouldContinueLoading(parameters)
        // Entre 15 s y el tope sigue cargando mientras la memoria alcance: el búfer no baja a 15 s
        // antes de volver a pedir datos.
        return bufferAllocator.totalBytesAllocated < targetBufferBytes
    }
}
