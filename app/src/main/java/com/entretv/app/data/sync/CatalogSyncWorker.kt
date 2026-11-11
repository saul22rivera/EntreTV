package com.entretv.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Antes actualizaba el catálogo con la app cerrada (9:30 y 21:30). El catálogo ya no se
 * actualiza de fondo: solo al entrar a la app o con el botón Actualizar. La clase sigue para
 * que la tarea que quedó programada en equipos con versiones viejas termine sin hacer nada.
 */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        private const val WORK_NAME = "catalog-sync-v2"
        private const val LEGACY_WORK_NAME = "catalog-sync"

        /** Quita la actualización de fondo que programaron las versiones anteriores. */
        fun cancelScheduled(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(WORK_NAME)
            work.cancelUniqueWork(LEGACY_WORK_NAME)
        }
    }
}
