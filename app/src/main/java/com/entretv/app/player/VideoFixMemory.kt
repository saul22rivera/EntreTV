package com.entretv.app.player

import android.content.Context
import androidx.media3.common.Format

/**
 * Recuerda qué arreglo de imagen funcionó para cada tipo de video (códec + resolución) en este
 * equipo, para aplicarlo solo la próxima vez:
 *  - [LEVEL_RESTART]: reiniciar el decodificador una vez al empezar.
 *  - [LEVEL_SOFTWARE]: empezar con el decodificador por software (sin modo túnel).
 *
 * Cada arreglo vale [MAX_AGE_MS]: uno viejo (de una falla pasajera) dejaba todo ese tipo de video
 * por software para siempre, y por software 50 cuadros por segundo se ven robóticos.
 */
class VideoFixMemory(context: Context) {
    companion object {
        const val LEVEL_NONE = 0
        const val LEVEL_RESTART = 1
        const val LEVEL_SOFTWARE = 2
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60_000L
        private const val AT_SUFFIX = "@at"

        fun keyFor(format: Format): String? {
            val mime = format.sampleMimeType ?: return null
            val bucket = if (format.height > 1080) "uhd" else "sd"
            return "$mime|$bucket"
        }
    }

    private val prefs by lazy { context.getSharedPreferences("entre_video_fixes", Context.MODE_PRIVATE) }

    fun levelFor(key: String): Int {
        val level = prefs.getInt(key, LEVEL_NONE)
        if (level == LEVEL_NONE) return LEVEL_NONE
        // Los de versiones anteriores no tienen fecha: se toman como viejos.
        val at = prefs.getLong(key + AT_SUFFIX, 0L)
        if (System.currentTimeMillis() - at > MAX_AGE_MS) {
            prefs.edit().remove(key).remove(key + AT_SUFFIX).apply()
            return LEVEL_NONE
        }
        return level
    }

    fun remember(key: String, level: Int) {
        prefs.edit().putInt(key, level).putLong(key + AT_SUFFIX, System.currentTimeMillis()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
