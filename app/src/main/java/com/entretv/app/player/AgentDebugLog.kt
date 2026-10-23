package com.entretv.app.player

// #region agent log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

/** Registro temporal de depuración (sesión 40ee51). Nunca recibe URLs ni credenciales. */
object AgentDebugLog {
    private const val ENDPOINT = "http://127.0.0.1:7728/ingest/7b9edf20-eef5-40f9-bb3e-126f34366829"
    private const val SESSION = "40ee51"
    private val URL_LIKE = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "AgentDebugLog").apply { isDaemon = true } }

    fun log(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        if (!com.entretv.app.BuildConfig.DEBUG) return
        val payload = JSONObject()
            .put("sessionId", SESSION)
            .put("runId", "run1")
            .put("hypothesisId", hypothesisId)
            .put("location", location)
            .put("message", message)
            .put("data", JSONObject(data.mapValues { (it.value?.toString() ?: "null").replace(URL_LIKE, "<url>") }))
            .put("timestamp", System.currentTimeMillis())
            .toString()
        android.util.Log.i("AgentDebugLog", payload)
        executor.execute {
            runCatching {
                val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2_000
                conn.readTimeout = 2_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Debug-Session-Id", SESSION)
                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}

internal fun agentFormat(f: androidx.media3.common.Format): Map<String, Any?> = mapOf(
    "mime" to f.sampleMimeType, "codecs" to f.codecs, "container" to f.containerMimeType,
    "size" to "${f.width}x${f.height}", "fps" to f.frameRate, "bitrate" to f.bitrate,
    "colorTransfer" to f.colorInfo?.colorTransfer, "colorSpace" to f.colorInfo?.colorSpace,
    "colorRange" to f.colorInfo?.colorRange, "rotation" to f.rotationDegrees,
    "roleFlags" to f.roleFlags, "selectionFlags" to f.selectionFlags
)
// #endregion
