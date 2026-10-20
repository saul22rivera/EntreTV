package com.entretv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.entretv.app.data.local.HiddenItemEntity
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.AccountFields
import com.entretv.app.ui.CategoryInfo
import com.entretv.app.ui.Section
import com.entretv.app.ui.SortOrder
import com.entretv.app.ui.theme.EntreBrand

// ============================ Configuración ============================

@Composable
fun SettingsPanel(
    accountName: String,
    accountUser: String,
    onEditAccount: () -> Unit,
    onLogout: () -> Unit,
    appVersion: String,
    onCheckUpdates: () -> Unit,
    downloadWifiOnly: Boolean,
    onDownloadWifiOnlyChange: (Boolean) -> Unit,
    bufferSeconds: Int,
    maxBufferSeconds: Int,
    defaultSort: SortOrder,
    cleanNames: Boolean,
    hiddenCount: Int,
    pinSet: Boolean,
    unlocked: Boolean,
    onBufferChange: (Int) -> Unit,
    onMaxBufferChange: (Int) -> Unit,
    onDefaultSortChange: (SortOrder) -> Unit,
    onCleanNamesChange: (Boolean) -> Unit,
    onOrganize: () -> Unit,
    onHiddenItems: () -> Unit,
    onCreatePin: () -> Unit,
    onChangePin: () -> Unit,
    onRemovePin: () -> Unit,
    onLockNow: () -> Unit,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    PanelDialog(title = "Configuración", onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle("Cuenta")
            Text(
                if (accountName.isNotBlank() && accountName != accountUser) "$accountName · usuario $accountUser" else "Usuario $accountUser",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ActionLine("Cambiar de usuario", "Edita tu nombre, usuario o contraseña", onEditAccount)
            ActionLine("Cerrar sesión", "Vuelve a la pantalla de inicio de sesión", onLogout)

            Divider()
            SectionTitle("Reproducción")
            SettingLabel(
                "Búfer de arranque",
                "Segundos de video que se cargan antes de empezar y después de un corte."
            )
            SettingsRepository.BUFFER_OPTIONS.forEach { seconds ->
                RadioLine(bufferLabel(seconds), seconds == bufferSeconds) { onBufferChange(seconds) }
            }
            Spacer(Modifier.height(10.dp))
            SettingLabel(
                "Búfer máximo",
                "Cuánto video por delante se descarga. Más búfer aguanta mejor una conexión inestable."
            )
            SettingsRepository.MAX_BUFFER_OPTIONS.forEach { seconds ->
                RadioLine(maxBufferLabel(seconds), seconds == maxBufferSeconds) { onMaxBufferChange(seconds) }
            }

            Divider()
            SectionTitle("Catálogo")
            SettingLabel("Orden por defecto", "“Original” respeta el orden en que lo manda tu proveedor.")
            SortOrder.entries.forEach { order ->
                RadioLine(sortLabel(order), order == defaultSort) { onDefaultSortChange(order) }
            }
            Spacer(Modifier.height(6.dp))
            SwitchLine(
                title = "Limpiar nombres",
                subtitle = "Quita prefijos y etiquetas como “ES:”, “US|” o “[HD]”.",
                checked = cleanNames,
                onChange = onCleanNamesChange
            )
            ActionLine("Organizar categorías", "Ocultar, reordenar y marcar como adultos", onOrganize)
            ActionLine(
                "Títulos y canales ocultos",
                if (hiddenCount == 0) "No ocultaste nada" else "$hiddenCount ocultos",
                onHiddenItems
            )

            Divider()
            SectionTitle("Control parental")
            if (!pinSet) {
                Text(
                    "Con un PIN, las categorías de adultos quedan ocultas hasta que lo ingreses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EntreBrand.TextDim,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ActionLine("Crear PIN", "4 dígitos", onCreatePin)
            } else {
                Text(
                    if (unlocked) "Desbloqueado hasta cerrar la app." else "Contenido para adultos bloqueado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EntreBrand.TextDim,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (unlocked) ActionLine("Bloquear ahora", null, onLockNow)
                else ActionLine("Desbloquear", "Muestra el contenido para adultos en esta sesión", onUnlock)
                ActionLine("Cambiar PIN", null, onChangePin)
                ActionLine("Quitar PIN", null, onRemovePin)
            }

            Divider()
            SectionTitle("Descargas")
            SwitchLine(
                title = "Descargar solo con Wi‑Fi",
                subtitle = "Espera una red sin límite de datos (Wi‑Fi o cable) para no gastar datos móviles.",
                checked = downloadWifiOnly,
                onChange = onDownloadWifiOnlyChange
            )

            Divider()
            SectionTitle("Aplicación")
            ActionLine("Buscar actualizaciones", "Versión instalada: $appVersion", onCheckUpdates)
        }
    }
}

private fun bufferLabel(seconds: Int): String = when (seconds) {
    0 -> "0 s · arranca al instante, puede trabarse más"
    3 -> "3 s · equilibrado (recomendado)"
    5 -> "5 s · más estable"
    9 -> "9 s · máxima estabilidad, tarda más en arrancar"
    else -> "$seconds s"
}

private fun maxBufferLabel(seconds: Int): String = when (seconds) {
    15 -> "15 s · usa menos memoria"
    30 -> "30 s · equilibrado (recomendado)"
    60 -> "60 s · conexiones inestables; en vivo va un poco más atrasado"
    else -> "$seconds s"
}

private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.ORIGINAL -> "Original · como viene de la lista"
    SortOrder.AZ -> "A-Z · alfabético"
    SortOrder.RATING -> "Puntuación · mejor valorados primero"
    SortOrder.RECENT -> "Recientes · lo último agregado primero"
}

// ============================ Organizar categorías ============================

@Composable
fun OrganizeCategoriesDialog(
    section: Section,
    categories: List<CategoryInfo>,
    onSection: (Section) -> Unit,
    onToggleHidden: (CategoryInfo) -> Unit,
    onMove: (CategoryInfo, Int) -> Unit,
    onToggleAdult: (CategoryInfo) -> Unit,
    onResetOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    PanelDialog(title = "Organizar categorías", onDismiss = onDismiss, extraAction = "Orden original" to onResetOrder) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                listOf(Section.LIVE, Section.MOVIES, Section.SERIES).forEach { s ->
                    FilterChip(selected = s == section, onClick = { onSection(s) }, label = { Text(s.label) })
                }
            }
            Text(
                "Sube o baja con las flechas. El ojo oculta la categoría y el candado la marca como de adultos (se bloquea con el PIN).",
                style = MaterialTheme.typography.bodySmall,
                color = EntreBrand.TextDim,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (categories.isEmpty()) {
                Text(
                    "No hay categorías todavía. Actualiza el catálogo para traerlas.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(categories, key = { _, c -> c.name }) { index, c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            c.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (c.hidden) EntreBrand.TextDim else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onMove(c, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir")
                        }
                        IconButton(onClick = { onMove(c, 1) }, enabled = index < categories.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar")
                        }
                        IconButton(onClick = { onToggleHidden(c) }) {
                            Icon(
                                if (c.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (c.hidden) "Mostrar" else "Ocultar",
                                tint = if (c.hidden) EntreBrand.TextDim else Color.White
                            )
                        }
                        IconButton(onClick = { onToggleAdult(c) }) {
                            Icon(
                                if (c.adult) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (c.adult) "Quitar marca de adultos" else "Marcar como adultos",
                                tint = if (c.adult) EntreBrand.Accent else EntreBrand.TextDim
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================ Cuenta ============================

/** Cambiar nombre, usuario o contraseña. El servidor es fijo y no se muestra. */
@Composable
fun AccountDialog(
    initialName: String,
    initialUser: String,
    initialPass: String,
    saving: Boolean,
    error: String?,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }
    PanelDialog(
        title = "Cambiar de usuario",
        onDismiss = { if (!saving) onDismiss() },
        extraAction = (if (saving) "Verificando…" else "Guardar") to { if (!saving) onSave(name, user, pass) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Tus listas, tu progreso y Mi lista se conservan si es la misma cuenta.",
                style = MaterialTheme.typography.bodySmall,
                color = EntreBrand.TextDim,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AccountFields(
                name = name, onName = { name = it },
                user = user, onUser = { user = it },
                pass = pass, onPass = { pass = it },
                enabled = !saving,
                onDone = { onSave(name, user, pass) }
            )
            if (error != null) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

// ============================ Ocultos ============================

@Composable
fun HiddenItemsDialog(
    items: List<HiddenItemEntity>,
    onUnhide: (HiddenItemEntity) -> Unit,
    onDismiss: () -> Unit
) {
    PanelDialog(title = "Títulos y canales ocultos", onDismiss = onDismiss) {
        if (items.isEmpty()) {
            Text(
                "No ocultaste nada. Desde el menú de un póster (mantener presionado o tecla Menú) puedes ocultar un canal o un título.",
                style = MaterialTheme.typography.bodyMedium,
                color = EntreBrand.TextDim
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                itemsIndexed(items, key = { _, it -> it.contentKey }) { _, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(width = 44.dp, height = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EntreBrand.PanelHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.imageUrl.isNotEmpty()) {
                                AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.size(44.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (item.kind) {
                                    "LIVE" -> "Canal"
                                    "MOVIE" -> "Película"
                                    else -> "Serie"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = EntreBrand.TextDim
                            )
                        }
                        TextButton(onClick = { onUnhide(item) }) { Text("Mostrar") }
                    }
                }
            }
        }
    }
}

// ============================ PIN ============================

/**
 * Teclado numérico para el PIN, usable con el control remoto. Al completar los
 * dígitos llama a [onSubmit]; si devuelve un error lo muestra y limpia.
 */
@Composable
fun PinDialog(
    title: String,
    message: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember(title) { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    fun press(digit: Char) {
        if (pin.length >= ParentalControl.PIN_LENGTH) return
        pin += digit
        if (pin.length == ParentalControl.PIN_LENGTH) {
            val entered = pin
            pin = ""
            onSubmit(entered)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = EntreBrand.Panel,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.onPreviewKeyEvent { event ->
                // Los números del control también sirven.
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val digit = digitOf(event.key)
                when {
                    digit != null -> { press(digit); true }
                    event.key == Key.Backspace || event.key == Key.Delete -> { pin = pin.dropLast(1); true }
                    else -> false
                }
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = EntreBrand.TextDim, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(ParentalControl.PIN_LENGTH) { i ->
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (i < pin.length) EntreBrand.Accent else Color.White.copy(alpha = 0.18f))
                        )
                    }
                }
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 10.dp).heightIn(min = 18.dp)
                )
                Spacer(Modifier.height(8.dp))
                val rows = listOf("123", "456", "789")
                rows.forEachIndexed { r, keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        keys.forEachIndexed { c, k ->
                            PinKey(
                                label = k.toString(),
                                modifier = if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier
                            ) { press(k) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PinKey(label = "✕", description = "Cancelar", onClick = onDismiss)
                    PinKey(label = "0") { press('0') }
                    PinKey(icon = Icons.AutoMirrored.Filled.Backspace, description = "Borrar") { pin = pin.dropLast(1) }
                }
            }
        }
    }
}

private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

@Composable
private fun PinKey(
    label: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (focused) EntreBrand.Accent else Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        val tint = if (focused) EntreBrand.OnAccent else Color.White
        if (icon != null) Icon(icon, contentDescription = description, tint = tint)
        else Text(label.orEmpty(), color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

// ============================ Piezas comunes ============================

@Composable
private fun PanelDialog(
    title: String,
    onDismiss: () -> Unit,
    extraAction: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = EntreBrand.Panel,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f, fill = false)) { content() }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    extraAction?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
                    TextButton(onClick = onDismiss) { Text("Listo") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = EntreBrand.Accent,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingLabel(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EntreBrand.TextDim)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun focusOutline(source: MutableInteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    return if (focused) Modifier.background(Color.White.copy(alpha = 0.10f), shape).border(2.dp, Color.White, shape) else Modifier
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(focusOutline(source))
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(focusOutline(source))
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EntreBrand.TextDim)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionLine(title: String, subtitle: String?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(focusOutline(source))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EntreBrand.TextDim)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = EntreBrand.TextDim)
    }
}
