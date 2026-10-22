package com.entretv.app.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Publica el canal de EntreTV y "Seguir viendo" con la app cerrada. */
@HiltWorker
class TvHomeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val publisher: TvHomePublisher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        publisher.refreshChannel(force = true)
        publisher.publishWatchNext()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "tv-home"

        fun enqueue(context: Context) {
            if (!TvHomePublisher.isSupported(context)) return
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TvHomeWorker>().build())
        }
    }
}

/** El launcher pide los programas al instalar la app o al reiniciar sus datos. */
class TvHomeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TvContractCompat.ACTION_INITIALIZE_PROGRAMS) TvHomeWorker.enqueue(context)
    }
}
