package com.entretv.app.ui.profile

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entretv.app.data.local.ProfileEntity
import com.entretv.app.data.profile.PinChange
import com.entretv.app.data.profile.PinCheck
import com.entretv.app.data.profile.ProfileRepository
import com.entretv.app.data.profile.ProfileSession
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.player.PlayerManager
import com.entretv.app.tv.TvDeepLink
import com.entretv.app.tv.TvHomePublisher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pantalla de perfiles abierta encima de la app. */
sealed interface ProfileScreen {
    /** "¿Quién está viendo?". */
    data object Picker : ProfileScreen
    data object Manage : ProfileScreen
    /** [profileId] null = perfil nuevo; [back] es a dónde se vuelve al terminar. */
    data class Edit(val profileId: String?, val back: ProfileScreen) : ProfileScreen
}

/** PIN que se está pidiendo. */
sealed interface PinPrompt {
    val error: PinCheck?
    /** Entrar al perfil. */
    data class Enter(val profileId: String, override val error: PinCheck? = null) : PinPrompt
    /** Editar o borrar el perfil. */
    data class Edit(val profileId: String, override val error: PinCheck? = null) : PinPrompt
    /** Perfil de niños: Configuración y la gestión de perfiles piden un PIN de adultos. */
    data class Adult(override val error: PinCheck? = null) : PinPrompt
}

data class ProfileUiState(
    val screen: ProfileScreen? = null,
    /** Arranque: la app espera la elección (Atrás sale de la app). */
    val startup: Boolean = false,
    val pin: PinPrompt? = null,
    /** Niños sin ningún PIN de adultos: alcanza con confirmar. */
    val adultConfirm: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ProfileRepository,
    private val session: ProfileSession,
    private val playlists: PlaylistRepository,
    private val tvHome: dagger.Lazy<TvHomePublisher>,
    private val player: dagger.Lazy<PlayerManager>
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>?> = repo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeId: StateFlow<String> = session.activeId
    val activeName: StateFlow<String> = session.activeName
    val activeAvatar: StateFlow<Int> = session.activeAvatar
    val kids: StateFlow<Boolean> = session.kids
    val multiple: StateFlow<Boolean> = session.multiple
    val askOnStart: StateFlow<Boolean> = session.askOnStart

    private val _ui = MutableStateFlow(startupState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    /** Lo que espera al PIN o a la confirmación de adultos. */
    private var adultAction: (() -> Unit)? = null

    /** Un PIN de adultos recién puesto alcanza para lo que sigue (Configuración › Perfiles) por un rato. */
    private var adultGrantedAt = Long.MIN_VALUE / 2

    init {
        // Quien ya tenía sesión antes de los perfiles: el perfil por defecto toma el nombre de la cuenta
        // (si al iniciar sesión no se escribió nombre, la cuenta quedó con el usuario y no sirve).
        if (!session.defaultNameChecked) viewModelScope.launch {
            val account = playlists.currentPlaylist() ?: return@launch
            if (account.name != account.username) repo.nameDefaultProfile(account.name)
            session.defaultNameChecked = true
        }
    }

    /** Con un perfil y sin PIN no se abre nada ni se lee la base: entra directo. */
    private fun startupState(): ProfileUiState {
        if (session.startupHandled) return ProfileUiState()
        val ask = session.multiple.value && session.askOnStart.value
        val pin = session.activeHasPin
        if (!ask && !pin) {
            session.startupHandled = true
            return ProfileUiState()
        }
        return ProfileUiState(
            screen = ProfileScreen.Picker,
            startup = true,
            pin = if (!ask) PinPrompt.Enter(session.activeId.value) else null
        )
    }

    fun openPicker() {
        _ui.value = ProfileUiState(screen = ProfileScreen.Picker)
    }

    fun openManage() = guardAdult { _ui.update { it.copy(screen = ProfileScreen.Manage) } }

    fun addProfile(from: ProfileScreen) = guardAdult {
        _ui.update { it.copy(screen = ProfileScreen.Edit(null, from)) }
    }

    /** Cerrar (Atrás). En el arranque no hay a dónde volver: [exitApp] cierra la app. */
    fun back(exitApp: () -> Unit) {
        val state = _ui.value
        when (val screen = state.screen) {
            is ProfileScreen.Edit -> _ui.update { it.copy(screen = screen.back) }
            ProfileScreen.Manage -> _ui.update { it.copy(screen = ProfileScreen.Picker) }
            ProfileScreen.Picker -> if (state.startup) exitApp() else _ui.value = ProfileUiState()
            null -> Unit
        }
    }

    fun choose(profile: ProfileEntity) {
        val state = _ui.value
        val current = profile.id == session.activeId.value
        if (profile.hasPin && (state.startup || !current)) {
            _ui.update { it.copy(pin = PinPrompt.Enter(profile.id)) }
        } else enter(profile.id)
    }

    fun edit(profile: ProfileEntity) {
        if (profile.hasPin) _ui.update { it.copy(pin = PinPrompt.Edit(profile.id)) }
        else _ui.update { it.copy(screen = ProfileScreen.Edit(profile.id, ProfileScreen.Manage)) }
    }

    fun submitPin(pin: String) {
        val prompt = _ui.value.pin ?: return
        viewModelScope.launch {
            val result = when (prompt) {
                is PinPrompt.Enter -> repo.verifyPin(prompt.profileId, pin)
                is PinPrompt.Edit -> repo.verifyPin(prompt.profileId, pin)
                is PinPrompt.Adult -> repo.verifyAdultPin(pin)
            }
            if (result != PinCheck.Ok) {
                _ui.update {
                    it.copy(
                        pin = when (prompt) {
                            is PinPrompt.Enter -> prompt.copy(error = result)
                            is PinPrompt.Edit -> prompt.copy(error = result)
                            is PinPrompt.Adult -> prompt.copy(error = result)
                        }
                    )
                }
                return@launch
            }
            _ui.update { it.copy(pin = null) }
            when (prompt) {
                is PinPrompt.Enter -> enter(prompt.profileId)
                is PinPrompt.Edit -> _ui.update { it.copy(screen = ProfileScreen.Edit(prompt.profileId, ProfileScreen.Manage)) }
                is PinPrompt.Adult -> runAdultAction()
            }
        }
    }

    fun dismissPin() {
        _ui.update { it.copy(pin = null) }
        adultAction = null
    }

    /**
     * Desde el perfil de niños, [action] (Configuración, administrar perfiles) pide el PIN de un
     * perfil de adultos; si no hay ninguno, una confirmación. Los demás perfiles pasan directo.
     */
    fun guardAdult(action: () -> Unit) {
        if (!session.kids.value || SystemClock.elapsedRealtime() - adultGrantedAt < ADULT_GRANT_MS) {
            action()
            return
        }
        adultAction = action
        viewModelScope.launch {
            if (repo.adultPinExists()) _ui.update { it.copy(pin = PinPrompt.Adult()) }
            else _ui.update { it.copy(adultConfirm = true) }
        }
    }

    fun confirmAdult(ok: Boolean) {
        _ui.update { it.copy(adultConfirm = false) }
        if (ok) runAdultAction() else adultAction = null
    }

    private fun runAdultAction() {
        adultGrantedAt = SystemClock.elapsedRealtime()
        val action = adultAction
        adultAction = null
        action?.invoke()
    }

    private fun enter(profileId: String) {
        viewModelScope.launch {
            val before = session.activeId.value
            repo.select(profileId) ?: return@launch
            session.startupHandled = true
            _ui.value = ProfileUiState()
            if (before != profileId) onProfileChanged()
        }
    }

    private companion object {
        const val ADULT_GRANT_MS = 5 * 60_000L
    }

    /** Lo que sonaba era del perfil anterior; la pantalla de inicio de la TV pasa al nuevo. */
    private fun onProfileChanged() {
        adultGrantedAt = Long.MIN_VALUE / 2
        val manager = player.get()
        if (manager.currentKey.value != null) manager.stop()
        if (TvHomePublisher.isSupported(context)) {
            viewModelScope.launch { tvHome.get().publishWatchNext() }
        }
    }

    fun save(
        profileId: String?,
        name: String,
        avatar: Int,
        kids: Boolean,
        pin: PinChange,
        back: ProfileScreen
    ) {
        viewModelScope.launch {
            val wasKids = session.kids.value
            if (profileId == null) {
                repo.create(name, avatar, kids, (pin as? PinChange.Set)?.pin)
            } else {
                repo.update(profileId, name, avatar, kids, pin)
            }
            _ui.update { it.copy(screen = back) }
            if (profileId == session.activeId.value && wasKids != kids) onProfileChanged()
        }
    }

    fun delete(profileId: String) {
        viewModelScope.launch {
            val before = session.activeId.value
            if (repo.delete(profileId)) {
                _ui.update { it.copy(screen = ProfileScreen.Manage) }
                if (before == profileId) onProfileChanged()
            }
        }
    }

    fun setAskOnStart(ask: Boolean) = session.setAskOnStart(ask)

    /** Las tarjetas del inicio de la TV no abren contenido de adultos en el perfil de niños. */
    suspend fun allowLink(link: TvDeepLink): Boolean {
        if (!session.kids.value) return true
        val (playlistId, section, category, title) = when (link.kind) {
            "MOVIE" -> playlists.movieById(link.id)?.let {
                LinkTarget(it.playlistId, PlaylistRepository.SECTION_MOVIES, it.categoryName, it.name)
            }
            "SERIES" -> playlists.seriesById(link.id)?.let {
                LinkTarget(it.playlistId, PlaylistRepository.SECTION_SERIES, it.categoryName, it.name)
            }
            "EPISODE" -> playlists.seriesById(link.id.substringBefore('#'))?.let {
                LinkTarget(it.playlistId, PlaylistRepository.SECTION_SERIES, it.categoryName, it.name)
            }
            else -> null
        } ?: return false
        val pref = playlists.categoryPrefsSnapshot(playlistId).firstOrNull { it.section == section && it.name == category }
        return !ParentalControl.isAdult(category, pref) && !ParentalControl.looksAdult(title)
    }

    private data class LinkTarget(val playlistId: String, val section: String, val category: String, val title: String)
}
