package com.entretv.app.ui

import android.content.Context
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
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

/**
 * Tamaño en píxeles de la imagen de un póster de la cuadrícula, tal como se dibujó la última vez.
 * Cero en equipos con poca memoria: ahí no se precarga a memoria.
 */
object GridPosterSize {
    @Volatile var px: IntSize = IntSize.Zero
}

/** Descargas de precarga a la vez: el resto del cupo por servidor queda para los pósters en pantalla. */
private const val PREFETCH_PARALLEL = 3

/**
 * Prepara pósters antes de que aparezcan. Pide la URL de la tarjeta ([cardImage]) por
 * [posterRequest]: así quedan en las miniaturas de [PosterThumbs] que la tarjeta lee.
 */
@Singleton
class PosterPrefetcher @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("entre_posters", Context.MODE_PRIVATE) }
    private val failures by lazy { ImageFailures.get(context) }

    /**
     * Deja la miniatura en disco de [urls], de a [PREFETCH_PARALLEL]; cancelar corta lo que esté bajando.
     * Antes de cada descarga espera a [gate]; si devuelve false no baja nada más.
     */
    suspend fun prefetch(urls: List<String>, gate: (suspend () -> Boolean)? = null) = load(urls, gate) { url ->
        val card = cardImage(url)
        if (PosterThumbs.exists(context, card) || failures.isRemembered(card)) null
        else posterRequest(context, url)
            .size(PosterThumbs.PREFETCH_WIDTH, PosterThumbs.PREFETCH_HEIGHT)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.DISABLED)
            // No se dibuja: un bitmap de software se guarda como miniatura sin copiarlo.
            .allowHardware(false)
            .build()
    }

    /**
     * Además de la miniatura, deja [urls] decodificados en la caché de memoria al tamaño de la
     * cuadrícula ([GridPosterSize]): al abrir la categoría aparecen sin esperar.
     */
    suspend fun warm(urls: List<String>) {
        val size = GridPosterSize.px
        if (size == IntSize.Zero) return prefetch(urls)
        load(urls) { url ->
            posterRequest(context, url)
                .size(size.width, size.height)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .build()
        }
    }

    private suspend fun load(
        urls: List<String>,
        gate: (suspend () -> Boolean)? = null,
        request: (String) -> ImageRequest?
    ) {
        if (urls.isEmpty()) return
        val loader = context.imageLoader
        val next = AtomicInteger()
        withContext(Dispatchers.IO) {
            coroutineScope {
                repeat(minOf(PREFETCH_PARALLEL, urls.size)) {
                    launch {
                        while (true) {
                            val url = urls.getOrNull(next.getAndIncrement()) ?: break
                            val built = request(url) ?: continue
                            if (gate != null && !gate()) break
                            loader.execute(built)
                        }
                    }
                }
            }
        }
    }

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
