package com.entretv.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entretv.app.R
import com.entretv.app.data.net.SpeedResult
import com.entretv.app.ui.SpeedTestState
import com.entretv.app.ui.theme.EntreBrand
import java.util.Locale

@Composable
fun SpeedTestDialog(state: SpeedTestState?, onAgain: () -> Unit, onDismiss: () -> Unit) {
    val done = state as? SpeedTestState.Done
    PanelDialog(
        title = stringResource(R.string.speed_test_title),
        onDismiss = onDismiss,
        extraAction = if (done != null) stringResource(R.string.speed_test_again) to onAgain else null
    ) {
        Column(Modifier.padding(bottom = 8.dp)) {
            when (state) {
                is SpeedTestState.Done -> SpeedResultView(state.result)
                else -> {
                    val phase = (state as? SpeedTestState.Running)?.phase ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(if (phase == 0) R.string.speed_test_phase_internet else R.string.speed_test_phase_server),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.speed_test_note), style = MaterialTheme.typography.bodySmall, color = EntreBrand.TextDim)
                }
            }
        }
    }
}

@Composable
private fun SpeedResultView(result: SpeedResult) {
    if (result.internetMbps == null && result.pingMs == null) {
        Text(stringResource(R.string.speed_test_offline), style = MaterialTheme.typography.bodyLarge)
        return
    }
    MeasureLine(stringResource(R.string.speed_test_internet), result.internetMbps, result.pingMs)
    Spacer(Modifier.height(6.dp))
    MeasureLine(stringResource(R.string.speed_test_server), result.serverMbps, result.serverPingMs)
    Spacer(Modifier.height(14.dp))
    result.usableMbps?.let {
        Text(stringResource(qualityFor(it)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
    }
    problemOf(result)?.let {
        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
    }
    if (result.unstable) {
        Text(stringResource(R.string.speed_test_unstable), style = MaterialTheme.typography.bodyMedium, color = EntreBrand.Accent)
    }
}

@Composable
private fun MeasureLine(label: String, mbps: Float?, pingMs: Int?) {
    val parts = buildList {
        if (mbps != null) add(stringResource(R.string.speed_test_mbps, formatMbps(mbps)))
        if (pingMs != null) add(stringResource(R.string.speed_test_ping, pingMs))
    }
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = EntreBrand.TextDim)
        Text(
            if (parts.isEmpty()) stringResource(R.string.speed_test_failed) else parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@StringRes
private fun qualityFor(mbps: Float): Int = when {
    mbps >= 25f -> R.string.speed_test_quality_4k
    mbps >= 10f -> R.string.speed_test_quality_fhd
    mbps >= 5f -> R.string.speed_test_quality_hd
    else -> R.string.speed_test_quality_sd
}

/** Dónde está el límite, si lo hay. */
@StringRes
private fun problemOf(result: SpeedResult): Int? {
    val home = result.internetMbps ?: return null
    val server = result.serverMbps
    return when {
        server != null && server < 25f && home >= server * 1.5f -> R.string.speed_test_problem_server
        home < 25f -> R.string.speed_test_problem_home
        else -> null
    }
}

private fun formatMbps(value: Float): String =
    if (value < 10f) String.format(Locale.getDefault(), "%.1f", value) else value.toInt().toString()
