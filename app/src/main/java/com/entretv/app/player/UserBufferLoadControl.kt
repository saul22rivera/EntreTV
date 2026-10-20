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
 * cada decisión:
 *  - búfer de arranque: segundos que se juntan antes de empezar o tras un trabón;
 *  - búfer máximo: hasta cuántos segundos por delante se descarga (15, 30 o 60).
 *
 * El búfer además tiene techo en bytes: en 4K, 30 s pueden pasar de 150 MB y en equipos
 * con poca memoria eso provoca tirones del recolector o cierres por falta de RAM.
 */
@OptIn(UnstableApi::class)
class UserBufferLoadControl(
    targetBufferBytes: Int,
    private val startBufferMs: () -> Int,
    private val maxBufferMs: () -> Int
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    /* minBufferMs = */ MIN_BUFFER_MS,
    /* maxBufferMs = */ MAX_BUFFER_MS,
    /* bufferForPlaybackMs = */ 2_500,
    /* bufferForPlaybackAfterRebufferMs = */ 5_000,
    targetBufferBytes,
    /* prioritizeTimeOverSizeThresholds = */ false,
    DEFAULT_BACK_BUFFER_DURATION_MS,
    DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME
) {
    private companion object {
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 60_000

        // Con 0 s, tras un trabón se exige 1 s para no entrar en un ciclo de cortes.
        const val MIN_AFTER_REBUFFER_US = 1_000_000L
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed
        )
        var neededUs = startBufferMs() * 1_000L
        if (parameters.rebuffering) neededUs = max(neededUs, MIN_AFTER_REBUFFER_US)
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) {
            neededUs = min(parameters.targetLiveOffsetUs / 2, neededUs)
        }
        return neededUs <= 0 || bufferedUs >= neededUs
    }

    /** Corta la descarga al llegar al búfer máximo elegido; el resto (memoria) lo decide la base. */
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed
        )
        val capUs = maxBufferMs().coerceIn(MIN_BUFFER_MS, MAX_BUFFER_MS) * 1_000L
        if (bufferedUs >= capUs) return false
        return super.shouldContinueLoading(parameters)
    }
}
