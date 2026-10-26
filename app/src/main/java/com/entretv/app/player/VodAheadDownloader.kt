package com.entretv.app.player

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/** La descarga adelantada no sirve para este título (sin largo, sin rangos, sin espacio…): se usa el camino normal. */
class AheadUnavailableException(cause: Throwable? = null) : IOException("Descarga adelantada no disponible", cause)

/**
 * Baja la película o episodio en curso al disco por delante de la reproducción con UNA sola
 * conexión: muchos proveedores Xtream permiten una por línea, así que el reproductor no abre
 * ninguna y lee solo del disco ([AheadCacheDataSource]).
 *
 *  - Si se corta la red, reintenta (1 s, 2 s… hasta 15 s; al volver la red, enseguida) desde
 *    el primer byte que falta. Lo ya bajado se conserva.
 *  - Un salto lejos de lo que se está bajando corta la conexión y la abre en el punto nuevo.
 *  - En disco queda solo el último título: al empezar otro se borra el anterior.
 *  - [client] tiene su propio Dispatcher: cancelar sus llamadas corta la conexión al instante
 *    (interrumpir el hilo no desbloquea una lectura de red).
 *  - La clave en caché es un hash de la URL: la URL lleva usuario y contraseña. Nunca se registra.
 */
@OptIn(UnstableApi::class)
class VodAheadDownloader(
    private val dir: File,
    private val databaseProvider: DatabaseProvider,
    private val client: OkHttpClient,
    userAgent: String
) {
    companion object {
        private const val FRAGMENT_BYTES = 1L * 1024 * 1024
        private const val MIN_FREE_BYTES = 1L * 1024 * 1024 * 1024
        /** Distancia "cerca" sin conocer la tasa de bits. */
        private const val DEFAULT_WINDOW_BYTES = 16L * 1024 * 1024
        private const val MIN_WINDOW_BYTES = 2L * 1024 * 1024
        private const val WINDOW_MS = 30_000L
        private const val MAX_RETRY_DELAY_MS = 15_000L
        /** Errores que no son de red (disco, datos) seguidos antes de pasar al camino normal. */
        private const val MAX_OTHER_ERRORS = 3
        private const val PROGRESS_EVERY_MS = 1_000L
        const val POLL_MS = 80L

        fun keyFor(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            return "vod-" + digest.joinToString("") { "%02x".format(it) }
        }
    }

    private val upstreamFactory = OkHttpDataSource.Factory(client).setUserAgent(userAgent)

    private val cacheLock = Any()
    @Volatile
    private var cacheInstance: SimpleCache? = null

    // Una sola instancia por carpeta en todo el proceso; se crea al primer uso.
    private val cache: SimpleCache
        get() = cacheInstance ?: synchronized(cacheLock) {
            cacheInstance ?: SimpleCache(dir, NoOpCacheEvictor(), databaseProvider).also { cacheInstance = it }
        }

    private val executorLazy = lazy<ExecutorService> {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "EntreVodAhead").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }
    private val executor by executorLazy

    private val _progress = MutableStateFlow<Float?>(null)
    /** Parte del archivo ya en disco (0..1); null si no hay descarga adelantada. */
    val progress: StateFlow<Float?> = _progress

    /** Duración del título; con ella "cerca" son ~30 s de video en vez de un tope fijo. */
    @Volatile
    var durationMs: Long = C.TIME_UNSET

    @Volatile
    private var url: String? = null
    @Volatile
    private var key: String? = null
    /** Cambia en cada arranque, salto o parada; una tarea vieja que sigue viva no toca nada. */
    @Volatile
    private var generation = 0
    @Volatile
    private var running = false
    @Volatile
    private var failed = false
    @Volatile
    private var jobStart = 0L
    /** Próximo byte que llega de la red (lo anterior ya está en disco o por guardarse). */
    @Volatile
    private var writePos = 0L
    @Volatile
    private var contentLength = C.LENGTH_UNSET.toLong()
    @Volatile
    private var writer: CacheWriter? = null
    private var lastPublishAt = 0L

    private val wakeLock = ReentrantLock()
    private val wake = wakeLock.newCondition()

    /** Prepara la descarga de [url]; arranca cuando el lector pide el primer byte. */
    @Synchronized
    fun attach(url: String) {
        if (url == this.url && !failed) return
        cancelJob()
        val key = keyFor(url)
        this.url = url
        this.key = key
        failed = false
        contentLength = C.LENGTH_UNSET.toLong()
        jobStart = 0L
        writePos = 0L
        _progress.value = null
        runCatching { executor.execute { prepare(key) } }.onFailure { failed = true }
    }

    /** Corta la descarga y cierra la conexión; lo bajado queda en disco. */
    @Synchronized
    fun stop() {
        if (url == null && !running) return
        cancelJob()
        url = null
        key = null
        failed = false
        contentLength = C.LENGTH_UNSET.toLong()
        _progress.value = null
    }

    fun release() {
        stop()
        if (executorLazy.isInitialized()) executor.shutdownNow()
        synchronized(cacheLock) {
            cacheInstance?.let { runCatching { it.release() } }
            cacheInstance = null
        }
    }

    /** Volvió la red: la espera entre reintentos termina ya. */
    fun onNetworkAvailable() {
        wakeLock.withLock { wake.signalAll() }
    }

    /** Conexión nueva ya, sin esperar el siguiente reintento (la vieja puede estar colgada). */
    @Synchronized
    fun reconnectNow() {
        val url = url ?: return
        if (running && !failed) restart(url, jobStart)
    }

    /**
     * Hasta dónde hay datos seguidos en disco desde [positionMs], en ms (aproximado, a tasa de
     * bits pareja). [C.TIME_UNSET] si [url] no se está bajando.
     */
    fun bufferedEndMs(url: String, positionMs: Long, durationMs: Long): Long {
        val key = key
        val length = contentLength
        val cache = cacheInstance
        if (url != this.url || key == null || cache == null || length <= 0 || durationMs <= 0) return C.TIME_UNSET
        val position = positionMs.coerceIn(0L, durationMs)
        val byte = (length.toDouble() * position / durationMs).toLong().coerceIn(0L, length)
        val cached = runCatching { cache.getCachedLength(key, byte, length - byte) }.getOrDefault(0L)
        if (cached <= 0) return position
        return ((byte + cached).toDouble() / length * durationMs).toLong().coerceAtMost(durationMs)
    }

    // ============================ Para el lector ============================

    /**
     * Arranca la descarga si no corre, o la mueve a [position] si ese byte no está y queda
     * detrás de donde empezó o lejos por delante de lo que se está bajando.
     */
    internal fun ensure(url: String, position: Long, cached: Boolean) {
        if (url != this.url || failed) throw AheadUnavailableException()
        val length = contentLength
        if (length != C.LENGTH_UNSET.toLong() && position >= length) return
        if (running) {
            if (cached) return
            val front = writePos
            if (position >= jobStart && position + 2 * FRAGMENT_BYTES >= front && position <= front + windowBytes()) return
        }
        restart(url, position)
    }

    /** Bloquea hasta conocer el largo del archivo (lo da la primera respuesta o una vez anterior). */
    internal fun awaitContentLength(url: String): Long {
        while (true) {
            if (url != this.url || failed) throw AheadUnavailableException()
            val length = contentLength
            if (length != C.LENGTH_UNSET.toLong()) return length
            sleepInterruptibly()
        }
    }

    internal fun isCached(url: String, position: Long): Boolean {
        val key = key
        if (url != this.url || key == null) throw AheadUnavailableException()
        return cache.isCached(key, position, 1)
    }

    /** Tramo en disco que contiene [position]; null si ese byte todavía no llegó. */
    internal fun cachedSpanAt(url: String, position: Long): CacheSpan? {
        val key = key
        if (url != this.url || key == null || failed) throw AheadUnavailableException()
        val cache = cache
        if (!cache.isCached(key, position, 1)) return null
        val span = cache.getCachedSpans(key).floor(CacheSpan(key, position, C.LENGTH_UNSET.toLong())) ?: return null
        return span.takeIf { it.isCached && it.file != null && position < it.position + it.length }
    }

    /** Espera corta que respeta la cancelación del cargador de ExoPlayer (interrumpe el hilo). */
    internal fun sleepInterruptibly() {
        if (Thread.interrupted()) throw InterruptedIOException()
        try {
            Thread.sleep(POLL_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        }
    }

    // ============================ Descarga ============================

    private fun windowBytes(): Long {
        val length = contentLength
        val duration = durationMs
        if (length <= 0 || duration <= 0) return DEFAULT_WINDOW_BYTES
        return maxOf(length * WINDOW_MS / duration, MIN_WINDOW_BYTES)
    }

    @Synchronized
    private fun restart(url: String, position: Long) {
        if (url != this.url || failed) return
        val key = key ?: return
        cancelJob()
        val gen = generation
        jobStart = position
        writePos = position
        running = true
        runCatching { executor.execute { download(gen, url, key, position) } }.onFailure { running = false }
    }

    /** Llamar con el monitor tomado. */
    private fun cancelJob() {
        generation++
        running = false
        writer?.cancel()
        writer = null
        client.dispatcher.cancelAll()
        wakeLock.withLock { wake.signalAll() }
    }

    @Synchronized
    private fun markFailed(gen: Int) {
        if (gen != generation) return
        failed = true
        running = false
    }

    /** Hilo de descarga: borra los títulos anteriores y recupera el largo guardado. */
    private fun prepare(key: String) {
        if (key != this.key) return
        try {
            val cache = cache
            for (other in cache.keys) if (other != key) cache.removeResource(other)
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            synchronized(this) {
                if (key == this.key && length > 0 && contentLength == C.LENGTH_UNSET.toLong()) contentLength = length
            }
            publishProgress(generation, key, force = true)
        } catch (e: Exception) {
            synchronized(this) { if (key == this.key) markFailed(generation) }
        }
    }

    private fun download(gen: Int, url: String, key: String, from: Long) {
        var attempt = 0
        var otherErrors = 0
        while (gen == generation) {
            val startedAt = writePos
            val cacheWriter = try {
                val source = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(DataSource.Factory { ProbeDataSource(upstreamFactory.createDataSource(), gen, key) })
                    .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache).setFragmentSize(FRAGMENT_BYTES))
                    .createDataSourceForDownloading()
                val spec = DataSpec.Builder().setUri(url).setPosition(from).setKey(key).build()
                CacheWriter(source, spec, null, CacheWriter.ProgressListener { _, _, _ -> publishProgress(gen, key, force = false) })
            } catch (e: Exception) {
                markFailed(gen)
                return
            }
            synchronized(this) {
                if (gen != generation) return
                writer = cacheWriter
            }
            try {
                // Recorre desde [from] hasta el final salteando lo que ya está en disco.
                cacheWriter.cache()
                synchronized(this) { if (gen == generation) running = false }
                publishProgress(gen, key, force = true)
                return
            } catch (e: IOException) {
                if (gen != generation) return
                when {
                    e.hasCause<AheadUnavailableException>() || e.isPermanentHttpError() -> {
                        markFailed(gen)
                        return
                    }
                    e.isNetworkError() || e.isBusyHttpError() -> otherErrors = 0
                    ++otherErrors >= MAX_OTHER_ERRORS -> {
                        markFailed(gen)
                        return
                    }
                }
                attempt = if (writePos > startedAt) 1 else attempt + 1
            } catch (e: Exception) {
                markFailed(gen)
                return
            }
            publishProgress(gen, key, force = true)
            val delayMs = (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(MAX_RETRY_DELAY_MS)
            try {
                wakeLock.withLock { if (gen == generation) wake.await(delayMs, TimeUnit.MILLISECONDS) }
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun publishProgress(gen: Int, key: String, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublishAt < PROGRESS_EVERY_MS) return
        lastPublishAt = now
        val length = contentLength
        if (gen != generation || length <= 0) return
        val cached = runCatching { cache.getCachedBytes(key, 0, length) }.getOrNull() ?: return
        if (gen == generation) _progress.value = (cached.toFloat() / length).coerceIn(0f, 1f)
    }

    private fun hasRoom(key: String, total: Long): Boolean {
        val cached = cache.getCachedBytes(key, 0, total)
        val volume = if (dir.exists()) dir else dir.parentFile ?: dir
        return volume.usableSpace >= total - cached + MIN_FREE_BYTES
    }

    /**
     * Mira la primera respuesta de cada pedido: sin largo conocido, sin soporte de rangos (un
     * pedido desde la mitad que vuelve completo) o sin espacio para el resto, el título no sirve
     * para bajarse adelantado. También sigue el byte que está llegando.
     */
    private inner class ProbeDataSource(
        private val upstream: DataSource,
        private val gen: Int,
        private val key: String
    ) : DataSource {

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val length = upstream.open(dataSpec)
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                val total = if (length == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else dataSpec.position + length
                val rangeOk = dataSpec.position == 0L || upstream.responseHeaders.keys.any { it.equals("Content-Range", ignoreCase = true) }
                if (total == C.LENGTH_UNSET.toLong() || !rangeOk || !hasRoom(key, total)) {
                    runCatching { upstream.close() }
                    throw AheadUnavailableException()
                }
                synchronized(this@VodAheadDownloader) { if (gen == generation) contentLength = total }
            }
            if (gen == generation) writePos = dataSpec.position
            return length
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = upstream.read(buffer, offset, length)
            if (read > 0 && gen == generation) writePos += read
            return read
        }

        override fun getUri(): Uri? = upstream.uri

        override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

        override fun close() {
            upstream.close()
        }
    }
}
