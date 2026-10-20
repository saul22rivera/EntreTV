package com.entretv.app.data.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.entretv.app.data.local.DownloadDao
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.VodMovieEntity
import com.entretv.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Películas y episodios guardados en el almacenamiento interno de la app: no los ve otra app,
 * no entran en la copia de seguridad y se borran al desinstalar. Van de a una, en orden de llegada.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val directory: File get() = File(context.noBackupFilesDir, "downloads").apply { mkdirs() }

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(contentKey: String): Flow<DownloadEntity?> = dao.observe(contentKey)

    fun observeSeries(seriesKey: String): Flow<List<DownloadEntity>> = dao.observeSeries(seriesKey)

    suspend fun get(contentKey: String): DownloadEntity? = dao.get(contentKey)

    /** URI del archivo si la descarga terminó y el archivo sigue ahí. */
    suspend fun localUri(contentKey: String): String? {
        val download = dao.get(contentKey)?.takeIf { it.isDone } ?: return null
        val file = File(directory, download.fileName)
        return if (file.isFile && file.length() > 0) Uri.fromFile(file).toString() else null
    }

    suspend fun downloadMovie(movie: VodMovieEntity, title: String) {
        add(
            DownloadEntity(
                contentKey = movie.id,
                kind = "MOVIE",
                playlistId = movie.playlistId,
                title = title,
                subtitle = movie.categoryName,
                imageUrl = movie.icon,
                sourceUrl = movie.streamUrl,
                fileName = fileNameFor(movie.id, movie.streamUrl)
            )
        )
    }

    suspend fun downloadEpisodes(episodes: List<EpisodeEntity>, seriesTitle: String, seriesCover: String) {
        val playlistId = episodes.firstOrNull()?.seriesKey?.substringBefore(':') ?: return
        episodes.forEach { episode ->
            add(
                DownloadEntity(
                    contentKey = episode.id,
                    kind = "EPISODE",
                    playlistId = playlistId,
                    title = seriesTitle,
                    subtitle = "T${episode.season}:E${episode.episodeNum} · ${episode.title}",
                    imageUrl = episode.imageUrl.ifBlank { seriesCover },
                    seriesKey = episode.seriesKey,
                    season = episode.season,
                    episodeNum = episode.episodeNum,
                    sourceUrl = episode.streamUrl,
                    fileName = fileNameFor(episode.id, episode.streamUrl)
                ),
                enqueue = false
            )
        }
        enqueue()
    }

    private suspend fun add(download: DownloadEntity, enqueue: Boolean = true) {
        val existing = dao.get(download.contentKey)
        when (existing?.status) {
            DownloadStatus.DONE, DownloadStatus.QUEUED, DownloadStatus.RUNNING -> return
            DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                dao.setStatus(download.contentKey, DownloadStatus.QUEUED)
            else -> dao.upsert(download)
        }
        if (enqueue) enqueue()
    }

    suspend fun pause(contentKey: String) {
        val download = dao.get(contentKey) ?: return
        if (download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.RUNNING) {
            dao.setStatus(contentKey, DownloadStatus.PAUSED)
        }
    }

    suspend fun resume(contentKey: String) {
        val download = dao.get(contentKey) ?: return
        if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.FAILED) {
            dao.setStatus(contentKey, DownloadStatus.QUEUED)
            enqueue()
        }
    }

    /** Si estaba bajándose, el worker ve que la fila ya no existe y borra el archivo parcial. */
    suspend fun delete(contentKey: String) {
        val download = dao.get(contentKey) ?: return
        dao.delete(contentKey)
        File(directory, download.fileName).delete()
        File(directory, download.fileName + PART_SUFFIX).delete()
    }

    /** Al abrir la app: retoma lo que quedó en cola o a medias. */
    fun resumePending() {
        scope.launch {
            if (dao.pendingCount() > 0) enqueue()
        }
    }

    /** Cambió la preferencia de red: se reprograma la cola con la condición nueva. */
    fun onNetworkPreferenceChanged() {
        scope.launch { enqueue(replace = true) }
    }

    private suspend fun enqueue(replace: Boolean = false) {
        val wifiOnly = settings.downloadWifiOnly.first()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()
        // APPEND: si ya hay un worker corriendo, el nuevo arranca al terminar y toma lo que quede.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun fileNameFor(contentKey: String, url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "")
            .lowercase().takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "mp4"
        return contentKey.replace(Regex("[^A-Za-z0-9_-]"), "_") + "." + ext
    }

    companion object {
        const val WORK_NAME = "entre_downloads"
        const val PART_SUFFIX = ".part"
    }
}
