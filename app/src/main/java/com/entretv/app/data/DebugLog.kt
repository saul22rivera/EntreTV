package com.entretv.app.data

// #region agent log
import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/** Diagnóstico temporal (sesión 40ee51): registros NDJSON a la PC por la red local. Sin URLs ni datos de la cuenta. */
object DebugLog {
    private const val ENDPOINT = "http://192.168.0.110:7729/ingest"
    private const val SESSION = "40ee51"
    private const val RUN = "post-fix"
    private val sender = Executors.newSingleThreadExecutor { Thread(it, "agent-log").apply { isDaemon = true } }
    private val sampler = Executors.newSingleThreadScheduledExecutor { Thread(it, "agent-mem").apply { isDaemon = true } }
    @Volatile private var app: Context? = null
    private val URL_RE = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
    private val CRED_RE = Regex("""(?i)(username|password|user|pass)=[^&\s]+""")

    val netLoads = AtomicInteger(); val diskLoads = AtomicInteger(); val memLoads = AtomicInteger(); val errLoads = AtomicInteger()
    val netMs = AtomicLong(); val diskMs = AtomicLong(); val slowLoads = AtomicInteger()
    val thumbQueue = AtomicInteger()
    @Volatile var syncing = false
    @Volatile var workerRunning = false

    fun clean(text: String?): String = CRED_RE.replace(URL_RE.replace(text.orEmpty(), "<url>"), "$1=<x>").take(300)

    fun init(context: Context) {
        app = context.applicationContext
        val am = context.getSystemService(ActivityManager::class.java)
        log("App.onCreate", "start", "A,H", mapOf(
            "heapMaxMb" to Runtime.getRuntime().maxMemory() / 1048576,
            "memoryClass" to (am?.memoryClass ?: -1), "largeMemoryClass" to (am?.largeMemoryClass ?: -1),
            "lowRam" to (am?.isLowRamDevice ?: false), "pid" to Process.myPid()
        ))
        val crashFile = File(context.filesDir, "agent_crash.json")
        if (crashFile.isFile) {
            val line = runCatching { crashFile.readText() }.getOrNull()
            crashFile.delete()
            if (line != null) sender.execute { post(line) }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val frames = error.stackTrace.take(18).joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            val cause = generateSequence(error.cause) { it.cause }.take(3).joinToString(" <- ") { "${it.javaClass.name}: ${clean(it.message)}" }
            val line = json("crash", "uncaught exception", "C", mapOf(
                "thread" to thread.name, "type" to error.javaClass.name, "msg" to clean(error.message),
                "cause" to cause, "frames" to frames, "heapUsedMb" to usedMb(), "syncing" to syncing, "worker" to workerRunning
            ))
            runCatching { crashFile.writeText(line) }
            runCatching { post(line, timeoutMs = 2_000) }
            previous?.uncaughtException(thread, error)
        }
        sampler.scheduleWithFixedDelay({ sample() }, 3, 3, TimeUnit.SECONDS)
        sender.execute { diskBench(context) }
    }

    /** Velocidad del almacenamiento donde vive la app (hipótesis H: USB adoptado lento). */
    private fun diskBench(context: Context) {
        val file = File(context.noBackupFilesDir, "agent_bench.bin")
        try {
            val chunk = ByteArray(256 * 1024) { it.toByte() }
            val t0 = System.nanoTime()
            java.io.FileOutputStream(file).use { out -> repeat(32) { out.write(chunk) }; out.fd.sync() }
            val writeMs = (System.nanoTime() - t0) / 1_000_000
            val small = System.nanoTime()
            repeat(20) { i -> java.io.FileOutputStream(File(context.noBackupFilesDir, "agent_small_$i")).use { out -> out.write(chunk, 0, 20_000); out.fd.sync() } }
            val smallMs = (System.nanoTime() - small) / 1_000_000
            repeat(20) { i -> File(context.noBackupFilesDir, "agent_small_$i").delete() }
            log("DebugLog.diskBench", "disk speed", "H", mapOf(
                "dataDir" to (if (context.dataDir.path.startsWith("/mnt/expand")) "adopted-usb" else "internal"),
                "write8MbMs" to writeMs, "writeMBps" to (if (writeMs > 0) 8_000 / writeMs else -1),
                "twenty20KbSyncedMs" to smallMs
            ))
        } catch (e: Exception) {
            log("DebugLog.diskBench", "bench failed", "H", mapOf("type" to e.javaClass.name))
        } finally {
            file.delete()
        }
    }

    private fun usedMb(): Long { val r = Runtime.getRuntime(); return (r.totalMemory() - r.freeMemory()) / 1048576 }

    private fun sample() {
        val net = netLoads.getAndSet(0); val disk = diskLoads.getAndSet(0)
        val nMs = netMs.getAndSet(0); val dMs = diskMs.getAndSet(0)
        log("DebugLog.sample", "mem", "A,B,F,G", mapOf(
            "heapUsedMb" to usedMb(), "heapMaxMb" to Runtime.getRuntime().maxMemory() / 1048576,
            "nativeMb" to Debug.getNativeHeapAllocatedSize() / 1048576,
            "thumbQueue" to thumbQueue.get(), "syncing" to syncing, "worker" to workerRunning,
            "posterNet" to net, "posterNetAvgMs" to (if (net > 0) nMs / net else 0),
            "posterDisk" to disk, "posterDiskAvgMs" to (if (disk > 0) dMs / disk else 0),
            "posterMem" to memLoads.getAndSet(0), "posterErr" to errLoads.getAndSet(0), "posterSlow3s" to slowLoads.getAndSet(0)
        ))
    }

    fun log(location: String, message: String, hypothesis: String, data: Map<String, Any?>) {
        val line = json(location, message, hypothesis, data)
        runCatching { sender.execute { post(line) } }
    }

    private fun json(location: String, message: String, hypothesis: String, data: Map<String, Any?>): String =
        JSONObject().apply {
            put("sessionId", SESSION); put("runId", RUN); put("hypothesisId", hypothesis)
            put("location", location); put("message", message); put("timestamp", System.currentTimeMillis())
            put("data", JSONObject(data.mapValues { it.value ?: JSONObject.NULL }))
        }.toString()

    private fun post(line: String, timeoutMs: Int = 4_000) {
        try {
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Debug-Session-Id", SESSION)
            c.outputStream.use { it.write(line.toByteArray()) }
            c.responseCode
            c.disconnect()
        } catch (_: Exception) {
        }
    }
}
// #endregion
