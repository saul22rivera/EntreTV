package com.entretv.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.entretv.app.MainActivity
import com.entretv.app.R
import com.entretv.app.data.local.DownloadDao
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.player.PlayerManager
import com.entretv.app.ui.AppLanguage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Baja la cola de descargas de a una; retoma archivos parciales con Range. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val repository: DownloadRepository,
    client: OkHttpClient
) : CoroutineWorker(context, params) {

    // Sin caché: los videos son enormes y se guardan directo en el archivo de destino.
    private val http = client.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Textos de avisos y errores guardados, en el idioma de la app. */
    private val texts: Context by lazy { AppLanguage.wrap(applicationContext) }

    private enum class Outcome { DONE, STOPPED, FAILED, NETWORK }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ensureChannel()
        while (!isStopped) {
            val next = dao.nextPending() ?: break
            runCatching { setForeground(foregroundInfo(next, null)) }
            when (transfer(next)) {
                Outcome.NETWORK -> {
                    return@withContext if (runAttemptCount < MAX_ATTEMPTS) {
                        Result.retry()
                    } else {
                        dao.setStatus(next.contentKey, DownloadStatus.FAILED, texts.getString(R.string.download_error_no_connection))
                        Result.failure()
                    }
                }
                Outcome.STOPPED -> if (isStopped) break
                Outcome.DONE -> notifyFinished(next, ok = true)
                Outcome.FAILED -> notifyFinished(next, ok = false)
            }
        }
        Result.success()
    }

    private suspend fun transfer(item: DownloadEntity): Outcome {
        val key = item.contentKey
        val dir = repository.directory
        val part = File(dir, item.fileName + DownloadRepository.PART_SUFFIX)
        val target = File(dir, item.fileName)
        dao.setStatus(key, DownloadStatus.RUNNING)

        var existing = if (part.isFile) part.length() else 0L
        val request = Request.Builder()
            .url(item.sourceUrl)
            .header("User-Agent", PlayerManager.USER_AGENT)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 416 && existing > 0 && item.totalBytes in 1..existing) {
                    return finish(item, part, target)
                }
                if (!response.isSuccessful) {
                    return fail(key, texts.getString(R.string.download_error_http, response.code))
                }
                val body = response.body ?: return fail(key, texts.getString(R.string.download_error_no_file))
                val type = body.contentType()?.toString().orEmpty().lowercase()
                if ("mpegurl" in type || "dash" in type) {
                    return fail(key, texts.getString(R.string.download_error_streamed))
                }
                val append = response.code == 206
                if (!append) existing = 0L
                val length = body.contentLength()
                val total = if (length > 0) existing + length else -1L
                if (length > 0 && dir.usableSpace < length + FREE_SPACE_RESERVE) {
                    return fail(key, texts.getString(R.string.download_error_no_space))
                }
                dao.updateProgress(key, existing, total)

                var written = existing
                var lastUpdate = SystemClock.elapsedRealtime()
                FileOutputStream(part, append).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                                lastUpdate = now
                                val current = dao.get(key)
                                if (current == null) {
                                    part.delete()
                                    return Outcome.STOPPED
                                }
                                dao.updateProgress(key, written, total)
                                if (current.status != DownloadStatus.RUNNING) return Outcome.STOPPED
                                if (isStopped) {
                                    dao.setStatus(key, DownloadStatus.QUEUED)
                                    return Outcome.STOPPED
                                }
                                if (dir.usableSpace < FREE_SPACE_RESERVE) {
                                    return fail(key, texts.getString(R.string.download_error_no_space))
                                }
                                runCatching {
                                    notifications.notify(NOTIFICATION_ID, progressNotification(current, written to total))
                                }
                            }
                        }
                    }
                }
                if (dao.get(key) == null) {
                    part.delete()
                    return Outcome.STOPPED
                }
                dao.updateProgress(key, written, total)
                if (total > 0 && written < total) {
                    dao.setStatus(key, DownloadStatus.QUEUED, texts.getString(R.string.download_error_interrupted))
                    return Outcome.NETWORK
                }
                return finish(item, part, target)
            }
        } catch (e: IOException) {
            if (dao.get(key) == null) {
                part.delete()
                return Outcome.STOPPED
            }
            if (isStopped) {
                dao.setStatus(key, DownloadStatus.QUEUED)
                return Outcome.STOPPED
            }
            dao.setStatus(key, DownloadStatus.QUEUED, texts.getString(R.string.download_error_interrupted))
            return Outcome.NETWORK
        } catch (e: IllegalArgumentException) {
            return fail(key, texts.getString(R.string.download_error_bad_url))
        }
    }

    private suspend fun finish(item: DownloadEntity, part: File, target: File): Outcome {
        target.delete()
        if (!part.renameTo(target)) return fail(item.contentKey, texts.getString(R.string.download_error_save))
        dao.updateProgress(item.contentKey, target.length(), target.length())
        dao.setStatus(item.contentKey, DownloadStatus.DONE)
        return Outcome.DONE
    }

    private suspend fun fail(key: String, message: String): Outcome {
        dao.setStatus(key, DownloadStatus.FAILED, message)
        return Outcome.FAILED
    }

    private fun ensureChannel() {
        // Si el canal ya existe solo se actualiza el nombre (por si cambió el idioma de la app).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, texts.getString(R.string.download_title), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun foregroundInfo(item: DownloadEntity, progress: Pair<Long, Long>?): ForegroundInfo {
        val notification = progressNotification(item, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun progressNotification(item: DownloadEntity, progress: Pair<Long, Long>?): android.app.Notification {
        val (done, total) = progress ?: (item.downloadedBytes to item.totalBytes)
        val name = item.displayName(texts)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(texts.getString(R.string.download_notification_title, name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
        if (total > 0) {
            val percent = (done * 100 / total).toInt()
            builder.setProgress(100, percent, false)
                .setContentText(texts.getString(R.string.download_notification_progress, percent, formatBytes(done), formatBytes(total)))
        } else {
            builder.setProgress(0, 0, true).setContentText(formatBytes(done))
        }
        return builder.build()
    }

    private suspend fun notifyFinished(item: DownloadEntity, ok: Boolean) {
        val name = item.displayName(texts)
        val error = dao.get(item.contentKey)?.error.orEmpty()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(texts.getString(if (ok) R.string.download_notification_done else R.string.download_failed))
            .setContentText(if (ok) name else "$name: $error")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { notifications.notify(item.contentKey.hashCode(), notification) }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4101
        private const val MAX_ATTEMPTS = 8
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 1_000L
        private const val FREE_SPACE_RESERVE = 200L * 1024 * 1024
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} MB"
            else -> "${(bytes / 1024).coerceAtLeast(0)} KB"
        }
    }
}
