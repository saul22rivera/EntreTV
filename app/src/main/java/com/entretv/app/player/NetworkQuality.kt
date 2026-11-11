package com.entretv.app.player

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** Aviso discreto de internet; solo existe mientras el video está detenido cargando. */
data class NetworkHint(
    val kind: Kind,
    /** Velocidad que está llegando, en Mbps; null si todavía no se pudo medir. */
    val mbps: Float?,
    /** Lo que pide el video, en Mbps; null si no se sabe. */
    val needMbps: Float?,
    /** Cuánto falta para seguir (0..99); null si no se sabe. */
    val percent: Int?,
    /** Precarga larga para que el resto se vea sin cortes (internet más lento que el video). */
    val preparing: Boolean = false
) {
    enum class Kind { SLOW, UNSTABLE }
}

/**
 * Mide lo que de verdad llega de la red al reproductor (bytes por segundo de los últimos
 * [WINDOW_MS]) y decide cuándo mostrar [NetworkHint]:
 *  - solo con el video detenido cargando: mientras se ve bien nunca hay aviso;
 *  - si la carga pasa de [SHOW_AFTER_MS] o es el segundo trabón en [STALL_WINDOW_MS];
 *  - una vez cada [COOLDOWN_MS] por título o canal, salvo esperas largas (precarga de un 4K),
 *    que siempre muestran cuánto falta.
 *
 * La lectura de bytes llega desde los hilos de OkHttp; el resto, desde el hilo principal.
 */
class NetworkQuality {

    private val totalBytes = AtomicLong()
    @Volatile
    private var lastDataAt = 0L
    /** Tamaño del archivo que se está viendo, según la última respuesta; -1 si no se sabe. */
    @Volatile
    var contentBytes: Long = -1L
        private set

    private val samples = ArrayDeque<LongArray>()
    private val stallTimes = ArrayDeque<Long>()
    private val shownAt = HashMap<String, Long>()
    private var bufferingSince = 0L
    private var episodeAllowed: Boolean? = null

    /** Para el cliente de video: cuenta cada byte que llega y anota el tamaño del archivo. */
    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        totalOf(response)?.let { contentBytes = it }
        val body = response.body ?: return@Interceptor response
        response.newBuilder().body(CountingBody(body)).build()
    }

    /** Contenido nuevo: borra trabones, tamaño y la espera en curso. */
    fun startContent() {
        contentBytes = -1L
        stallTimes.clear()
        bufferingSince = 0L
        episodeAllowed = null
    }

    fun onStall(now: Long = SystemClock.elapsedRealtime()) {
        stallTimes.addLast(now)
        pruneStalls(now)
    }

    /** Toma una muestra; llamarlo seguido (cada medio segundo) mientras hay algo en el reproductor. */
    fun sample(now: Long = SystemClock.elapsedRealtime()) {
        samples.addLast(longArrayOf(now, totalBytes.get()))
        while (samples.size > 2 && now - samples[1][0] >= WINDOW_MS) samples.removeFirst()
    }

    /** Bits por segundo de los últimos segundos; null si hay muy poca muestra. */
    fun speedBps(now: Long = SystemClock.elapsedRealtime()): Long? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val elapsed = last[0] - first[0]
        if (elapsed < MIN_SAMPLE_MS || now - last[0] > WINDOW_MS) return null
        return (last[1] - first[1]) * 8_000L / elapsed
    }

    /** Cuánto hace que no llega ningún byte; [Long.MAX_VALUE] si nunca llegó. */
    fun msSinceData(now: Long = SystemClock.elapsedRealtime()): Long =
        if (lastDataAt == 0L) Long.MAX_VALUE else now - lastDataAt

    /**
     * El aviso a mostrar ahora; null si no corresponde. [key] identifica el título o canal,
     * [needBps] lo que pide el video y [longWait] una precarga larga que siempre se muestra.
     */
    fun hint(
        key: String?,
        buffering: Boolean,
        needBps: Long?,
        percent: Int?,
        longWait: Boolean,
        now: Long = SystemClock.elapsedRealtime()
    ): NetworkHint? {
        if (!buffering) {
            bufferingSince = 0L
            episodeAllowed = null
            return null
        }
        if (bufferingSince == 0L) bufferingSince = now
        pruneStalls(now)
        val repeated = stallTimes.size >= 2
        if (!longWait && !repeated && now - bufferingSince < SHOW_AFTER_MS) return null
        val allowed = longWait || (episodeAllowed ?: run {
            val k = key.orEmpty()
            val last = shownAt[k]
            val ok = last == null || now - last >= COOLDOWN_MS
            if (ok) shownAt[k] = now
            episodeAllowed = ok
            ok
        })
        if (!allowed) return null
        val speed = speedBps(now)
        val mbps = speed?.let { it / 1_000_000f }
        val need = needBps?.takeIf { it > 0 }?.let { it / 1_000_000f }
        val noData = lastDataAt == 0L || now - lastDataAt > NO_DATA_MS
        val kind = when {
            mbps != null && need != null && mbps < need * SLOW_MARGIN -> NetworkHint.Kind.SLOW
            noData || repeated -> NetworkHint.Kind.UNSTABLE
            else -> NetworkHint.Kind.SLOW
        }
        return NetworkHint(kind, mbps, need, percent, preparing = longWait)
    }

    private fun pruneStalls(now: Long) {
        while (stallTimes.isNotEmpty() && now - stallTimes.first() > STALL_WINDOW_MS) stallTimes.removeFirst()
    }

    /** Total del archivo: de "Content-Range: bytes a-b/total" o del largo de una respuesta completa. */
    private fun totalOf(response: Response): Long? {
        response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()?.let { return it }
        if (response.code != 200) return null
        return response.body?.contentLength()?.takeIf { it > 0 }
    }

    private inner class CountingBody(private val body: ResponseBody) : ResponseBody() {
        private val counted: BufferedSource by lazy {
            object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) {
                        totalBytes.addAndGet(read)
                        lastDataAt = SystemClock.elapsedRealtime()
                    }
                    return read
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = body.contentType()
        override fun contentLength(): Long = body.contentLength()
        override fun source(): BufferedSource = counted
        override fun close() = body.close()
    }

    private companion object {
        const val WINDOW_MS = 5_000L
        const val MIN_SAMPLE_MS = 1_500L
        const val SHOW_AFTER_MS = 3_000L
        const val STALL_WINDOW_MS = 120_000L
        const val COOLDOWN_MS = 10 * 60_000L
        const val NO_DATA_MS = 3_000L
        /** Por debajo de 1,2 veces lo que pide el video, el internet no alcanza con margen. */
        const val SLOW_MARGIN = 1.2f
    }
}
