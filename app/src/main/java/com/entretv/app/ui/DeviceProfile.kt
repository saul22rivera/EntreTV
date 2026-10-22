package com.entretv.app.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.entretv.app.BuildConfig

/** Medidas del inicio según el dispositivo: celular, tablet o TV. */
data class DeviceProfile(
    val isTv: Boolean,
    /** En celular el panel lateral apila icono y texto para ocupar menos ancho. */
    val compactRail: Boolean,
    val railWidth: Dp,
    val posterWidth: Dp,
    val posterHeight: Dp,
    val liveWidth: Dp,
    val liveHeight: Dp,
    val continueWidth: Dp,
    val continueHeight: Dp,
    val itemGap: Dp,
    val rowGap: Dp,
    val edge: Dp,
    /** Celular, aunque esté acostado: acostado usa las medidas de tablet por el ancho. */
    val isPhone: Boolean = false,
    /** Ver [isLowRamDevice]. */
    val lowRam: Boolean = false
)

/** Mismo criterio que [rememberDeviceProfile], para quien no está en Compose. */
fun isTvDevice(context: Context): Boolean =
    context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

private const val LOW_RAM_TOTAL_BYTES = 2_500_000_000L
private const val LOW_RAM_HEAP_MB = 192
/** Solo en debug: `adb shell setprop debug.entretv.lowram 1` (o 0) y reiniciar la app. */
private const val LOW_RAM_OVERRIDE_PROP = "debug.entretv.lowram"

@Volatile
private var lowRamCached: Boolean? = null

/**
 * Equipo con poca memoria (TV box baratos, Fire TV Stick): el sistema lo marca así, el heap
 * normal de una app es de 192 MB o menos, o tiene menos de ~2,5 GB de RAM. Ahí las imágenes
 * van en RGB_565, el foco no anima y la caché y el búfer de video son más chicos.
 * Se calcula una vez por proceso.
 */
fun isLowRamDevice(context: Context): Boolean =
    lowRamCached ?: detectLowRam(context.applicationContext).also { lowRamCached = it }

private fun detectLowRam(context: Context): Boolean {
    if (BuildConfig.DEBUG) {
        when (systemProperty(LOW_RAM_OVERRIDE_PROP)) {
            "1" -> return true
            "0" -> return false
        }
    }
    val am = context.getSystemService(ActivityManager::class.java) ?: return false
    if (am.isLowRamDevice || am.memoryClass <= LOW_RAM_HEAP_MB) return true
    val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
    return info.totalMem in 1 until LOW_RAM_TOTAL_BYTES
}

@SuppressLint("PrivateApi")
private fun systemProperty(key: String): String? = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as? String
}.getOrNull()

/** Agrandado al enfocar; en equipos con poca memoria queda fijo y el foco lo marca el borde. */
@Composable
fun animateFocusScale(focused: Boolean, scale: Float, label: String): State<Float> {
    val lowRam = isLowRamDevice(LocalContext.current)
    return animateFloatAsState(if (focused && !lowRam) scale else 1f, label = label)
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val config = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val width = config.screenWidthDp
    val phone = !isTv && config.smallestScreenWidthDp < 600
    return remember(isTv, width, phone) {
        val lowRam = isLowRamDevice(context)
        when {
            isTv -> DeviceProfile(
                isTv = true, compactRail = false, railWidth = 220.dp,
                // Unas dos filas y media a la vista en 1080p (960x540 dp).
                posterWidth = 100.dp, posterHeight = 148.dp,
                liveWidth = 160.dp, liveHeight = 74.dp,
                continueWidth = 190.dp, continueHeight = 107.dp,
                itemGap = 14.dp, rowGap = 6.dp, edge = 28.dp
            )
            width >= 600 -> DeviceProfile(
                isTv = false, compactRail = false, railWidth = 196.dp,
                posterWidth = 136.dp, posterHeight = 198.dp,
                liveWidth = 190.dp, liveHeight = 86.dp,
                continueWidth = 230.dp, continueHeight = 129.dp,
                itemGap = 14.dp, rowGap = 10.dp, edge = 20.dp,
                isPhone = phone
            )
            else -> DeviceProfile(
                isTv = false, compactRail = true, railWidth = 96.dp,
                posterWidth = 108.dp, posterHeight = 158.dp,
                liveWidth = 150.dp, liveHeight = 66.dp,
                continueWidth = 168.dp, continueHeight = 94.dp,
                itemGap = 10.dp, rowGap = 4.dp, edge = 12.dp,
                isPhone = true
            )
        }.copy(lowRam = lowRam)
    }
}
