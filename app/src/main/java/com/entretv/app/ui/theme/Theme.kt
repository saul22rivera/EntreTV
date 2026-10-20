package com.entretv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object EntreBrand {
    val Accent = Color(0xFF8AB4F8)
    val OnAccent = Color(0xFF0B1B33)
    val Ink = Color(0xFF07080C)
    val Panel = Color(0xFF12151D)
    val PanelHigh = Color(0xFF1B202B)
    val Text = Color(0xFFE6EAF2)
    val TextDim = Color(0xFFB9C0CE)
}

private val EntreDark = darkColorScheme(
    primary = EntreBrand.Accent,
    onPrimary = EntreBrand.OnAccent,
    primaryContainer = Color(0xFF1A3A6B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF9CCAFF),
    onSecondary = EntreBrand.OnAccent,
    tertiary = EntreBrand.Accent,
    background = EntreBrand.Ink,
    onBackground = EntreBrand.Text,
    surface = EntreBrand.Panel,
    onSurface = EntreBrand.Text,
    surfaceVariant = EntreBrand.PanelHigh,
    onSurfaceVariant = EntreBrand.TextDim,
    surfaceContainer = EntreBrand.Panel,
    surfaceContainerHigh = EntreBrand.PanelHigh,
    surfaceContainerLow = EntreBrand.Ink,
    outline = Color(0xFF2A3040)
)

@Composable
fun EntreTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EntreDark, content = content)
}

@Composable
fun EntreWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 24.sp) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append("Entre") }
            withStyle(SpanStyle(color = EntreBrand.Accent)) { append("TV") }
        },
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp
    )
}
