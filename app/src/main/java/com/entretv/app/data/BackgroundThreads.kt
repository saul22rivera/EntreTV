package com.entretv.app.data

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hilos de prioridad baja para el trabajo pesado en segundo plano (bajar y guardar el
 * catálogo): si se reproduce algo a la vez, el video tiene la CPU primero y no se traba.
 */
fun backgroundExecutor(name: String, threads: Int): ExecutorService {
    val count = AtomicInteger()
    return Executors.newFixedThreadPool(threads) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "$name-${count.incrementAndGet()}")
    }
}
