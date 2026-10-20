package com.entretv.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val edge: Dp
)

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val width = config.screenWidthDp
    return remember(isTv, width) {
        when {
            isTv -> DeviceProfile(
                isTv = true, compactRail = false, railWidth = 220.dp,
                // Unas dos filas y media a la vista en 1080p (960x540 dp).
                posterWidth = 100.dp, posterHeight = 148.dp,
                liveWidth = 160.dp, liveHeight = 90.dp,
                continueWidth = 190.dp, continueHeight = 107.dp,
                itemGap = 14.dp, rowGap = 6.dp, edge = 28.dp
            )
            width >= 600 -> DeviceProfile(
                isTv = false, compactRail = false, railWidth = 196.dp,
                posterWidth = 136.dp, posterHeight = 198.dp,
                liveWidth = 190.dp, liveHeight = 107.dp,
                continueWidth = 230.dp, continueHeight = 129.dp,
                itemGap = 14.dp, rowGap = 10.dp, edge = 20.dp
            )
            else -> DeviceProfile(
                isTv = false, compactRail = true, railWidth = 96.dp,
                posterWidth = 108.dp, posterHeight = 158.dp,
                liveWidth = 150.dp, liveHeight = 84.dp,
                continueWidth = 168.dp, continueHeight = 94.dp,
                itemGap = 10.dp, rowGap = 4.dp, edge = 12.dp
            )
        }
    }
}
