package com.entretv.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import coil.intercept.Interceptor
import coil.network.HttpException
import coil.request.ErrorResult
import com.entretv.app.data.backgroundExecutor
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import okhttp3.Interceptor as HttpInterceptor

/** Reintentos de una tarjeta cuyo póster no llegó por la red, y la espera antes de cada uno. */
internal const val POSTER_RETRIES = 2
internal const val POSTER_RETRY_DELAY_MS = 8_000L
/** Solo distingue el pedido del reintento: no cambia la clave de la caché en memoria. */
internal const val POSTER_RETRY_PARAM = "entre_retry"

/** El póster falló de forma definitiva hace poco (404, 410 o servidor inexistente): no se vuelve a pedir. */
class BrokenImageException : Exception("imagen rota recordada")

/** El servidor de imágenes no respondió varias veces seguidas: se lo deja descansar. */
class DeadHostException : IOException("servidor de imágenes sin respuesta")

/**
 * Pósters que no conviene volver a pedir. Las URL que fallaron de forma definitiva se recuerdan
 * [FAILED_TTL_MS] en un archivo (solo su SHA-1: llevan datos de la cuenta); los servidores que no
 * conectan [DEAD_HOST_FAILURES] veces seguidas se saltan [DEAD_HOST_MS], solo en memoria.
 */
class ImageFailures private constructor(private val context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val io = backgroundExecutor("image-failures", 1).asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val file by lazy { File(context.noBackupFilesDir, FILE) }

    /** SHA-1 de la URL → cuándo falló. Hasta que termina de leerse el archivo solo tiene lo nuevo. */
    private val failed = ConcurrentHashMap<String, Long>()
    /** Solo se toca desde [io]. */
    private var saveJob: Job? = null

    private val connectFailures = ConcurrentHashMap<String, Int>()
    private val deadUntil = ConcurrentHashMap<String, Long>()

    init {
        scope.launch { load() }
        // Sin red todos los servidores fallan: al volver la red se les da otra oportunidad.
        try {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectFailures.clear()
                    deadUntil.clear()
                }
            })
        } catch (_: RuntimeException) {
        }
    }

    /** Pedidos de [posterRequest]: si la URL ya falló de forma definitiva, error sin tocar la red. */
    val interceptor = Interceptor { chain ->
        val request = chain.request
        val url = request.data as? String
        if (url == null || request.parameters.value<Boolean>(PosterThumbs.PARAM) != true) {
            return@Interceptor chain.proceed(request)
        }
        if (isRemembered(url)) return@Interceptor ErrorResult(null, request, BrokenImageException())
        val result = chain.proceed(request)
        if (result is ErrorResult) onFailure(url, result.throwable)
        result
    }

    /** Para el cliente HTTP de Coil: corta al instante los pedidos a un servidor caído. */
    val hostBreaker = HttpInterceptor { chain ->
        val host = chain.request().url.host
        if (isDead(host)) throw DeadHostException()
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            if (e.isConnectFailure()) onConnectFailure(host)
            throw e
        }
        connectFailures.remove(host)
        deadUntil.remove(host)
        response
    }

    /** [url] falló de forma definitiva hace menos de [FAILED_TTL_MS]. Solo memoria: sirve en el hilo principal. */
    fun isRemembered(url: String): Boolean {
        val key = keyOf(url)
        val at = failed[key] ?: return false
        if (System.currentTimeMillis() - at < FAILED_TTL_MS) return true
        failed.remove(key)
        return false
    }

    /** Olvida todo (al borrar las imágenes desde Configuración). */
    fun clear() {
        connectFailures.clear()
        deadUntil.clear()
        scope.launch {
            failed.clear()
            saveJob?.cancel()
            file.delete()
        }
    }

    private fun onFailure(url: String, error: Throwable) {
        when {
            error is HttpException && (error.response.code == 404 || error.response.code == 410) -> remember(url)
            // Con la red caída también falla el DNS: solo cuenta si hay internet de verdad.
            error is UnknownHostException -> scope.launch { if (networkValidated()) remember(url) }
        }
    }

    private fun remember(url: String) {
        failed[keyOf(url)] = System.currentTimeMillis()
        scope.launch {
            if (saveJob?.isActive == true) return@launch
            saveJob = scope.launch {
                delay(SAVE_DELAY_MS)
                save()
            }
        }
    }

    private fun networkValidated(): Boolean {
        val cm = connectivity ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isDead(host: String): Boolean {
        val until = deadUntil[host] ?: return false
        if (System.currentTimeMillis() < until) return true
        deadUntil.remove(host)
        return false
    }

    private fun onConnectFailure(host: String) {
        val count = connectFailures.merge(host, 1) { a, b -> a + b } ?: 1
        if (count >= DEAD_HOST_FAILURES) {
            connectFailures.remove(host)
            deadUntil[host] = System.currentTimeMillis() + DEAD_HOST_MS
        }
    }

    /** Solo fallas al conectar: un servidor lento que ya respondió no cuenta. */
    private fun IOException.isConnectFailure(): Boolean =
        this is UnknownHostException || this is ConnectException ||
            (this is SocketTimeoutException && message.orEmpty().contains("connect", ignoreCase = true))

    private fun load() {
        val now = System.currentTimeMillis()
        try {
            if (!file.isFile) return
            file.forEachLine { line ->
                val key = line.substringBefore(' ')
                val at = line.substringAfter(' ', "").toLongOrNull() ?: return@forEachLine
                if (key.length == SHA1_HEX_LENGTH && now - at < FAILED_TTL_MS) failed.putIfAbsent(key, at)
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val now = System.currentTimeMillis()
        try {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.bufferedWriter().use { out ->
                for ((key, at) in failed) {
                    if (now - at < FAILED_TTL_MS) out.append(key).append(' ').append(at.toString()).append('\n')
                }
            }
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val FILE = "entre_img_failed.txt"
        private const val FAILED_TTL_MS = 24 * 60 * 60_000L
        private const val SAVE_DELAY_MS = 5_000L
        private const val DEAD_HOST_FAILURES = 3
        private const val DEAD_HOST_MS = 10 * 60_000L
        private const val SHA1_HEX_LENGTH = 40

        @Volatile private var instance: ImageFailures? = null

        fun get(context: Context): ImageFailures =
            instance ?: synchronized(this) {
                instance ?: ImageFailures(context.applicationContext).also { instance = it }
            }

        /** Error de red pasajero (lento o cortado): vale la pena reintentar el póster más tarde. */
        fun isTransient(error: Throwable): Boolean = error is IOException && error !is DeadHostException

        private fun keyOf(url: String): String = url.encodeUtf8().sha1().hex()
    }
}
