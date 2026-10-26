package com.entretv.app.player

import android.content.Context
import androidx.media3.common.Format

/**
 * Recuerda qué arreglo de imagen funcionó para cada tipo de video (códec + resolución) en este
 * equipo, para aplicarlo solo la próxima vez:
 *  - [LEVEL_RESTART]: reiniciar el decodificador una vez al empezar.
 *  - [LEVEL_SOFTWARE]: empezar con el decodificador por software (sin modo túnel).
 */
class VideoFixMemory(context: Context) {
    companion object {
        const val LEVEL_NONE = 0
        const val LEVEL_RESTART = 1
        const val LEVEL_SOFTWARE = 2

        fun keyFor(format: Format): String? {
            val mime = format.sampleMimeType ?: return null
            val bucket = if (format.height > 1080) "uhd" else "sd"
            return "$mime|$bucket"
        }
    }

    private val prefs by lazy { context.getSharedPreferences("entre_video_fixes", Context.MODE_PRIVATE) }

    fun levelFor(key: String): Int = prefs.getInt(key, LEVEL_NONE)

    fun remember(key: String, level: Int) {
        prefs.edit().putInt(key, level).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
