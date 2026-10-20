package com.entretv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.entretv.app.data.update.UpdateRepository
import com.entretv.app.data.update.UpdateState
import com.entretv.app.ui.theme.EntreBrand
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository
) : ViewModel() {
    val state = repository.state
    val manual = repository.manual
    val versionName: String get() = repository.currentVersionName

    fun checkAuto() = repository.check(manual = false)
    fun checkNow() = repository.check(manual = true)
    fun download() {
        when (val s = state.value) {
            is UpdateState.Available -> repository.download(s.info)
            is UpdateState.Failed -> s.info?.let(repository::download)
            else -> Unit
        }
    }
    fun install() = repository.install()
    fun refreshPermission() = repository.refreshPermission()
    fun dismiss() = repository.dismiss()
}

/**
 * Diálogo global de actualización. Busca versiones nuevas al abrir la app y se
 * oculta mientras [suppressed] (por ejemplo, durante la reproducción).
 */
@Composable
fun UpdateHost(suppressed: Boolean, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manual by viewModel.manual.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkAuto() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

    val visible = when (state) {
        UpdateState.Idle -> false
        UpdateState.Checking, UpdateState.UpToDate, is UpdateState.Failed -> manual
        else -> true
    }
    if (!visible || suppressed) return

    Dialog(onDismissRequest = viewModel::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = EntreBrand.Panel,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = EntreBrand.Accent)
                    Spacer(Modifier.padding(start = 10.dp))
                    Text(title(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                UpdateBody(state, viewModel.versionName)
                Spacer(Modifier.height(18.dp))
                UpdateActions(state, viewModel)
            }
        }
    }
}

private fun title(state: UpdateState): String = when (state) {
    UpdateState.Checking -> "Buscando actualizaciones"
    UpdateState.UpToDate -> "EntreTV está al día"
    is UpdateState.Available -> "Hay una versión nueva"
    is UpdateState.Downloading -> "Descargando actualización"
    is UpdateState.ReadyToInstall -> "Lista para instalar"
    is UpdateState.Failed -> "No se pudo actualizar"
    UpdateState.Idle -> ""
}

@Composable
private fun UpdateBody(state: UpdateState, current: String) {
    val dim = EntreBrand.TextDim
    when (state) {
        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 3.dp)
            Text("Consultando el servidor…", color = dim)
        }
        UpdateState.UpToDate -> Text("Tenés la última versión ($current).", color = dim)
        is UpdateState.Available -> {
            Text("Versión ${state.info.versionName} (tenés la $current).", color = dim)
            if (state.info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.info.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                )
            }
        }
        is UpdateState.Downloading -> {
            Text("Versión ${state.info.versionName}", color = dim)
            Spacer(Modifier.height(12.dp))
            val p = state.progress
            if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Text("${(p * 100).toInt()} %", color = dim, modifier = Modifier.padding(top = 6.dp))
            }
        }
        is UpdateState.ReadyToInstall -> Text(
            if (state.needsPermission)
                "Para instalar, permití que EntreTV instale apps: Ajustes › Apps › EntreTV › " +
                    "Instalar apps desconocidas (en algunas TV: Ajustes › Seguridad › Fuentes desconocidas). " +
                    "Después volvé y elegí Instalar."
            else "Se abrirá el instalador de Android. Tus listas y tu progreso se conservan.",
            color = dim
        )
        is UpdateState.Failed -> Text(state.message, color = dim)
        UpdateState.Idle -> Unit
    }
}

@Composable
private fun UpdateActions(state: UpdateState, viewModel: UpdateViewModel) {
    val primary = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        when (state) {
            is UpdateState.Available -> {
                EntreButton("Más tarde", null, onClick = viewModel::dismiss, compact = true)
                EntreButton("Actualizar", null, onClick = viewModel::download, primary = true, compact = true,
                    modifier = Modifier.focusRequester(primary))
            }
            is UpdateState.Downloading ->
                EntreButton("Cancelar", null, onClick = viewModel::dismiss, compact = true, modifier = Modifier.focusRequester(primary))
            is UpdateState.ReadyToInstall -> {
                EntreButton("Más tarde", null, onClick = viewModel::dismiss, compact = true)
                EntreButton(
                    if (state.needsPermission) "Dar permiso" else "Instalar", null,
                    onClick = { viewModel.install() }, primary = true, compact = true,
                    modifier = Modifier.focusRequester(primary)
                )
            }
            is UpdateState.Failed -> {
                EntreButton("Cerrar", null, onClick = viewModel::dismiss, compact = true,
                    modifier = if (state.info == null) Modifier.focusRequester(primary) else Modifier)
                if (state.info != null) {
                    EntreButton("Reintentar", null, onClick = viewModel::download, primary = true, compact = true,
                        modifier = Modifier.focusRequester(primary))
                }
            }
            else -> EntreButton("Cerrar", null, onClick = viewModel::dismiss, compact = true, modifier = Modifier.focusRequester(primary))
        }
    }
    LaunchedEffect(state::class) { runCatching { primary.requestFocus() } }
}
