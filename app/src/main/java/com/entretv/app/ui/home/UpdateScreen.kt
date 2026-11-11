package com.entretv.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.entretv.app.R
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.repo.SyncStep
import com.entretv.app.ui.UpdatePhase
import com.entretv.app.ui.UpdateScreenState
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark
import kotlinx.coroutines.delay

/** Pasado este tiempo aparece "Entrar sin esperar": un servidor lento no deja la app bloqueada. */
private const val UPDATE_SLOW_MS = 40_000L

private val DoneGreen = Color(0xFF7BD88F)
private val FailedRed = Color(0xFFFF8A80)

/** Orden en pantalla; una lista M3U solo trae En vivo. */
private val SECTIONS = listOf(PlaylistRepository.SECTION_MOVIES, PlaylistRepository.SECTION_SERIES, PlaylistRepository.SECTION_LIVE)

/**
 * Pantalla de actualización del catálogo, encima de Inicio: Inicio sigue armado debajo y al
 * quitarse vuelve el foco a donde estaba. Mientras se ve, tapa los toques y no deja salir el foco.
 * [state] null = oculta (se desvanece).
 */
@Composable
fun UpdateOverlay(state: UpdateScreenState?, isTv: Boolean, onEnterNow: () -> Unit, onRetry: () -> Unit) {
    // Durante el desvanecimiento se sigue dibujando lo último que se mostró.
    val last = remember { arrayOfNulls<UpdateScreenState>(1) }
    if (state != null) last[0] = state
    AnimatedVisibility(visible = state != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(350))) {
        last[0]?.let { UpdateScreen(it, active = state != null, isTv = isTv, onEnterNow = onEnterNow, onRetry = onRetry) }
    }
}

@Composable
private fun UpdateScreen(
    state: UpdateScreenState,
    active: Boolean,
    isTv: Boolean,
    onEnterNow: () -> Unit,
    onRetry: () -> Unit
) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        slow = false
        if (state.phase == UpdatePhase.RUNNING) {
            delay(UPDATE_SLOW_MS)
            slow = true
        }
    }
    val failed = state.phase == UpdatePhase.FAILED
    val canEnter = failed || (slow && state.phase == UpdatePhase.RUNNING)
    BackHandler(enabled = active) { if (canEnter) onEnterNow() }

    // Inicio sigue debajo con sus propios pedidos de foco: mientras esta pantalla esté activa,
    // el foco vuelve a ella (al botón principal si lo hay).
    val trap = remember { FocusRequester() }
    val primary = remember { FocusRequester() }
    var reclaim by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, canEnter, reclaim) {
        if (!active) return@LaunchedEffect
        withFrameNanos { }
        if (!(canEnter && runCatching { primary.requestFocus() }.isSuccess)) runCatching { trap.requestFocus() }
    }

    val shown = if (state.steps.isEmpty()) SECTIONS else SECTIONS.filter { it in state.steps }
    fun stepOf(section: String) = state.steps[section] ?: SyncStep.WAITING
    val target = when {
        state.phase != UpdatePhase.RUNNING || shown.isEmpty() -> 1f
        else -> shown.sumOf { stepWeight(stepOf(it)).toDouble() }.toFloat() / shown.size
    }
    val progress by animateFloatAsState(target, spring(stiffness = Spring.StiffnessVeryLow), label = "updateProgress")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1A33), EntreBrand.Ink)))
            .onFocusChanged { if (active && !it.hasFocus) reclaim++ }
            .focusRequester(trap)
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .focusable()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            EntreWordmark(fontSize = if (isTv) 44.sp else 36.sp)
            Spacer(Modifier.height(24.dp))
            Crossfade(state.phase, animationSpec = tween(300), label = "updateTitle") { phase ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (phase == UpdatePhase.DONE) {
                        BouncyIcon(Icons.Default.CheckCircle, DoneGreen, 48.dp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        stringResource(
                            when (phase) {
                                UpdatePhase.RUNNING -> R.string.update_title_running
                                UpdatePhase.DONE -> R.string.update_title_done
                                UpdatePhase.FAILED -> R.string.update_title_failed
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = EntreBrand.Text,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            when (phase) {
                                UpdatePhase.RUNNING -> R.string.update_subtitle_running
                                UpdatePhase.DONE -> R.string.update_subtitle_done
                                UpdatePhase.FAILED -> R.string.update_subtitle_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = EntreBrand.TextDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(EntreBrand.Panel)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                shown.forEach { section -> SectionLine(section, stepOf(section)) }
            }
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = if (failed) FailedRed else EntreBrand.Accent,
                trackColor = EntreBrand.PanelHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            if (failed) {
                val secondary = remember { FocusRequester() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .focusRequester(primary)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = secondary
                            }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.catalog_update_retry))
                    }
                    OutlinedButton(
                        onClick = onEnterNow,
                        modifier = Modifier
                            .focusRequester(secondary)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                left = primary
                                right = FocusRequester.Cancel
                            }
                    ) {
                        Text(stringResource(R.string.update_enter_anyway))
                    }
                }
            } else {
                AnimatedVisibility(visible = canEnter, enter = fadeIn()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
                        Button(
                            onClick = onEnterNow,
                            modifier = Modifier
                                .focusRequester(primary)
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                        ) {
                            Text(stringResource(R.string.update_enter_now))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.update_enter_now_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EntreBrand.TextDim,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Cuánto del avance de una sección ya está hecho, para que la barra se mueva sin saltos. */
private fun stepWeight(step: SyncStep): Float = when (step) {
    SyncStep.WAITING -> 0f
    SyncStep.DOWNLOADING -> 0.1f
    SyncStep.SAVING -> 0.75f
    SyncStep.DONE, SyncStep.FAILED -> 1f
}

@Composable
private fun SectionLine(section: String, step: SyncStep) {
    val (icon, label) = when (section) {
        PlaylistRepository.SECTION_MOVIES -> Icons.Default.Movie to R.string.section_movies
        PlaylistRepository.SECTION_SERIES -> Icons.Default.VideoLibrary to R.string.section_series
        else -> Icons.Default.LiveTv to R.string.section_live
    }
    val active = step == SyncStep.DOWNLOADING || step == SyncStep.SAVING
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        SectionIcon(icon, highlighted = active || step == SyncStep.DONE)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = EntreBrand.Text
            )
            Text(
                stringResource(
                    when (step) {
                        SyncStep.WAITING -> R.string.update_step_waiting
                        SyncStep.DOWNLOADING -> R.string.update_step_downloading
                        SyncStep.SAVING -> R.string.update_step_saving
                        SyncStep.DONE -> R.string.update_step_done
                        SyncStep.FAILED -> R.string.update_step_failed
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = when (step) {
                    SyncStep.DONE -> DoneGreen
                    SyncStep.FAILED -> FailedRed
                    else -> EntreBrand.TextDim
                }
            )
        }
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (step) {
                SyncStep.DOWNLOADING, SyncStep.SAVING ->
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = EntreBrand.Accent)
                SyncStep.DONE -> BouncyIcon(Icons.Default.CheckCircle, DoneGreen, 28.dp)
                SyncStep.FAILED -> BouncyIcon(Icons.Default.ErrorOutline, FailedRed, 28.dp)
                SyncStep.WAITING -> Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(EntreBrand.PanelHigh)
                )
            }
        }
    }
}

/** Ícono que entra con un pequeño rebote. */
@Composable
private fun BouncyIcon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    )
}

@Composable
private fun SectionIcon(icon: ImageVector, highlighted: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (highlighted) EntreBrand.Accent.copy(alpha = 0.18f) else EntreBrand.PanelHigh)
    ) {
        Icon(icon, contentDescription = null, tint = if (highlighted) EntreBrand.Accent else EntreBrand.TextDim, modifier = Modifier.size(24.dp))
    }
}
