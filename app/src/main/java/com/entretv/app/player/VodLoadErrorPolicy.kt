package com.entretv.app.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Películas y episodios: un corte de red se reintenta sin fin y sin mostrar error (cada 1 s,
 * 2 s… hasta 5 s) y la carga sigue desde el mismo byte. 401/404/410 no se reintentan;
 * 403/509 (límite de conexiones del proveedor) y lo demás, [limitedRetries] veces y después
 * el error pasa al reproductor, que reconstruye la fuente.
 */
@OptIn(UnstableApi::class)
class VodLoadErrorPolicy(private val limitedRetries: Int) : DefaultLoadErrorHandlingPolicy(limitedRetries) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        val count = loadErrorInfo.errorCount
        if (error.hasCause<AheadUnavailableException>()) return C.TIME_UNSET
        val http = error.findCause<HttpDataSource.InvalidResponseCodeException>()
        if (http != null) {
            if (http.isPermanent()) return C.TIME_UNSET
            return if (count <= limitedRetries) backoff(count) else C.TIME_UNSET
        }
        if (error.isNetworkError()) return backoff(count)
        return if (count <= limitedRetries) super.getRetryDelayMsFor(loadErrorInfo) else C.TIME_UNSET
    }

    // Sin esto el error de red llegaría al reproductor a los pocos intentos aunque se siga reintentando.
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE

    private fun backoff(count: Int): Long = minOf(1_000L * count, 5_000L)
}

internal inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is T) return cause
        cause = cause.cause
    }
    return null
}

internal inline fun <reified T : Throwable> Throwable.hasCause(): Boolean = findCause<T>() != null

@OptIn(UnstableApi::class)
internal fun HttpDataSource.InvalidResponseCodeException.isPermanent(): Boolean =
    responseCode == 401 || responseCode == 404 || responseCode == 410

/** Corte, tiempo agotado o servidor que no responde: vale la pena seguir intentando. */
@OptIn(UnstableApi::class)
internal fun Throwable.isNetworkError(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        when (cause) {
            is HttpDataSource.InvalidResponseCodeException,
            is HttpDataSource.InvalidContentTypeException,
            is HttpDataSource.CleartextNotPermittedException -> return false
            is SocketTimeoutException, is UnknownHostException, is SocketException -> return true
        }
        cause = cause.cause
    }
    // OkHttp envuelve cualquier falla de la conexión en esta excepción.
    return this is HttpDataSource.HttpDataSourceException
}

@OptIn(UnstableApi::class)
internal fun IOException.isPermanentHttpError(): Boolean =
    findCause<HttpDataSource.InvalidResponseCodeException>()?.isPermanent() == true

/** 403/429/509/5xx: límite de conexiones o servidor saturado; se libera al rato. */
@OptIn(UnstableApi::class)
internal fun IOException.isBusyHttpError(): Boolean {
    val code = findCause<HttpDataSource.InvalidResponseCodeException>()?.responseCode ?: return false
    return code == 403 || code == 429 || code == 509 || code >= 500
}
