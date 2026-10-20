package com.entretv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.entretv.app.R
import com.entretv.app.data.repo.AccountException
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Si hay una cuenta guardada se entra directo; si no, se pide iniciar sesión. */
enum class Session { LOADING, LOGGED_OUT, LOGGED_IN }

@HiltViewModel
class SessionViewModel @Inject constructor(repository: PlaylistRepository) : ViewModel() {
    val session: StateFlow<Session> = repository.playlists
        .map { if (it.isEmpty()) Session.LOGGED_OUT else Session.LOGGED_IN }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session.LOADING)
}

data class LoginUiState(val loading: Boolean = false, val error: UiText? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(private val repository: PlaylistRepository) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun login(name: String, user: String, pass: String) {
        if (_ui.value.loading) return
        if (user.isBlank() || pass.isBlank()) {
            _ui.value = LoginUiState(error = uiText(R.string.login_missing_fields))
            return
        }
        _ui.value = LoginUiState(loading = true)
        viewModelScope.launch {
            repository.login(name.trim(), user.trim(), pass.trim())
                .onSuccess { _ui.value = LoginUiState() }
                .onFailure { _ui.value = LoginUiState(error = (it as? AccountException)?.text ?: uiText(R.string.login_failed)) }
        }
    }

    fun clearError() {
        if (_ui.value.error != null) _ui.value = _ui.value.copy(error = null)
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1830), EntreBrand.Ink)))
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EntreWordmark(fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_subtitle),
                color = EntreBrand.TextDim,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            AccountFields(
                name = name, onName = { name = it; viewModel.clearError() },
                user = user, onUser = { user = it; viewModel.clearError() },
                pass = pass, onPass = { pass = it; viewModel.clearError() },
                enabled = !ui.loading,
                onDone = { viewModel.login(name, user, pass) }
            )
            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it.asString(),
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))
            if (ui.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.login_verifying), color = EntreBrand.TextDim, modifier = Modifier.padding(start = 12.dp))
                }
            } else {
                EntreButton(
                    stringResource(R.string.login_enter), Icons.AutoMirrored.Filled.Login,
                    onClick = { viewModel.login(name, user, pass) },
                    primary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Nombre, usuario y contraseña; se usa al iniciar sesión y al cambiar de usuario en Ajustes. */
@Composable
fun AccountFields(
    name: String, onName: (String) -> Unit,
    user: String, onUser: (String) -> Unit,
    pass: String, onPass: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit
) {
    val focus = LocalFocusManager.current
    var showPass by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name, onValueChange = onName, enabled = enabled,
            label = { Text(stringResource(R.string.login_name)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = user, onValueChange = onUser, enabled = enabled,
            label = { Text(stringResource(R.string.login_user)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pass, onValueChange = onPass, enabled = enabled,
            label = { Text(stringResource(R.string.login_password)) }, singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(if (showPass) R.string.login_hide_password else R.string.login_show_password)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
