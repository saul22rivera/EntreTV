package com.entretv.app

import android.app.ActivityManager
import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.sync.CatalogSyncWorker
import com.entretv.app.tv.TvHomePublisher
import com.entretv.app.ui.AppLanguage
import com.entretv.app.ui.isLowRamDevice
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

@HiltAndroidApp
class EntreTVApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloads: DownloadRepository
    @Inject lateinit var tvHome: dagger.Lazy<TvHomePublisher>
    @Inject lateinit var httpClient: dagger.Lazy<OkHttpClient>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
        AppLanguage.init(this)
        downloads.resumePending()
        // WorkManager abre su base al primer uso: fuera del hilo principal para no demorar el arranque.
        thread(name = "catalog-sync-schedule") {
            CatalogSyncWorker.schedule(this)
            if (TvHomePublisher.isSupported(this)) tvHome.get().start(appScope)
        }
    }

    // Las imágenes se decodifican al tamaño en pantalla (AsyncImage toma el del layout).
    // Con poca memoria: JPEG en RGB_565 (la mitad de bytes; exige bitmaps de software,
    // los de hardware siempre son ARGB_8888), sin fundido y caché en memoria del 10 % del heap
    // normal: con largeHeap, maxSizePercent se calcularía sobre largeMemoryClass.
    override fun newImageLoader(): ImageLoader {
        val lowRam = isLowRamDevice(this)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .apply {
                        if (lowRam) {
                            val heapMb = getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
                            maxSizeBytes(heapMb * 1024 * 1024 / 10)
                        } else {
                            maxSizePercent(0.15)
                        }
                    }
                    .build()
            }
            // Cliente de la app con despachador propio: el de fábrica deja 5 descargas por servidor
            // y los pósters vienen casi todos del mismo. Sin caché HTTP: Coil ya guarda en disco.
            .okHttpClient {
                httpClient.get().newBuilder()
                    .cache(null)
                    .dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 12 })
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("entre_coil"))
                    .maxSizeBytes((if (lowRam) 128L else 256L) * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(!lowRam)
            .apply { if (lowRam) allowHardware(false).allowRgb565(true) }
            .build()
    }
}
