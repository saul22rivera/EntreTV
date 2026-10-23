package com.entretv.app.data.profile

import android.content.Context
import com.entretv.app.data.local.DEFAULT_PROFILE_ID
import com.entretv.app.data.local.ProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Perfil activo y lo justo para decidir el arranque sin abrir la base: con un solo perfil y
 * sin PIN la app entra directo como siempre.
 */
@Singleton
class ProfileSession @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null) ?: DEFAULT_PROFILE_ID)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    private val _kids = MutableStateFlow(prefs.getBoolean(KEY_KIDS, false))
    /** El perfil activo es de niños: nada de adultos en ningún lado. */
    val kids: StateFlow<Boolean> = _kids.asStateFlow()

    private val _activeName = MutableStateFlow(prefs.getString(KEY_NAME, "").orEmpty())
    val activeName: StateFlow<String> = _activeName.asStateFlow()

    private val _activeAvatar = MutableStateFlow(prefs.getInt(KEY_AVATAR, 0))
    val activeAvatar: StateFlow<Int> = _activeAvatar.asStateFlow()

    private val _multiple = MutableStateFlow(prefs.getBoolean(KEY_MULTIPLE, false))
    /** Hay más de un perfil. */
    val multiple: StateFlow<Boolean> = _multiple.asStateFlow()

    private val _askOnStart = MutableStateFlow(prefs.getBoolean(KEY_ASK_ON_START, true))
    /** Con más de un perfil, la app abre en "¿Quién está viendo?". */
    val askOnStart: StateFlow<Boolean> = _askOnStart.asStateFlow()

    val activeHasPin: Boolean get() = prefs.getBoolean(KEY_HAS_PIN, false)

    /** El arranque ya pasó por la elección de perfil (o no hacía falta) en este proceso. */
    @Volatile var startupHandled: Boolean = false

    /** Ya se le buscó nombre al perfil por defecto con la cuenta guardada (se hace una sola vez). */
    var defaultNameChecked: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_NAMED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_NAMED, value).apply()

    fun activate(profile: ProfileEntity) {
        prefs.edit()
            .putString(KEY_ACTIVE, profile.id)
            .putBoolean(KEY_KIDS, profile.isKids)
            .putBoolean(KEY_HAS_PIN, profile.hasPin)
            .putString(KEY_NAME, profile.name)
            .putInt(KEY_AVATAR, profile.avatar)
            .apply()
        _activeName.value = profile.name
        _activeAvatar.value = profile.avatar
        _kids.value = profile.isKids
        _activeId.value = profile.id
    }

    fun setProfileCount(count: Int) {
        val multiple = count > 1
        if (multiple != _multiple.value) {
            prefs.edit().putBoolean(KEY_MULTIPLE, multiple).apply()
            _multiple.value = multiple
        }
    }

    fun setAskOnStart(ask: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_ON_START, ask).apply()
        _askOnStart.value = ask
    }

    // Intentos fallidos de PIN: sobreviven a cerrar la app para que no sirva reiniciarla.
    fun failedAttempts(key: String): Int = prefs.getInt("fails_$key", 0)

    fun lockedUntil(key: String): Long = prefs.getLong("lock_$key", 0L)

    fun setPinAttempts(key: String, fails: Int, lockedUntil: Long) {
        prefs.edit().putInt("fails_$key", fails).putLong("lock_$key", lockedUntil).apply()
    }

    fun clearPinAttempts(key: String) {
        prefs.edit().remove("fails_$key").remove("lock_$key").apply()
    }

    private companion object {
        const val PREFS = "entre_profiles"
        const val KEY_ACTIVE = "active_id"
        const val KEY_KIDS = "active_kids"
        const val KEY_HAS_PIN = "active_has_pin"
        const val KEY_NAME = "active_name"
        const val KEY_AVATAR = "active_avatar"
        const val KEY_MULTIPLE = "multiple"
        const val KEY_ASK_ON_START = "ask_on_start"
        const val KEY_DEFAULT_NAMED = "default_name_checked"
    }
}
