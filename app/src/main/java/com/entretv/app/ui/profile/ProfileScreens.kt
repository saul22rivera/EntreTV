package com.entretv.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.entretv.app.R
import com.entretv.app.data.local.ProfileEntity
import com.entretv.app.data.profile.PinChange
import com.entretv.app.data.profile.PinCheck
import com.entretv.app.data.profile.ProfileRepository
import com.entretv.app.ui.EntreButton
import com.entretv.app.ui.animateFocusScale
import com.entretv.app.ui.home.ActionLine
import com.entretv.app.ui.home.PinDialog
import com.entretv.app.ui.home.SwitchLine
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreWordmark

/** Colores de los avatares; el perfil guarda el índice. */
private val PROFILE_COLORS = listOf(
    Color(0xFFE5484D), Color(0xFF3E63DD), Color(0xFF30A46C), Color(0xFFF5A524),
    Color(0xFF8E4EC6), Color(0xFF12A594), Color(0xFFD6409F), Color(0xFF6E7B8B)
)

fun profileColor(avatar: Int): Color = PROFILE_COLORS[Math.floorMod(avatar, PROFILE_COLORS.size)]

/** El perfil creado al actualizar no tiene nombre propio. */
@Composable
fun profileDisplayName(name: String): String = name.ifBlank { stringResource(R.string.profile_default_name) }

/** Lo que el menú y Configuración muestran del perfil activo. */
data class ProfileMenu(val name: String, val avatar: Int, val multiple: Boolean, val askOnStart: Boolean)

/** Acciones de perfiles que Inicio ofrece; [guardAdult] protege lo de adultos en el perfil de niños. */
class ProfileActions(
    val onSwitch: () -> Unit,
    val onManage: () -> Unit,
    val onAskOnStartChange: (Boolean) -> Unit,
    val guardAdult: (() -> Unit) -> Unit
)

/**
 * "¿Quién está viendo?", la gestión de perfiles y los PIN, encima de todo. En el arranque
 * ([ProfileUiState.startup]) Atrás cierra la app.
 */
@Composable
fun ProfilesHost(viewModel: ProfileViewModel, onExitApp: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val screen = ui.screen
    val prompt = ui.pin
    // Solo se lee la base si hay algo de perfiles en pantalla.
    val profiles = if (screen != null || prompt != null) viewModel.profiles.collectAsStateWithLifecycle().value else null

    if (screen != null) {
        val activeId by viewModel.activeId.collectAsStateWithLifecycle()
        Dialog(
            onDismissRequest = { viewModel.back(onExitApp) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E1830), EntreBrand.Ink)))
            ) {
                if (profiles != null) key(screen) { CompositionLocalProvider(LocalContentColor provides Color.White) {
                    when (screen) {
                        ProfileScreen.Picker, ProfileScreen.Manage -> ProfileGrid(
                            profiles = profiles,
                            activeId = activeId,
                            manage = screen == ProfileScreen.Manage,
                            onChoose = viewModel::choose,
                            onEdit = viewModel::edit,
                            onAdd = { viewModel.addProfile(screen) },
                            onManage = viewModel::openManage,
                            onDone = { viewModel.back(onExitApp) }
                        )
                        is ProfileScreen.Edit -> ProfileEditor(
                            profile = screen.profileId?.let { id -> profiles.firstOrNull { it.id == id } },
                            count = profiles.size,
                            onSave = { name, avatar, kids, pin -> viewModel.save(screen.profileId, name, avatar, kids, pin, screen.back) },
                            onCancel = { viewModel.back(onExitApp) },
                            onDelete = { screen.profileId?.let(viewModel::delete) }
                        )
                    }
                } }
            }
        }
    }

    if (prompt != null) {
        val profileId = when (prompt) {
            is PinPrompt.Enter -> prompt.profileId
            is PinPrompt.Edit -> prompt.profileId
            is PinPrompt.Adult -> null
        }
        val name = profileDisplayName(profiles?.firstOrNull { it.id == profileId }?.name.orEmpty())
        PinDialog(
            title = if (prompt is PinPrompt.Adult) stringResource(R.string.profile_pin_adult_title)
            else stringResource(R.string.profile_pin_enter_title, name),
            message = stringResource(
                when (prompt) {
                    is PinPrompt.Enter -> R.string.profile_pin_enter_message
                    is PinPrompt.Edit -> R.string.profile_pin_edit_message
                    is PinPrompt.Adult -> R.string.profile_pin_adult_message
                }
            ),
            error = when (val e = prompt.error) {
                is PinCheck.Wrong -> pluralStringResource(R.plurals.profile_pin_wrong, e.attemptsLeft, e.attemptsLeft)
                is PinCheck.Locked -> stringResource(R.string.profile_pin_locked, e.seconds)
                else -> null
            },
            onSubmit = viewModel::submitPin,
            onDismiss = viewModel::dismissPin
        )
    }

    if (ui.adultConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.confirmAdult(false) },
            title = { Text(stringResource(R.string.profile_adult_confirm_title)) },
            text = { Text(stringResource(R.string.profile_adult_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAdult(true) }) { Text(stringResource(R.string.profile_adult_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmAdult(false) }) { Text(stringResource(R.string.profile_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileGrid(
    profiles: List<ProfileEntity>,
    activeId: String,
    manage: Boolean,
    onChoose: (ProfileEntity) -> Unit,
    onEdit: (ProfileEntity) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onDone: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val tile = if (compact) 92.dp else 124.dp
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    val focusId = profiles.firstOrNull { it.id == activeId }?.id ?: profiles.firstOrNull()?.id
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        EntreWordmark(fontSize = 30.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(if (manage) R.string.profile_manage_title else R.string.profile_who_is_watching),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            profiles.forEach { p ->
                ProfileTile(
                    name = profileDisplayName(p.name),
                    avatar = p.avatar,
                    kids = p.isKids,
                    locked = p.hasPin,
                    editing = manage,
                    current = !manage && p.id == focusId,
                    size = tile,
                    modifier = if (p.id == focusId) Modifier.focusRequester(focus) else Modifier,
                    onClick = { if (manage) onEdit(p) else onChoose(p) }
                )
            }
            if (profiles.size < ProfileRepository.MAX_PROFILES) {
                AddTile(size = tile, onClick = onAdd)
            }
        }
        Spacer(Modifier.height(28.dp))
        if (manage) {
            EntreButton(stringResource(R.string.profile_done), Icons.Default.Check, onClick = onDone, primary = true)
        } else {
            EntreButton(stringResource(R.string.profile_manage), Icons.Default.Edit, onClick = onManage)
        }
        if (profiles.size >= ProfileRepository.MAX_PROFILES) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.profile_limit, ProfileRepository.MAX_PROFILES),
                style = MaterialTheme.typography.bodySmall,
                color = EntreBrand.TextDim
            )
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    avatar: Int,
    kids: Boolean,
    locked: Boolean,
    editing: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Último perfil usado: en el celular no hay foco que lo señale. */
    current: Boolean = false,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFocusScale(focused, 1.08f, "profileTile")
    val highlighted = focused || current
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(size + 28.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(profileColor(avatar))
                .then(
                    when {
                        focused -> Modifier.border(3.dp, Color.White, shape)
                        current -> Modifier.border(3.dp, EntreBrand.Accent, shape)
                        else -> Modifier
                    }
                )
        ) {
            Text(initialOf(name), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Black)
            if (editing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.profile_edit), tint = Color.White, modifier = Modifier.size(size * 0.32f))
            }
            if (locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.profile_locked),
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            color = if (highlighted) Color.White else EntreBrand.TextDim,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (kids) {
            Text(
                stringResource(R.string.profile_kids_badge),
                color = EntreBrand.Accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AddTile(size: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(size + 28.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(size)
                .clip(shape)
                .background(Color.White.copy(alpha = if (focused) 0.22f else 0.08f))
                .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.4f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.profile_add),
            color = if (focused) Color.White else EntreBrand.TextDim,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun initialOf(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

@Composable
private fun ProfileEditor(
    profile: ProfileEntity?,
    count: Int,
    onSave: (String, Int, Boolean, PinChange) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val defaultName = if (profile == null) stringResource(R.string.profile_new_default_name, count + 1) else profileDisplayName(profile.name)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var avatar by rememberSaveable { mutableIntStateOf(profile?.avatar ?: (count % PROFILE_COLORS.size)) }
    var kids by rememberSaveable { mutableStateOf(profile?.isKids ?: false) }
    var pinChange by remember { mutableStateOf<PinChange>(PinChange.Keep) }
    // null: sin pedir; "": primer ingreso del PIN nuevo; si no, el primero para confirmar.
    var pinStep by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val hasPin = when (pinChange) {
        PinChange.Keep -> profile?.hasPin == true
        PinChange.Remove -> false
        is PinChange.Set -> true
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    // Al cambiar el PIN se reemplazan sus filas: el foco vuelve a esa sección y no al nombre (que abriría el teclado).
    val pinFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    var refocusPin by remember { mutableIntStateOf(0) }
    LaunchedEffect(refocusPin) {
        if (refocusPin == 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { pinFocus.requestFocus() }
    }

    fun save() {
        val trimmed = name.trim()
        // El perfil sin nombre propio sigue mostrándose con el nombre por defecto en cada idioma.
        val stored = if (profile != null && profile.name.isBlank() && trimmed == defaultName) "" else trimmed.ifEmpty { defaultName }
        onSave(stored, avatar, kids, pinChange)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                stringResource(if (profile == null) R.string.profile_new_title else R.string.profile_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(profileColor(avatar))
                ) {
                    Text(initialOf(name), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileRepository.NAME_MAX) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
                    // El campo de texto se queda con las flechas: con el control remoto arriba/abajo salen de él.
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            val direction = when (event.key) {
                                Key.DirectionDown -> FocusDirection.Down
                                Key.DirectionUp -> FocusDirection.Up
                                else -> return@onPreviewKeyEvent false
                            }
                            if (event.type == KeyEventType.KeyDown) {
                                keyboard?.hide()
                                focusManager.moveFocus(direction)
                            }
                            true
                        }
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.profile_color), style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PROFILE_COLORS.forEachIndexed { index, color ->
                    ColorDot(
                        color = color,
                        selected = index == avatar,
                        description = stringResource(R.string.profile_color_option, index + 1),
                        modifier = if (index == avatar) Modifier.focusRequester(firstFocus) else Modifier
                    ) { avatar = index }
                }
            }
            Spacer(Modifier.height(14.dp))
            SwitchLine(
                title = stringResource(R.string.profile_kids),
                subtitle = stringResource(R.string.profile_kids_desc),
                checked = kids,
                onChange = { kids = it }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.profile_pin_section).uppercase(),
                color = EntreBrand.Accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            Column(Modifier.focusRequester(pinFocus)) {
                if (!hasPin) {
                    ActionLine(stringResource(R.string.profile_pin_add), stringResource(R.string.profile_pin_add_desc)) {
                        pinError = null
                        pinStep = ""
                    }
                } else {
                    Text(
                        stringResource(R.string.profile_pin_on),
                        style = MaterialTheme.typography.bodySmall,
                        color = EntreBrand.TextDim,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    ActionLine(stringResource(R.string.profile_pin_change), null) {
                        pinError = null
                        pinStep = ""
                    }
                    ActionLine(stringResource(R.string.profile_pin_remove), null) {
                        pinChange = PinChange.Remove
                        refocusPin++
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Al bajar a los botones se llega a Guardar.
                modifier = Modifier
                    .focusProperties {
                        onEnter = {
                            runCatching { saveFocus.requestFocus() }
                            cancelFocusChange()
                        }
                    }
                    .focusGroup()
            ) {
                EntreButton(
                    stringResource(R.string.profile_save),
                    Icons.Default.Check,
                    onClick = ::save,
                    modifier = Modifier.focusRequester(saveFocus),
                    primary = true
                )
                EntreButton(stringResource(R.string.profile_cancel), null, onClick = onCancel)
                if (profile != null && count > 1) {
                    EntreButton(stringResource(R.string.profile_delete), Icons.Default.Delete, onClick = { confirmDelete = true })
                }
            }
        }
    }

    pinStep?.let { first ->
        val mismatch = stringResource(R.string.profile_pin_mismatch)
        PinDialog(
            title = stringResource(if (first.isEmpty()) R.string.profile_pin_new_title else R.string.profile_pin_repeat_title),
            message = stringResource(if (first.isEmpty()) R.string.profile_pin_new_message else R.string.profile_pin_repeat_message),
            error = pinError,
            onSubmit = { pin ->
                when {
                    first.isEmpty() -> {
                        pinError = null
                        pinStep = pin
                    }
                    pin == first -> {
                        pinChange = PinChange.Set(pin)
                        pinError = null
                        pinStep = null
                        refocusPin++
                    }
                    else -> {
                        pinError = mismatch
                        pinStep = ""
                    }
                }
            },
            onDismiss = {
                pinStep = null
                pinError = null
                refocusPin++
            }
        )
    }

    if (confirmDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title, profileDisplayName(profile.name))) },
            text = { Text(stringResource(R.string.profile_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.profile_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.profile_cancel)) } }
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClickLabel = description, onClick = onClick)
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
