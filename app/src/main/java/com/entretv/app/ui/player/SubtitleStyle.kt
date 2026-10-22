@file:OptIn(UnstableApi::class)

package com.entretv.app.ui.player

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.entretv.app.R
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SettingsRepository

val SUBTITLE_SIZE_OPTIONS = listOf(
    SettingsRepository.SUBTITLE_SMALL to R.string.player_subtitle_small,
    SettingsRepository.SUBTITLE_NORMAL to R.string.player_subtitle_normal,
    SettingsRepository.SUBTITLE_LARGE to R.string.player_subtitle_large,
    SettingsRepository.SUBTITLE_XLARGE to R.string.player_subtitle_xlarge
)

val SUBTITLE_COLOR_OPTIONS = listOf(
    SettingsRepository.SUBTITLE_COLOR_WHITE to R.string.subtitle_color_white,
    SettingsRepository.SUBTITLE_COLOR_YELLOW to R.string.subtitle_color_yellow
)

val SUBTITLE_BACKGROUND_OPTIONS = listOf(
    SettingsRepository.SUBTITLE_BG_NONE to R.string.subtitle_bg_none,
    SettingsRepository.SUBTITLE_BG_SHADOW to R.string.subtitle_bg_shadow,
    SettingsRepository.SUBTITLE_BG_SEMI to R.string.subtitle_bg_semi,
    SettingsRepository.SUBTITLE_BG_BLACK to R.string.subtitle_bg_black
)

val SUBTITLE_POSITION_OPTIONS = listOf(
    SettingsRepository.SUBTITLE_POS_LOW to R.string.subtitle_pos_low,
    SettingsRepository.SUBTITLE_POS_MID to R.string.subtitle_pos_mid,
    SettingsRepository.SUBTITLE_POS_HIGH to R.string.subtitle_pos_high
)

@StringRes
fun labelOf(options: List<Pair<Int, Int>>, value: Int): Int = options.firstOrNull { it.first == value }?.second ?: options.first().second

private const val YELLOW = 0xFFFFE01A.toInt()
private const val SEMI_BLACK = 0x99000000.toInt()

private fun textColor(prefs: PlayerPrefs): Int =
    if (prefs.subtitleColor == SettingsRepository.SUBTITLE_COLOR_YELLOW) YELLOW else android.graphics.Color.WHITE

private fun sizeScale(prefs: PlayerPrefs): Float = when (prefs.subtitleSize) {
    SettingsRepository.SUBTITLE_SMALL -> 0.8f
    SettingsRepository.SUBTITLE_LARGE -> 1.4f
    SettingsRepository.SUBTITLE_XLARGE -> 1.8f
    else -> 1f
}

/** El estilo de siempre (blanco, fondo semitransparente): ahí se respetan colores y posiciones del archivo. */
private fun PlayerPrefs.isDefaultSubtitleLook(): Boolean =
    subtitleColor == SettingsRepository.SUBTITLE_COLOR_WHITE && subtitleBackground == SettingsRepository.SUBTITLE_BG_SEMI

/** Tamaño, color, fondo y altura de los subtítulos según Configuración. */
fun SubtitleView.applyUserStyle(prefs: PlayerPrefs) {
    val color = textColor(prefs)
    val style = when (prefs.subtitleBackground) {
        SettingsRepository.SUBTITLE_BG_NONE -> CaptionStyleCompat(
            color, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null
        )
        SettingsRepository.SUBTITLE_BG_SHADOW -> CaptionStyleCompat(
            color, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, android.graphics.Color.BLACK, null
        )
        SettingsRepository.SUBTITLE_BG_BLACK -> CaptionStyleCompat(
            color, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK, null
        )
        else -> CaptionStyleCompat(
            color, SEMI_BLACK, android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null
        )
    }
    setStyle(style)
    setApplyEmbeddedFontSizes(false)
    // Con un estilo propio, los colores y fondos del proveedor lo taparían.
    setApplyEmbeddedStyles(prefs.isDefaultSubtitleLook())
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * sizeScale(prefs))
    setBottomPaddingFraction(
        when (prefs.subtitlePosition) {
            SettingsRepository.SUBTITLE_POS_MID -> 0.16f
            SettingsRepository.SUBTITLE_POS_HIGH -> 0.26f
            else -> SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
        }
    )
}

/** Muestra de cómo se verán los subtítulos, para Configuración. */
@Composable
fun SubtitlePreview(prefs: PlayerPrefs, modifier: Modifier = Modifier) {
    val color = Color(textColor(prefs))
    val background = when (prefs.subtitleBackground) {
        SettingsRepository.SUBTITLE_BG_SEMI -> Color(SEMI_BLACK)
        SettingsRepository.SUBTITLE_BG_BLACK -> Color.Black
        else -> Color.Transparent
    }
    val shadow = when (prefs.subtitleBackground) {
        SettingsRepository.SUBTITLE_BG_SHADOW -> Shadow(Color.Black, Offset(3f, 3f), 4f)
        SettingsRepository.SUBTITLE_BG_NONE -> Shadow(Color.Black, Offset(0f, 0f), 6f)
        else -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF3A4A5A), RoundedCornerShape(10.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Text(
            stringResource(R.string.subtitle_preview_text),
            style = TextStyle(
                color = color,
                fontSize = (16 * sizeScale(prefs)).sp,
                fontWeight = FontWeight.Medium,
                shadow = shadow
            ),
            modifier = Modifier
                .background(background)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
