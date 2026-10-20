package com.entretv.app.ui

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.os.ConfigurationCompat
import com.entretv.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Fecha y hora pequeña ("mar 20 oct · 14:35"); se actualiza al cambiar el minuto. */
@Composable
fun SmallClock(
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.85f)
) {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val dateFormat = remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale) }
    val timeFormat = remember(locale, is24h) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hma"), locale)
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val date = Date(now)
    Text(
        if (showDate) "${dateFormat.format(date)} · ${timeFormat.format(date)}" else timeFormat.format(date),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
    )
}

/** "Actualizado hoy 14:32", "ayer 9:10" o "18 oct 20:15"; [at] = 0 si nunca se actualizó. */
@Composable
fun lastUpdateLabel(at: Long): String {
    if (at <= 0L) return stringResource(R.string.home_never_updated)
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hma"), locale).format(Date(at))
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    fun Calendar.sameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val whenText = when {
        then.sameDay(today) -> stringResource(R.string.home_today_at, time)
        then.sameDay(yesterday) -> stringResource(R.string.home_yesterday_at, time)
        else -> SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(Date(at)) + " " + time
    }
    return stringResource(R.string.home_last_update, whenText)
}
