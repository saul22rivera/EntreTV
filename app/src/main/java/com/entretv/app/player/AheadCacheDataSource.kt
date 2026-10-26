package com.entretv.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Lo que lee el reproductor cuando la película se baja adelantada: solo el disco, nunca la red
 * (la única conexión es la de [VodAheadDownloader]). Si el byte pedido no llegó, espera; si
 * queda lejos de lo que se está bajando (un salto), le pide a la descarga que se mueva ahí.
 *
 * No bloquea tramos de la caché: si lo hiciera, la descarga no podría escribirlos.
 */
@OptIn(UnstableApi::class)
class AheadCacheDataSource(private val downloader: VodAheadDownloader) : BaseDataSource(/* isNetwork = */ false) {

    class Factory(private val downloader: VodAheadDownloader) : DataSource.Factory {
        override fun createDataSource(): DataSource = AheadCacheDataSource(downloader)
    }

    private var uri: Uri? = null
    private var url = ""
    private var position = 0L
    private var bytesRemaining = 0L
    private var file: RandomAccessFile? = null
    private var fileStart = 0L
    private var fileEnd = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        url = dataSpec.uri.toString()
        transferInitializing(dataSpec)
        try {
            position = dataSpec.position
            downloader.ensure(url, position, downloader.isCached(url, position))
            val length = downloader.awaitContentLength(url)
            if (position > length) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, length - position)
            else length - position
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw AheadUnavailableException(e)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val raf = fileAt(position)
        val toRead = minOf(length.toLong(), bytesRemaining, fileEnd - position).toInt()
        val read = raf.read(buffer, offset, toRead)
        if (read < 0) throw EOFException()
        position += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        closeFile()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    /** Archivo del tramo que contiene [p], esperando a que llegue si todavía no está. */
    private fun fileAt(p: Long): RandomAccessFile {
        file?.let { if (p in fileStart until fileEnd) return it }
        closeFile()
        while (true) {
            val span = try {
                downloader.cachedSpanAt(url, p)
            } catch (e: RuntimeException) {
                throw AheadUnavailableException(e)
            }
            val spanFile = span?.file
            if (span != null && spanFile != null) {
                val raf = RandomAccessFile(spanFile, "r")
                raf.seek(p - span.position)
                file = raf
                fileStart = span.position
                fileEnd = span.position + span.length
                return raf
            }
            try {
                downloader.ensure(url, p, cached = false)
            } catch (e: RuntimeException) {
                throw AheadUnavailableException(e)
            }
            downloader.sleepInterruptibly()
        }
    }

    private fun closeFile() {
        file?.let { runCatching { it.close() } }
        file = null
    }
}
