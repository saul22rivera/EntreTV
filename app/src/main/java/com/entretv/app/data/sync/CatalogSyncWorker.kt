package com.entretv.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.tv.TvHomePublisher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Actualiza el catálogo poco después del corte de la mañana (y del de la noche), con la app
 * cerrada: al abrirla ya está al día y no hace falta bajar nada. Usa la misma regla que al
 * entrar, así que si ya se actualizó después del corte no vuelve a bajar. En Android TV
 * después renueva el canal de EntreTV del inicio (una vez por día como mucho).
 */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PlaylistRepository,
    private val tvHome: TvHomePublisher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        repository.refreshAll(force = false).fold(
            onSuccess = {
                tvHome.refreshChannel(force = false)
                Result.success()
            },
            onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success() }
        )

    companion object {
        private const val WORK_NAME = "catalog-sync"
        private const val MAX_ATTEMPTS = 3
        private const val RUN_MINUTE = 30

        /** Cada 12 h a partir de las 9:30 o las 21:30, lo que llegue primero. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(delayToNextRun(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun delayToNextRun(now: Long): Long {
            val next = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, PlaylistRepository.DAILY_CUTOFF_HOUR)
                set(Calendar.MINUTE, RUN_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (next.timeInMillis <= now) next.add(Calendar.HOUR_OF_DAY, 12)
            return next.timeInMillis - now
        }
    }
}
