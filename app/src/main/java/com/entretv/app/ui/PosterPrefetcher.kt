package com.entretv.app.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TMDB_BIG = Regex("/t/p/(original|w1280|w780|w500)/")

/** Los pósters de TMDB que manda el servidor suelen venir en tamaño original: para una tarjeta basta w342. */
fun cardImage(url: String): String = if ("image.tmdb.org" in url) TMDB_BIG.replace(url, "/t/p/w342/") else url

/** Descargas de precarga a la vez: el resto del cupo por servidor queda para los pósters en pantalla. */
private const val PREFETCH_PARALLEL = 3

/**
 * Baja pósters a la caché de disco de Coil antes de que aparezcan. Pide la URL de la tarjeta
 * ([cardImage]): la clave de la caché es la URL, así la tarjeta la encuentra.
 */
@Singleton
class PosterPrefetcher @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("entre_posters", Context.MODE_PRIVATE) }

    /** Baja [urls] de a [PREFETCH_PARALLEL]; cancelar la corrutina corta lo que esté bajando. */
    suspend fun prefetch(urls: List<String>) {
        if (urls.isEmpty()) return
        val loader = context.imageLoader
        val next = AtomicInteger()
        coroutineScope {
            repeat(minOf(PREFETCH_PARALLEL, urls.size)) {
                launch {
                    while (true) {
                        val url = urls.getOrNull(next.getAndIncrement()) ?: break
                        loader.execute(request(url))
                    }
                }
            }
        }
    }

    // Coil termina de escribir la respuesta en disco antes de decodificar: no hace falta el bitmap.
    private fun request(url: String) = ImageRequest.Builder(context)
        .data(cardImage(url))
        .memoryCachePolicy(CachePolicy.DISABLED)
        .decoderFactory { _, _, _ -> Decoder { DecodeResult(ColorDrawable(), isSampled = false) } }
        .build()

    /** La precarga del catálogo todavía no se completó hoy. */
    suspend fun catalogDueToday(): Boolean = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_CATALOG_DAY, -1L) != LocalDate.now().toEpochDay()
    }

    suspend fun markCatalogDone() = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_CATALOG_DAY, LocalDate.now().toEpochDay()).apply()
    }

    private companion object {
        const val KEY_CATALOG_DAY = "catalog_day"
    }
}
