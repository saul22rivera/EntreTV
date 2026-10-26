package com.entretv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.Options
import coil.request.SuccessResult
import com.entretv.app.data.backgroundExecutor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** Pedido de un póster de tarjeta: pasa por las miniaturas de [PosterThumbs]. */
fun posterRequest(context: Context, url: String): ImageRequest.Builder =
    ImageRequest.Builder(context)
        .data(cardImage(url))
        .setParameter(PosterThumbs.PARAM, true, memoryCacheKey = null)

/**
 * Miniaturas de los pósters en disco, ya reducidas al tamaño de tarjeta (WebP de unos 20 KB).
 * Volver a mostrar un póster decodifica ese archivo en vez del original, que del proveedor puede
 * pesar cientos de KB, y en el mismo espacio caben muchos más. Solo para pedidos de [posterRequest].
 * Van fuera de la caché: Android no las borra cuando le falta espacio.
 */
class PosterThumbs(context: Context, private val maxBytes: Long, private val scope: CoroutineScope) {

    private val dir = dirOf(context)
    private val legacyDir = legacyDirOf(context)
    private val writer = backgroundExecutor("poster-thumbs", 1).asCoroutineDispatcher()
    private val writing = ConcurrentHashMap.newKeySet<String>()

    val fetcherFactory = Fetcher.Factory<Uri> { data, options, _ -> fetcher(data, options) }

    val interceptor = Interceptor { chain ->
        val result = chain.proceed(chain.request)
        if (result is SuccessResult) save(result)
        result
    }

    /** Al arrancar: si se pasó del tope, borra las menos usadas hasta quedar en el 80 %. */
    fun trim() {
        scope.launch(writer) {
            migrate()
            val files = dir.listFiles()?.filter { it.isFile } ?: return@launch
            var total = files.sumOf { it.length() }
            if (total <= maxBytes) return@launch
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= maxBytes * 8 / 10) break
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    /** Las de versiones anteriores estaban en la caché: se mudan una vez. */
    private fun migrate() {
        if (!legacyDir.isDirectory) return
        dir.mkdirs()
        legacyDir.listFiles()?.forEach { old ->
            if (!old.isFile || !old.name.endsWith(".webp")) return@forEach
            val target = File(dir, old.name)
            if (target.exists() || !old.renameTo(target)) old.delete()
        }
        legacyDir.deleteRecursively()
    }

    private fun fileFor(url: String) = fileIn(dir, url)

    private fun fetcher(data: Uri, options: Options): Fetcher? {
        if (options.parameters.value<Boolean>(PARAM) != true) return null
        val file = fileFor(data.toString())
        if (!file.isFile) return null
        return Fetcher {
            file.setLastModified(System.currentTimeMillis())
            SourceResult(
                source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
                mimeType = "image/webp",
                dataSource = DataSource.DISK
            )
        }
    }

    private fun save(result: SuccessResult) {
        val request = result.request
        if (request.parameters.value<Boolean>(PARAM) != true) return
        if (result.dataSource != DataSource.NETWORK && result.dataSource != DataSource.DISK) return
        val url = request.data as? String ?: return
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return
        // Una miniatura chica se vería borrosa en una tarjeta más grande.
        if (bitmap.height < MIN_HEIGHT) return
        val file = fileFor(url)
        if (!writing.add(file.name)) return
        scope.launch(writer) {
            try {
                if (file.isFile) return@launch
                val source = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@launch
                } else bitmap
                val scaled = if (source.height > MAX_HEIGHT) {
                    Bitmap.createScaledBitmap(source, source.width * MAX_HEIGHT / source.height, MAX_HEIGHT, true)
                } else source
                dir.mkdirs()
                val tmp = File(dir, file.name + ".tmp")
                val ok = tmp.outputStream().use { out -> scaled.compress(webpLossy(), QUALITY, out) }
                if (ok) tmp.renameTo(file) else tmp.delete()
                if (scaled !== source) scaled.recycle()
                if (source !== bitmap) source.recycle()
            } catch (_: Exception) {
            } finally {
                writing.remove(file.name)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun webpLossy() =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    companion object {
        const val PARAM = "entre_poster"
        const val DIR = "entre_thumbs"
        private const val MIN_HEIGHT = 160
        private const val MAX_HEIGHT = 450
        private const val QUALITY = 80
        /** Tamaño con el que la precarga decodifica para dejar la miniatura hecha. */
        const val PREFETCH_WIDTH = 300
        const val PREFETCH_HEIGHT = MAX_HEIGHT

        private fun dirOf(context: Context) = context.noBackupFilesDir.resolve(DIR)

        private fun legacyDirOf(context: Context) = context.cacheDir.resolve(DIR)

        private fun fileIn(dir: File, url: String) = File(dir, url.encodeUtf8().sha1().hex() + ".webp")

        /** Ya hay miniatura de [url] (la de la tarjeta). Toca el disco: fuera del hilo principal. */
        fun exists(context: Context, url: String): Boolean = fileIn(dirOf(context), url).isFile

        /** Borra todas las miniaturas. Toca el disco: fuera del hilo principal. */
        fun clear(context: Context) {
            dirOf(context).deleteRecursively()
            legacyDirOf(context).deleteRecursively()
        }
    }
}
