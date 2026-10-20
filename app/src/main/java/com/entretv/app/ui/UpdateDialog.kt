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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.entretv.app.R
import com.entretv.app.data.update.UpdateRepository
import com.entretv.app.data.update.UpdateState
import com.entretv.app.data.update.isRequired
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
    fun refreshPermission() {
        val before = state.value
        repository.refreshPermission()
        // Volvió de Ajustes con el permiso dado: en una obligatoria se sigue solo.
        val after = state.value
        if (before is UpdateState.ReadyToInstall && before.needsPermission &&
            after is UpdateState.ReadyToInstall && !after.needsPermission && after.info.isRequired
        ) repository.install()
    }
    fun dismiss() = repository.dismiss()
}

/**
 * Diálogo global de actualización. Busca versiones nuevas en silencio en cada inicio y se
 * oculta mientras [suppressed] (por ejemplo, durante la reproducción), salvo que la
 * actualización sea obligatoria: entonces bloquea la app hasta instalarla.
 */
@Composable
fun UpdateHost(suppressed: Boolean, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manual by viewModel.manual.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkAuto() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

    val required = state.isRequired
    val visible = when (state) {
        UpdateState.Idle -> false
        UpdateState.Checking, UpdateState.UpToDate -> manual
        is UpdateState.Failed -> manual || required
        else -> true
    }
    if (!visible || (suppressed && !required)) return

    Dialog(
        onDismissRequest = viewModel::dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !required,
            dismissOnClickOutside = !required
        )
    ) {
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

@Composable
private fun title(state: UpdateState): String = when {
    state.isRequired && state !is UpdateState.Failed -> stringResource(R.string.update_required_title)
    else -> plainTitle(state)
}

@Composable
private fun plainTitle(state: UpdateState): String = when (state) {
    UpdateState.Checking -> stringResource(R.string.update_checking_title)
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date_title)
    is UpdateState.Available -> stringResource(R.string.update_available_title)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading_title)
    is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready_title)
    is UpdateState.Failed -> stringResource(R.string.update_failed_title)
    UpdateState.Idle -> ""
}

@Composable
private fun UpdateBody(state: UpdateState, current: String) {
    val dim = EntreBrand.TextDim
    when (state) {
        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.update_checking), color = dim)
        }
        UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date, current), color = dim)
        is UpdateState.Available -> {
            Text(stringResource(R.string.update_available, state.info.versionName, current), color = dim)
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
            Text(
                if (state.info.isRequired) stringResource(R.string.update_required_downloading, state.info.versionName)
                else stringResource(R.string.update_version, state.info.versionName),
                color = dim
            )
            Spacer(Modifier.height(12.dp))
            val p = state.progress
            if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.download_percent, (p * 100).toInt()), color = dim, modifier = Modifier.padding(top = 6.dp))
            }
        }
        is UpdateState.ReadyToInstall -> Text(
            stringResource(if (state.needsPermission) R.string.update_permission_needed else R.string.update_installer_note),
            color = dim
        )
        is UpdateState.Failed -> Text(state.message.asString(), color = dim)
        UpdateState.Idle -> Unit
    }
}

@Composable
private fun UpdateActions(state: UpdateState, viewModel: UpdateViewModel) {
    val primary = remember { FocusRequester() }
    val required = state.isRequired
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        when (state) {
            is UpdateState.Available -> {
                if (!required) EntreButton(stringResource(R.string.update_later), null, onClick = viewModel::dismiss, compact = true)
                EntreButton(stringResource(R.string.update_update), null, onClick = viewModel::download, primary = true, compact = true,
                    modifier = Modifier.focusRequester(primary))
            }
            is UpdateState.Downloading ->
                if (required) {
                    CircularProgressIndicator(Modifier.focusRequester(primary), strokeWidth = 3.dp)
                } else {
                    EntreButton(stringResource(R.string.update_cancel), null, onClick = viewModel::dismiss, compact = true,
                        modifier = Modifier.focusRequester(primary))
                }
            is UpdateState.ReadyToInstall -> {
                if (!required) EntreButton(stringResource(R.string.update_later), null, onClick = viewModel::dismiss, compact = true)
                EntreButton(
                    stringResource(if (state.needsPermission) R.string.update_grant_permission else R.string.update_install), null,
                    onClick = { viewModel.install() }, primary = true, compact = true,
                    modifier = Modifier.focusRequester(primary)
                )
            }
            is UpdateState.Failed -> {
                if (!required) EntreButton(stringResource(R.string.update_close), null, onClick = viewModel::dismiss, compact = true,
                    modifier = if (state.info == null) Modifier.focusRequester(primary) else Modifier)
                if (state.info != null) {
                    EntreButton(stringResource(R.string.update_retry), null, onClick = viewModel::download, primary = true, compact = true,
                        modifier = Modifier.focusRequester(primary))
                }
            }
            else -> EntreButton(stringResource(R.string.update_close), null, onClick = viewModel::dismiss, compact = true,
                modifier = Modifier.focusRequester(primary))
        }
    }
    LaunchedEffect(state::class) { runCatching { primary.requestFocus() } }
}
