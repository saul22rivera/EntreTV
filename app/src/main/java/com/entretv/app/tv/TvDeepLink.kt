package com.entretv.app.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.entretv.app.MainActivity

/**
 * Lo que abre una tarjeta de la pantalla de inicio de la TV: reproducir (Seguir viendo) o la
 * ficha (canal de EntreTV). Solo lleva ids internos; nunca direcciones de video ni la cuenta.
 */
data class TvDeepLink(val play: Boolean, val kind: String, val id: String) {

    companion object {
        private const val SCHEME = "entretv"
        private const val HOST_PLAY = "play"
        private const val HOST_DETAILS = "details"
        private const val PARAM_KIND = "kind"
        private const val PARAM_ID = "id"

        private val PLAY_KINDS = setOf("MOVIE", "EPISODE")
        private val DETAILS_KINDS = setOf("MOVIE", "SERIES")

        fun play(kind: String, id: String) = TvDeepLink(true, kind, id)
        fun details(kind: String, id: String) = TvDeepLink(false, kind, id)

        fun parse(intent: Intent?): TvDeepLink? {
            val data = intent?.data ?: return null
            if (data.scheme != SCHEME) return null
            val kind = data.getQueryParameter(PARAM_KIND) ?: return null
            val id = data.getQueryParameter(PARAM_ID)?.takeIf { it.isNotBlank() } ?: return null
            return when (data.host) {
                HOST_PLAY -> play(kind, id).takeIf { kind in PLAY_KINDS }
                HOST_DETAILS -> details(kind, id).takeIf { kind in DETAILS_KINDS }
                else -> null
            }
        }
    }

    /** Intent explícito a [MainActivity] en formato URI, como lo pide TvContract. */
    fun toIntentUri(context: Context): Uri {
        val data = Uri.Builder()
            .scheme(SCHEME)
            .authority(if (play) HOST_PLAY else HOST_DETAILS)
            .appendQueryParameter(PARAM_KIND, kind)
            .appendQueryParameter(PARAM_ID, id)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, data, context, MainActivity::class.java)
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }
}
