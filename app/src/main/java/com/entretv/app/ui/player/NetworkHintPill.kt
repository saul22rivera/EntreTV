package com.entretv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.entretv.app.R
import com.entretv.app.player.NetworkHint
import java.util.Locale

private val HintTint = Color(0xFFFFC857)

/**
 * Aviso de internet chico y semitransparente para una esquina: no tapa la imagen ni pide tocar
 * nada. Aparece y se va con un desvanecido. [compact] deja solo el ícono (mini reproductor).
 */
@Composable
fun NetworkHintPill(hint: NetworkHint?, modifier: Modifier = Modifier, compact: Boolean = false) {
    // El último aviso sigue dibujándose mientras se desvanece.
    val last = remember { arrayOfNulls<NetworkHint>(1) }
    if (hint != null) last[0] = hint
    AnimatedVisibility(
        visible = hint != null,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(700)),
        modifier = modifier
    ) {
        val shown = last[0] ?: return@AnimatedVisibility
        Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(50)) {
            if (compact) {
                Icon(
                    Icons.Default.NetworkCheck,
                    contentDescription = hintTitle(shown),
                    tint = HintTint,
                    modifier = Modifier.padding(5.dp).size(14.dp)
                )
                return@Surface
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = HintTint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(hintText(shown), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun hintTitle(hint: NetworkHint): String = stringResource(
    if (hint.kind == NetworkHint.Kind.SLOW) R.string.player_net_slow else R.string.player_net_unstable
)

@Composable
private fun hintText(hint: NetworkHint): String {
    if (hint.preparing) {
        return hint.percent?.let { stringResource(R.string.player_net_preparing_percent, it) }
            ?: stringResource(R.string.player_net_preparing)
    }
    val parts = mutableListOf(hintTitle(hint))
    val speed = hint.mbps
    val need = hint.needMbps
    if (speed != null && speed > 0f) {
        parts += if (need != null && hint.kind == NetworkHint.Kind.SLOW) {
            stringResource(R.string.player_net_speed_need, mbps(speed), mbps(need))
        } else {
            stringResource(R.string.player_net_speed, mbps(speed))
        }
    }
    hint.percent?.let { parts += stringResource(R.string.player_net_loading, it) }
    return parts.joinToString(" · ")
}

private fun mbps(value: Float): String =
    if (value < 10f) String.format(Locale.getDefault(), "%.1f", value) else value.toInt().toString()
