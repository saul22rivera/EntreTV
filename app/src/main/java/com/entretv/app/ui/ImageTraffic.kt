package com.entretv.app.ui

import coil.request.ImageRequest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * Pedidos de imágenes por la red. El primero de un póster en pantalla es rápido: si no conecta en
 * medio segundo se reintenta enseguida ([relaxed]). Los reintentos y la descarga en segundo plano
 * ([inBackground]) esperan más. Los de pantalla se cuentan en [active] para que el segundo plano les ceda la red.
 */
object ImageTraffic {
    private const val HEADER_RELAXED = "X-Entre-Relaxed"
    private const val HEADER_BACKGROUND = "X-Entre-Background"
    private const val RELAXED_CONNECT_S = 3
    private const val RELAXED_READ_S = 8

    /** Marca puesta en el pedido de OkHttp de un intento holgado: el corte de servidor caído solo cuenta esos. */
    object Relaxed

    private val uiActive = MutableStateFlow(0)
    /** Pósters de pantalla bajando por la red ahora mismo. */
    val active: StateFlow<Int> = uiActive

    /** Tiempos holgados: para el reintento de un póster que no llegó. */
    fun ImageRequest.Builder.relaxed(): ImageRequest.Builder = setHeader(HEADER_RELAXED, "1")

    /** Descarga en segundo plano: tiempos holgados y no cuenta como pantalla. */
    fun ImageRequest.Builder.inBackground(): ImageRequest.Builder = setHeader(HEADER_BACKGROUND, "1")

    val interceptor = Interceptor { chain ->
        val original = chain.request()
        val background = original.header(HEADER_BACKGROUND) != null
        val relaxed = background || original.header(HEADER_RELAXED) != null
        val request = original.newBuilder()
            .removeHeader(HEADER_BACKGROUND)
            .removeHeader(HEADER_RELAXED)
            .apply { if (relaxed) tag(Relaxed::class.java, Relaxed) }
            .build()
        val next = if (relaxed) {
            chain.withConnectTimeout(RELAXED_CONNECT_S, TimeUnit.SECONDS)
                .withReadTimeout(RELAXED_READ_S, TimeUnit.SECONDS)
        } else chain
        if (background) return@Interceptor next.proceed(request)
        uiActive.update { it + 1 }
        val done = AtomicBoolean()
        val finish = { if (done.compareAndSet(false, true)) uiActive.update { it - 1 } }
        val response = try {
            next.proceed(request)
        } catch (e: Throwable) {
            finish()
            throw e
        }
        val body = response.body ?: run { finish(); return@Interceptor response }
        response.newBuilder().body(CountedBody(body, finish)).build()
    }

    /** El póster sigue bajando hasta que se cierra el cuerpo, no al llegar la cabecera. */
    private class CountedBody(private val body: ResponseBody, private val onClose: () -> Unit) : ResponseBody() {
        private val source by lazy {
            object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long = try {
                    super.read(sink, byteCount).also { if (it == -1L) onClose() }
                } catch (e: Exception) {
                    onClose()
                    throw e
                }

                override fun close() {
                    onClose()
                    super.close()
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = body.contentType()
        override fun contentLength(): Long = body.contentLength()
        override fun source(): BufferedSource = source

        override fun close() {
            onClose()
            body.close()
        }
    }
}
