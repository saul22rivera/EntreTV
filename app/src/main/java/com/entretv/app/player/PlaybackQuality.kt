package com.entretv.app.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Cifras de calidad del contenido actual: cuánto tardó la primera imagen (inicio o zapping),
 * trabones y porcentaje del tiempo cargando, cuadros perdidos, cambios de calidad y ancho de
 * banda medido. Se reinicia con [start] en cada contenido.
 *
 * Un trabón es quedarse cargando después de haber mostrado imagen, sin que sea por un salto
 * pedido por el usuario. Los avisos llegan en el hilo principal.
 */
@OptIn(UnstableApi::class)
class PlaybackQuality : AnalyticsListener {

    data class Snapshot(
        /** Del pedido a la primera imagen; null si todavía no hubo imagen. */
        val startupMs: Long?,
        val stalls: Int,
        /** Tiempo cargando sobre el tiempo desde la primera imagen; null sin imagen aún. */
        val stallPercent: Float?,
        val droppedFrames: Int,
        val qualitySwitches: Int,
        /** Ancho de banda estimado por el reproductor, en bits por segundo. */
        val bandwidthBps: Long?
    )

    /** Se llama al empezar cada trabón. */
    var onStall: (() -> Unit)? = null

    private var startedAt = 0L
    private var firstFrameAt = 0L
    private var startupMs = C.TIME_UNSET
    private var stalls = 0
    private var stallTotalMs = 0L
    private var stallFrom = 0L
    private var seeking = false
    private var droppedFrames = 0
    private var qualitySwitches = 0
    private var lastVideoHeight = 0
    private var lastVideoBitrate = Format.NO_VALUE
    private var bandwidthBps = 0L

    /** Contenido nuevo: borra las cifras del anterior. */
    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        firstFrameAt = 0L
        startupMs = C.TIME_UNSET
        stalls = 0
        stallTotalMs = 0L
        stallFrom = 0L
        seeking = false
        droppedFrames = 0
        qualitySwitches = 0
        lastVideoHeight = 0
        lastVideoBitrate = Format.NO_VALUE
    }

    fun snapshot(): Snapshot {
        val now = SystemClock.elapsedRealtime()
        val stalled = stallTotalMs + if (stallFrom > 0L) now - stallFrom else 0L
        val watched = if (firstFrameAt > 0L) now - firstFrameAt else 0L
        return Snapshot(
            startupMs = startupMs.takeIf { it != C.TIME_UNSET },
            stalls = stalls,
            stallPercent = if (watched > 0L) (stalled * 100f / watched).coerceAtMost(100f) else null,
            droppedFrames = droppedFrames,
            qualitySwitches = qualitySwitches,
            bandwidthBps = bandwidthBps.takeIf { it > 0L }
        )
    }

    /** Resumen de una línea para el diagnóstico; nunca lleva URLs. */
    fun summary(): String {
        val s = snapshot()
        return "qoe startup=${s.startupMs ?: -1}ms stalls=${s.stalls} stall%=${"%.2f".format(s.stallPercent ?: 0f)} " +
            "dropped=${s.droppedFrames} switches=${s.qualitySwitches} bw=${(s.bandwidthBps ?: 0L) / 1000}kbps"
    }

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        if (startupMs != C.TIME_UNSET || startedAt == 0L) return
        firstFrameAt = eventTime.realtimeMs
        startupMs = (firstFrameAt - startedAt).coerceAtLeast(0L)
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        val now = eventTime.realtimeMs
        if (state == Player.STATE_BUFFERING) {
            if (firstFrameAt > 0L && !seeking && stallFrom == 0L) {
                stallFrom = now
                stalls++
                onStall?.invoke()
            }
            return
        }
        if (stallFrom > 0L) {
            stallTotalMs += now - stallFrom
            stallFrom = 0L
        }
        if (state == Player.STATE_READY) seeking = false
    }

    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) seeking = true
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        this.droppedFrames += droppedFrames
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        if (format.isCoverArt()) return
        val changed = lastVideoHeight > 0 && (format.height != lastVideoHeight || format.bitrate != lastVideoBitrate)
        if (changed) qualitySwitches++
        lastVideoHeight = format.height
        lastVideoBitrate = format.bitrate
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long
    ) {
        bandwidthBps = bitrateEstimate
    }
}
