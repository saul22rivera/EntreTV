package com.entretv.app.data.net

import com.entretv.app.data.repo.PlaylistRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Resultado de "Probar mi internet". Velocidades en Mbps y tiempos en ms; null = no se pudo medir. */
data class SpeedResult(
    val internetMbps: Float?,
    val pingMs: Int?,
    val jitterMs: Int?,
    val serverMbps: Float?,
    val serverPingMs: Int?
) {
    /** El ping salta mucho entre una medición y otra (wifi lejos o saturado). */
    val unstable: Boolean get() = (jitterMs ?: 0) >= UNSTABLE_JITTER_MS

    /** Lo que de verdad llega al reproductor: el más lento de los dos tramos. */
    val usableMbps: Float? get() = listOfNotNull(internetMbps, serverMbps).minOrNull()

    companion object {
        const val UNSTABLE_JITTER_MS = 30
    }
}

/**
 * Mide la bajada y el ping contra un CDN neutral y contra el servidor del proveedor.
 * Las URLs del proveedor llevan usuario y clave: no se registran ni se muestran.
 */
@Singleton
class SpeedTester @Inject constructor(
    okHttpClient: OkHttpClient,
    private val repository: PlaylistRepository
) {
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .cache(null)
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .retryOnConnectionFailure(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** [onPhase]: 0 = tu internet, 1 = servidor del proveedor. */
    suspend fun run(onPhase: (Int) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
        onPhase(0)
        val pings = pingSeries(CDN_PING_URL, PING_COUNT)
        val internet = coroutineScope {
            List(CDN_STREAMS) { async { download(CDN_DOWN_URL, DOWNLOAD_MS) } }.map { it.await() }
        }.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

        onPhase(1)
        val playlist = runCatching { repository.currentPlaylist() }.getOrNull()
        val base = playlist?.url?.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}:${it.port}/" }
        val serverPings = base?.let { pingSeries(it, SERVER_PING_COUNT) }.orEmpty()
        val sample = playlist?.let { p ->
            runCatching { repository.moviesNamed(p.id, "", 5) }.getOrDefault(emptyList())
                .firstOrNull { it.streamUrl.startsWith("http", ignoreCase = true) }?.streamUrl
        }
        val server = sample?.let { download(it, DOWNLOAD_MS) }

        SpeedResult(
            internetMbps = internet,
            pingMs = pings.median(),
            jitterMs = pings.jitter(),
            serverMbps = server,
            serverPingMs = serverPings.median()
        )
    }

    /** Tiempos de ida y vuelta por la misma conexión; la primera (abre la conexión) no cuenta. */
    private suspend fun pingSeries(url: String, count: Int): List<Int> {
        val times = ArrayList<Int>(count)
        repeat(count + 1) { index ->
            kotlin.coroutines.coroutineContext.ensureActive()
            val start = System.nanoTime()
            val ok = runCatching {
                client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute()
                    .use { it.body?.bytes() }
            }.isSuccess
            val ms = ((System.nanoTime() - start) / 1_000_000).toInt()
            if (ok && index > 0) times += ms
        }
        return times
    }

    /** Mbps bajando [url] durante [durationMs]; el primer segundo (arranque de TCP) no cuenta. */
    private suspend fun download(url: String, durationMs: Long): Float? {
        val ctx = kotlin.coroutines.coroutineContext
        return runCatching {
            val call = client.newBuilder().readTimeout(4, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build())
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                val source = response.body?.source() ?: return null
                val buffer = ByteArray(64 * 1024)
                val start = System.nanoTime()
                val end = start + durationMs * 1_000_000
                val warm = start + WARMUP_MS * 1_000_000
                var countedFrom = 0L
                var bytes = 0L
                val input = source.inputStream()
                while (ctx.isActiveSafe()) {
                    val now = System.nanoTime()
                    if (now >= end) break
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (now >= warm) {
                        if (countedFrom == 0L) countedFrom = now
                        bytes += read
                    }
                }
                call.cancel()
                val seconds = (System.nanoTime() - countedFrom) / 1e9
                if (countedFrom == 0L || seconds < 0.5 || bytes <= 0) null else (bytes * 8 / seconds / 1e6).toFloat()
            }
        }.getOrNull()
    }

    private fun kotlin.coroutines.CoroutineContext.isActiveSafe(): Boolean =
        this[kotlinx.coroutines.Job]?.isActive ?: true

    private fun List<Int>.median(): Int? = if (isEmpty()) null else sorted()[size / 2]

    /** Variación media entre pings seguidos. */
    private fun List<Int>.jitter(): Int? =
        if (size < 3) null else zipWithNext { a, b -> abs(a - b) }.average().toInt()

    private companion object {
        const val CDN_PING_URL = "https://speed.cloudflare.com/__down?bytes=0"
        const val CDN_DOWN_URL = "https://speed.cloudflare.com/__down?bytes=100000000"
        const val CDN_STREAMS = 2
        const val PING_COUNT = 8
        const val SERVER_PING_COUNT = 4
        const val DOWNLOAD_MS = 6_000L
        const val WARMUP_MS = 1_000L
    }
}
