package com.entretv.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Imagen en imagen: estado compartido entre la actividad y la pantalla del reproductor. */
object PipController {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Zona del video en pantalla, para que la animación de entrada salga desde ahí. */
    @Volatile
    var videoBounds: Rect? = null

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun onModeChanged(inPip: Boolean) {
        _active.value = inPip
    }

    fun enter(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isSupported(activity)) return false
        return runCatching { activity.enterPictureInPictureMode(params()) }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun params(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        videoBounds?.takeIf { !it.isEmpty }?.let { bounds ->
            // El sistema exige que el rectángulo tenga la misma proporción que la ventana PiP.
            val height = bounds.width() * 9 / 16
            val top = bounds.centerY() - height / 2
            builder.setSourceRectHint(Rect(bounds.left, top, bounds.right, top + height))
        }
        return builder.build()
    }
}
