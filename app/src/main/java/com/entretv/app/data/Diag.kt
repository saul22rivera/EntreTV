// #region agent log
package com.entretv.app.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.entretv.app.BuildConfig
import java.io.File
import kotlin.concurrent.thread

/** Diagnóstico temporal por logcat (etiqueta EntreDiag). Nunca registra URLs ni credenciales. */
object Diag {
    private const val TAG = "EntreDiag"

    fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, msg)
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Tamaño de cada carpeta de datos de la app, en MB. */
    fun storage(context: Context) {
        if (!BuildConfig.DEBUG) return
        thread(name = "diag-storage", priority = Thread.MIN_PRIORITY) {
            val roots = listOfNotNull(context.filesDir, context.noBackupFilesDir, context.cacheDir, context.getDatabasePath("x").parentFile)
            for (root in roots) {
                val children = root.listFiles().orEmpty()
                for (child in children) log("storage ${root.name}/${child.name} = ${size(child) / (1024 * 1024)} MB")
            }
        }
    }

    /** Si el hilo principal no responde en [STALL_MS], registra qué está haciendo (cada 500 ms mientras siga trabado). */
    fun watchMain() {
        if (!BuildConfig.DEBUG) return
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val mainThread = android.os.Looper.getMainLooper().thread
        thread(name = "diag-watchdog", isDaemon = true) {
            while (true) {
                val answered = java.util.concurrent.atomic.AtomicBoolean(false)
                val sent = now()
                main.post { answered.set(true) }
                Thread.sleep(STALL_MS)
                var reported = 0
                while (!answered.get()) {
                    val stack = mainThread.stackTrace.take(28).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                    log("main stalled ${now() - sent} ms: $stack")
                    if (++reported >= 40) break
                    Thread.sleep(500)
                }
                if (reported > 0) log("main free after ${now() - sent} ms")
                Thread.sleep(250)
            }
        }
    }

    private const val STALL_MS = 1_500L

    private fun size(file: File): Long =
        if (file.isFile) file.length() else file.listFiles().orEmpty().sumOf { size(it) }
}
// #endregion
