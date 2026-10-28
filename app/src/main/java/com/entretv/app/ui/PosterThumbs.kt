package com.entretv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.Options
import coil.request.SuccessResult
import coil.size.Size
import com.entretv.app.data.backgroundExecutor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
 * Versión diminuta del póster guardada en el equipo: estirada a la tarjeta se ve borrosa mientras
 * llega la nítida. Nunca va a la red: si no está, el pedido falla.
 */
fun microRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(cardImage(url))
        .setParameter(PosterThumbs.PARAM_MICRO, true, memoryCacheKey = "1")
        .size(Size.ORIGINAL)
        .diskCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(CachePolicy.DISABLED)
        .crossfade(false)
        .build()

private class MissingMicroException : Exception("sin vista previa")

/**
 * Miniaturas de los pósters en disco, ya reducidas al tamaño de tarjeta (WebP de unos 20 KB).
 * Volver a mostrar un póster decodifica ese archivo en vez del original, que del proveedor puede
 * pesar cientos de KB, y en el mismo espacio caben muchos más. Solo para pedidos de [posterRequest].
 * Van fuera de la caché: Android no las borra cuando le falta espacio.
 */
class PosterThumbs(context: Context, private val maxBytes: Long, private val scope: CoroutineScope) {

    private val dir = dirOf(context)
    private val microDir = microDirOf(dir)
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
            var total = files.sumOf { it.length() } + (microDir.listFiles()?.sumOf { it.length() } ?: 0L)
            if (total <= maxBytes) return@launch
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= maxBytes * 8 / 10) break
                val size = file.length()
                if (!file.delete()) continue
                total -= size
                val micro = File(microDir, file.name)
                val microSize = micro.length()
                if (micro.delete()) total -= microSize
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
        if (options.parameters.value<Boolean>(PARAM_MICRO) == true) {
            val micro = File(microDir, fileFor(data.toString()).name)
            return Fetcher {
                if (!micro.isFile) throw MissingMicroException()
                SourceResult(
                    source = ImageSource(micro.toOkioPath(), FileSystem.SYSTEM),
                    mimeType = "image/webp",
                    dataSource = DataSource.DISK
                )
            }
        }
        if (options.parameters.value<Boolean>(PARAM) != true) return null
        val file = fileFor(data.toString())
        if (!file.isFile) return null
        return Fetcher {
            // Para el recorte de trim(); escribir la fecha en cada póster mostrado cargaba el disco.
            val now = System.currentTimeMillis()
            if (now - file.lastModified() > TOUCH_EVERY_MS) file.setLastModified(now)
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
        // Cada una en espera retiene su bitmap: con disco lento la fila crecía sin fin.
        if (pendingWrites.value >= MAX_PENDING) return
        if (!writing.add(file.name)) return
        pendingWrites.update { it + 1 }
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
                if (ok) writeMicro(microDir, file.name, scaled)
                if (scaled !== source) scaled.recycle()
                if (source !== bitmap) source.recycle()
            } catch (_: Exception) {
            } finally {
                writing.remove(file.name)
                pendingWrites.update { it - 1 }
            }
        }
    }

    companion object {
        const val PARAM = "entre_poster"
        const val PARAM_MICRO = "entre_micro"
        const val DIR = "entre_thumbs"
        private const val MICRO_DIR = "m"
        private const val EXT = ".webp"
        private const val MIN_HEIGHT = 160
        private const val MAX_HEIGHT = 450
        private const val QUALITY = 80
        private const val MICRO_WIDTH = 16
        private const val MICRO_QUALITY = 60
        private const val MAX_PENDING = 12
        private const val TOUCH_EVERY_MS = 3 * 24 * 60 * 60_000L

        private val pendingWrites = MutableStateFlow(0)
        /** Miniaturas esperando a guardarse en disco. */
        val pending: StateFlow<Int> = pendingWrites
        /** Tamaño con el que la precarga decodifica para dejar la miniatura hecha. */
        const val PREFETCH_WIDTH = 300
        const val PREFETCH_HEIGHT = MAX_HEIGHT

        private fun dirOf(context: Context) = context.noBackupFilesDir.resolve(DIR)

        private fun legacyDirOf(context: Context) = context.cacheDir.resolve(DIR)

        private fun microDirOf(dir: File) = dir.resolve(MICRO_DIR)

        private fun fileIn(dir: File, url: String) = File(dir, keyOf(url) + EXT)

        /** Nombre (sin extensión) de la miniatura de [url], la de la tarjeta ([cardImage]). */
        fun keyOf(url: String): String = url.encodeUtf8().sha1().hex()

        /** Ya hay miniatura de [url] (la de la tarjeta). Toca el disco: fuera del hilo principal. */
        fun exists(context: Context, url: String): Boolean = fileIn(dirOf(context), url).isFile

        /** Claves de las miniaturas guardadas, leyendo la carpeta una sola vez. Toca el disco. */
        fun storedKeys(context: Context): Set<String> = keysIn(dirOf(context))

        /** Claves de las vistas previas guardadas. Toca el disco. */
        fun storedMicroKeys(context: Context): Set<String> = keysIn(microDirOf(dirOf(context)))

        private fun keysIn(dir: File): Set<String> =
            dir.list()?.asSequence()?.filter { it.endsWith(EXT) }?.map { it.removeSuffix(EXT) }?.toHashSet() ?: emptySet()

        /** Borra miniaturas y vistas previas que no estén en [keep]; devuelve cuántas. Toca el disco. */
        fun prune(context: Context, keep: Set<String>): Int {
            var removed = 0
            val dir = dirOf(context)
            for (folder in listOf(dir, microDirOf(dir))) {
                for (name in folder.list().orEmpty()) {
                    if (!name.endsWith(EXT) || name.removeSuffix(EXT) in keep) continue
                    if (File(folder, name).delete() && folder == dir) removed++
                }
            }
            return removed
        }

        /** Arma la vista previa de [key] a partir de su miniatura. Toca el disco: fuera del hilo principal. */
        fun makeMicro(context: Context, key: String): Boolean {
            val dir = dirOf(context)
            val source = File(dir, key + EXT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0) return false
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MICRO_WIDTH * 2) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return false
            return try {
                writeMicro(microDirOf(dir), source.name, bitmap)
            } finally {
                bitmap.recycle()
            }
        }

        private fun writeMicro(microDir: File, name: String, bitmap: Bitmap): Boolean = try {
            val height = (MICRO_WIDTH * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtLeast(1)
            val micro = Bitmap.createScaledBitmap(bitmap, MICRO_WIDTH, height, true)
            microDir.mkdirs()
            val tmp = File(microDir, "$name.tmp")
            val ok = tmp.outputStream().use { out -> micro.compress(webpLossy(), MICRO_QUALITY, out) }
            if (micro !== bitmap) micro.recycle()
            if (ok && tmp.renameTo(File(microDir, name))) true
            else {
                tmp.delete()
                false
            }
        } catch (_: Exception) {
            false
        }

        @Suppress("DEPRECATION")
        private fun webpLossy() =
            if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

        /** Borra todas las miniaturas. Toca el disco: fuera del hilo principal. */
        fun clear(context: Context) {
            dirOf(context).deleteRecursively()
            legacyDirOf(context).deleteRecursively()
        }
    }
}
