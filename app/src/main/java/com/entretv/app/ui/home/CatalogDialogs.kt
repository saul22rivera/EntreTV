package com.entretv.app.ui.home

import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.entretv.app.R
import com.entretv.app.data.local.HiddenItemEntity
import com.entretv.app.data.settings.AccountInfo
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.PlayerPrefs
import com.entretv.app.data.settings.SettingsRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.entretv.app.ui.AccountFields
import com.entretv.app.ui.CategoryInfo
import com.entretv.app.ui.Section
import com.entretv.app.ui.SortOrder
import com.entretv.app.ui.label
import com.entretv.app.ui.player.SUBTITLE_BACKGROUND_OPTIONS
import com.entretv.app.ui.player.SUBTITLE_COLOR_OPTIONS
import com.entretv.app.ui.player.SUBTITLE_POSITION_OPTIONS
import com.entretv.app.ui.player.SUBTITLE_SIZE_OPTIONS
import com.entretv.app.ui.player.SubtitlePreview
import com.entretv.app.ui.theme.EntreBrand

// ============================ Configuración ============================

@Composable
fun SettingsPanel(
    accountName: String,
    accountUser: String,
    accountInfo: AccountInfo?,
    onEditAccount: () -> Unit,
    onLogout: () -> Unit,
    /** null = sin perfiles (no se muestra la sección). */
    profileName: String?,
    multipleProfiles: Boolean,
    askProfileOnStart: Boolean,
    onSwitchProfile: () -> Unit,
    onManageProfiles: () -> Unit,
    onAskProfileOnStartChange: (Boolean) -> Unit,
    appVersion: String,
    onCheckUpdates: () -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    showClock: Boolean,
    onShowClockChange: (Boolean) -> Unit,
    playerPrefs: PlayerPrefs,
    onContentLanguageChange: (String) -> Unit,
    onSubtitlesByDefaultChange: (Boolean) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    onSubtitleColorChange: (Int) -> Unit,
    onSubtitleBackgroundChange: (Int) -> Unit,
    onSubtitlePositionChange: (Int) -> Unit,
    onLiveFormatChange: (String) -> Unit,
    onDecoderChange: (Int) -> Unit,
    isTv: Boolean,
    onFrameRateModeChange: (Int) -> Unit,
    onTunnelingChange: (Boolean) -> Unit,
    onSkipIntroChange: (Boolean) -> Unit,
    refreshMode: Int,
    onRefreshModeChange: (Int) -> Unit,
    lastRefreshLabel: String,
    onRefreshNow: () -> Unit,
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
    PanelDialog(title = stringResource(R.string.settings_title), onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AccountCard(accountName, accountUser, accountInfo)
            ActionLine(stringResource(R.string.settings_switch_user), stringResource(R.string.settings_switch_user_desc), onEditAccount)
            ActionLine(stringResource(R.string.settings_logout), stringResource(R.string.settings_logout_desc), onLogout)

            if (profileName != null) {
                Divider()
                SectionTitle(stringResource(R.string.settings_section_profiles))
                ActionLine(
                    stringResource(R.string.settings_switch_profile),
                    stringResource(R.string.settings_switch_profile_desc, profileName),
                    onSwitchProfile
                )
                ActionLine(stringResource(R.string.settings_manage_profiles), stringResource(R.string.settings_manage_profiles_desc), onManageProfiles)
                if (multipleProfiles) {
                    SwitchLine(
                        title = stringResource(R.string.settings_ask_profile),
                        subtitle = stringResource(R.string.settings_ask_profile_desc),
                        checked = askProfileOnStart,
                        onChange = onAskProfileOnStartChange
                    )
                }
            }

            Divider()
            SectionTitle(stringResource(R.string.settings_section_language))
            SettingLabel(stringResource(R.string.settings_app_language), stringResource(R.string.settings_app_language_desc))
            APP_LANGUAGES.forEach { (code, label) ->
                RadioLine(label, appLanguage == code) { onAppLanguageChange(code) }
            }
            Spacer(Modifier.height(10.dp))
            SettingLabel(
                stringResource(R.string.settings_content_language),
                stringResource(R.string.settings_content_language_desc)
            )
            SettingsRepository.CONTENT_LANGUAGES.forEach { code ->
                RadioLine(stringResource(contentLanguageLabel(code)), playerPrefs.contentLanguage == code) { onContentLanguageChange(code) }
            }
            Spacer(Modifier.height(6.dp))
            SwitchLine(
                title = stringResource(R.string.settings_subtitles),
                subtitle = if (playerPrefs.subtitlesByDefault)
                    stringResource(R.string.settings_subtitles_on, stringResource(contentLanguageInline(playerPrefs.contentLanguage)))
                else stringResource(R.string.settings_subtitles_off),
                checked = playerPrefs.subtitlesByDefault,
                onChange = onSubtitlesByDefaultChange
            )

            Divider()
            SectionTitle(stringResource(R.string.settings_section_subtitles))
            Text(
                stringResource(R.string.settings_subtitle_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = EntreBrand.TextDim,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            SubtitlePreview(playerPrefs, Modifier.padding(bottom = 8.dp))
            SettingLabel(stringResource(R.string.player_subtitle_text_size), "")
            SUBTITLE_SIZE_OPTIONS.forEach { (value, label) ->
                RadioLine(stringResource(label), playerPrefs.subtitleSize == value) { onSubtitleSizeChange(value) }
            }
            SettingLabel(stringResource(R.string.subtitle_color), "")
            SUBTITLE_COLOR_OPTIONS.forEach { (value, label) ->
                RadioLine(stringResource(label), playerPrefs.subtitleColor == value) { onSubtitleColorChange(value) }
            }
            SettingLabel(stringResource(R.string.subtitle_background), "")
            SUBTITLE_BACKGROUND_OPTIONS.forEach { (value, label) ->
                RadioLine(stringResource(label), playerPrefs.subtitleBackground == value) { onSubtitleBackgroundChange(value) }
            }
            SettingLabel(stringResource(R.string.subtitle_position), "")
            SUBTITLE_POSITION_OPTIONS.forEach { (value, label) ->
                RadioLine(stringResource(label), playerPrefs.subtitlePosition == value) { onSubtitlePositionChange(value) }
            }

            Divider()
            SectionTitle(stringResource(R.string.settings_section_display))
            SwitchLine(
                title = stringResource(R.string.settings_clock),
                subtitle = stringResource(R.string.settings_clock_desc),
                checked = showClock,
                onChange = onShowClockChange
            )

            Divider()
            SectionTitle(stringResource(R.string.settings_section_playback))
            SettingLabel(
                stringResource(R.string.settings_live_format),
                stringResource(R.string.settings_live_format_desc)
            )
            RadioLine(stringResource(R.string.settings_live_hls), playerPrefs.liveFormat == SettingsRepository.LIVE_HLS) {
                onLiveFormatChange(SettingsRepository.LIVE_HLS)
            }
            RadioLine(stringResource(R.string.settings_live_ts), playerPrefs.liveFormat == SettingsRepository.LIVE_TS) {
                onLiveFormatChange(SettingsRepository.LIVE_TS)
            }
            Spacer(Modifier.height(10.dp))
            SettingLabel(stringResource(R.string.settings_decoder), stringResource(R.string.settings_decoder_desc))
            listOf(
                SettingsRepository.DECODER_AUTO to R.string.settings_decoder_auto,
                SettingsRepository.DECODER_HARDWARE to R.string.settings_decoder_hardware,
                SettingsRepository.DECODER_SOFTWARE to R.string.settings_decoder_software
            ).forEach { (mode, label) ->
                RadioLine(stringResource(label), playerPrefs.decoder == mode) { onDecoderChange(mode) }
            }
            Spacer(Modifier.height(10.dp))
            SettingLabel(stringResource(R.string.settings_frame_rate), stringResource(R.string.settings_frame_rate_desc))
            listOf(
                SettingsRepository.FRAME_RATE_OFF to R.string.settings_frame_rate_off,
                SettingsRepository.FRAME_RATE_AUTO to R.string.settings_frame_rate_auto
            ).forEach { (mode, label) ->
                RadioLine(stringResource(label), playerPrefs.frameRateMode == mode) { onFrameRateModeChange(mode) }
            }
            if (isTv) {
                Spacer(Modifier.height(6.dp))
                SwitchLine(
                    title = stringResource(R.string.settings_tunneling),
                    subtitle = stringResource(R.string.settings_tunneling_desc),
                    checked = playerPrefs.tunneling,
                    onChange = onTunnelingChange
                )
            }
            Spacer(Modifier.height(6.dp))
            SwitchLine(
                title = stringResource(R.string.settings_skip_intro),
                subtitle = stringResource(R.string.settings_skip_intro_desc),
                checked = playerPrefs.skipIntroButton,
                onChange = onSkipIntroChange
            )
            Spacer(Modifier.height(10.dp))
            SettingLabel(
                stringResource(R.string.settings_buffer),
                stringResource(R.string.settings_buffer_desc)
            )
            SettingsRepository.BUFFER_OPTIONS.forEach { seconds ->
                RadioLine(bufferLabel(seconds), seconds == bufferSeconds) { onBufferChange(seconds) }
            }
            Spacer(Modifier.height(10.dp))
            SettingLabel(
                stringResource(R.string.settings_max_buffer),
                stringResource(R.string.settings_max_buffer_desc)
            )
            SettingsRepository.MAX_BUFFER_OPTIONS.forEach { seconds ->
                RadioLine(maxBufferLabel(seconds), seconds == maxBufferSeconds) { onMaxBufferChange(seconds) }
            }

            Divider()
            SectionTitle(stringResource(R.string.settings_section_catalog))
            SettingLabel(stringResource(R.string.settings_refresh), stringResource(R.string.settings_refresh_desc))
            listOf(
                SettingsRepository.REFRESH_ALWAYS to R.string.settings_refresh_always,
                SettingsRepository.REFRESH_DAILY to R.string.settings_refresh_daily,
                SettingsRepository.REFRESH_TWICE_DAILY to R.string.settings_refresh_twice
            ).forEach { (mode, label) ->
                RadioLine(stringResource(label), refreshMode == mode) { onRefreshModeChange(mode) }
            }
            ActionLine(stringResource(R.string.settings_refresh_now), lastRefreshLabel, onRefreshNow)
            Spacer(Modifier.height(10.dp))
            SettingLabel(stringResource(R.string.settings_default_sort), stringResource(R.string.settings_default_sort_desc))
            SortOrder.entries.forEach { order ->
                RadioLine(stringResource(sortLabel(order)), order == defaultSort) { onDefaultSortChange(order) }
            }
            Spacer(Modifier.height(6.dp))
            SwitchLine(
                title = stringResource(R.string.settings_clean_names),
                subtitle = stringResource(R.string.settings_clean_names_desc),
                checked = cleanNames,
                onChange = onCleanNamesChange
            )
            ActionLine(stringResource(R.string.settings_organize), stringResource(R.string.settings_organize_desc), onOrganize)
            ActionLine(
                stringResource(R.string.settings_hidden),
                if (hiddenCount == 0) stringResource(R.string.settings_hidden_none)
                else pluralStringResource(R.plurals.settings_hidden_count, hiddenCount, hiddenCount),
                onHiddenItems
            )

            Divider()
            SectionTitle(stringResource(R.string.settings_section_parental))
            if (!pinSet) {
                Text(
                    stringResource(R.string.settings_pin_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = EntreBrand.TextDim,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ActionLine(stringResource(R.string.settings_create_pin), stringResource(R.string.settings_create_pin_desc), onCreatePin)
            } else {
                Text(
                    stringResource(if (unlocked) R.string.settings_unlocked_status else R.string.settings_locked_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = EntreBrand.TextDim,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (unlocked) ActionLine(stringResource(R.string.settings_lock_now), null, onLockNow)
                else ActionLine(stringResource(R.string.settings_unlock), stringResource(R.string.settings_unlock_desc), onUnlock)
                ActionLine(stringResource(R.string.settings_change_pin), null, onChangePin)
                ActionLine(stringResource(R.string.settings_remove_pin), null, onRemovePin)
            }

            Divider()
            SectionTitle(stringResource(R.string.settings_section_downloads))
            SwitchLine(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_desc),
                checked = downloadWifiOnly,
                onChange = onDownloadWifiOnlyChange
            )

            Divider()
            SectionTitle(stringResource(R.string.settings_section_app))
            ActionLine(stringResource(R.string.settings_check_updates), stringResource(R.string.settings_installed_version, appVersion), onCheckUpdates)
        }
    }
}

/** Idiomas de la app: código para AppCompat y nombre en su propio idioma. */
val APP_LANGUAGES = listOf("es" to "Español", "en" to "English")

@StringRes
private fun contentLanguageLabel(code: String): Int = when (code) {
    "en" -> R.string.settings_language_en
    else -> R.string.settings_language_es
}

@StringRes
private fun contentLanguageInline(code: String): Int = when (code) {
    "en" -> R.string.settings_language_en_inline
    else -> R.string.settings_language_es_inline
}

/** Nombre, usuario, estado y vencimiento de la cuenta, arriba de Configuración. */
@Composable
private fun AccountCard(name: String, user: String, info: AccountInfo?) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val now = System.currentTimeMillis()
    val expiresMs = (info?.expiresAt ?: 0L) * 1000
    val expired = info?.status.equals("Expired", true) || (expiresMs > 0 && expiresMs < now)
    val active = !expired && (info?.status.isNullOrBlank() || info?.status.equals("Active", true))
    val daysLeft = if (expiresMs > now) ((expiresMs - now) / 86_400_000L).toInt() else -1
    val statusText = when {
        info == null -> null
        expired -> stringResource(R.string.settings_status_expired)
        active && info.isTrial -> stringResource(R.string.settings_status_trial)
        active -> stringResource(R.string.settings_status_active)
        info.status.equals("Banned", true) -> stringResource(R.string.settings_status_banned)
        info.status.equals("Disabled", true) -> stringResource(R.string.settings_status_disabled)
        else -> info.status
    }
    val statusColor = when {
        expired || !active -> Color(0xFFE5484D)
        daysLeft in 0..7 -> Color(0xFFF5A524)
        else -> Color(0xFF30A46C)
    }
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.ifBlank { user },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (statusText != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Text(stringResource(R.string.settings_user, user), style = MaterialTheme.typography.bodyMedium, color = EntreBrand.TextDim)
            Spacer(Modifier.height(6.dp))
            val expiry = when {
                info == null -> stringResource(R.string.settings_status_unknown)
                expiresMs <= 0 -> stringResource(R.string.settings_no_expiry)
                else -> {
                    val date = DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(expiresMs))
                    when {
                        expired -> stringResource(R.string.settings_expired_on, date)
                        daysLeft == 0 -> stringResource(R.string.settings_expires_today, date)
                        daysLeft == 1 -> stringResource(R.string.settings_expires_tomorrow, date)
                        daysLeft <= 30 -> pluralStringResource(R.plurals.settings_expires_days_left, daysLeft, date, daysLeft)
                        else -> stringResource(R.string.settings_expires_on, date)
                    }
                }
            }
            Text(
                expiry,
                style = MaterialTheme.typography.bodyMedium,
                color = if (expired || daysLeft in 0..7) statusColor else Color.White
            )
            if ((info?.maxConnections ?: 0) > 0) {
                Text(
                    pluralStringResource(R.plurals.settings_screens, info!!.maxConnections, info.maxConnections),
                    style = MaterialTheme.typography.bodySmall,
                    color = EntreBrand.TextDim
                )
            }
        }
    }
}

@Composable
private fun bufferLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.settings_buffer_0)
    3 -> stringResource(R.string.settings_buffer_3)
    5 -> stringResource(R.string.settings_buffer_5)
    9 -> stringResource(R.string.settings_buffer_9)
    else -> stringResource(R.string.settings_seconds, seconds)
}

@Composable
private fun maxBufferLabel(seconds: Int): String = when (seconds) {
    15 -> stringResource(R.string.settings_max_buffer_15)
    30 -> stringResource(R.string.settings_max_buffer_30)
    60 -> stringResource(R.string.settings_max_buffer_60)
    else -> stringResource(R.string.settings_seconds, seconds)
}

@StringRes
private fun sortLabel(order: SortOrder): Int = when (order) {
    SortOrder.ORIGINAL -> R.string.settings_sort_original
    SortOrder.AZ -> R.string.settings_sort_az
    SortOrder.RATING -> R.string.settings_sort_rating
    SortOrder.RECENT -> R.string.settings_sort_recent
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
    PanelDialog(
        title = stringResource(R.string.dialog_organize_title),
        onDismiss = onDismiss,
        extraAction = stringResource(R.string.dialog_reset_order) to onResetOrder
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                listOf(Section.LIVE, Section.MOVIES, Section.SERIES).forEach { s ->
                    FilterChip(selected = s == section, onClick = { onSection(s) }, label = { Text(s.label) })
                }
            }
            Text(
                stringResource(R.string.dialog_organize_hint),
                style = MaterialTheme.typography.bodySmall,
                color = EntreBrand.TextDim,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.dialog_organize_empty),
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
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.dialog_move_up))
                        }
                        IconButton(onClick = { onMove(c, 1) }, enabled = index < categories.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.dialog_move_down))
                        }
                        IconButton(onClick = { onToggleHidden(c) }) {
                            Icon(
                                if (c.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (c.hidden) R.string.dialog_show else R.string.dialog_hide),
                                tint = if (c.hidden) EntreBrand.TextDim else Color.White
                            )
                        }
                        IconButton(onClick = { onToggleAdult(c) }) {
                            Icon(
                                if (c.adult) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = stringResource(if (c.adult) R.string.dialog_unmark_adult else R.string.dialog_mark_adult),
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
        title = stringResource(R.string.dialog_account_title),
        onDismiss = { if (!saving) onDismiss() },
        extraAction = stringResource(if (saving) R.string.dialog_verifying else R.string.dialog_save) to { if (!saving) onSave(name, user, pass) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.dialog_account_hint),
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
    PanelDialog(title = stringResource(R.string.dialog_hidden_title), onDismiss = onDismiss) {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.dialog_hidden_empty),
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
                                stringResource(
                                    when (item.kind) {
                                        "LIVE" -> R.string.dialog_kind_channel
                                        "MOVIE" -> R.string.dialog_kind_movie
                                        else -> R.string.dialog_kind_series
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = EntreBrand.TextDim
                            )
                        }
                        TextButton(onClick = { onUnhide(item) }) { Text(stringResource(R.string.dialog_show)) }
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
                    PinKey(label = "✕", description = stringResource(R.string.dialog_cancel), onClick = onDismiss)
                    PinKey(label = "0") { press('0') }
                    PinKey(icon = Icons.AutoMirrored.Filled.Backspace, description = stringResource(R.string.dialog_delete)) { pin = pin.dropLast(1) }
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
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_done)) }
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
    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EntreBrand.TextDim)
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
internal fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun ActionLine(title: String, subtitle: String?, onClick: () -> Unit) {
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
