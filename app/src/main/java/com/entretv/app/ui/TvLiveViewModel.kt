package com.entretv.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** Guía del canal enfocado: lo que está ahora y lo que sigue. */
@Immutable
data class LiveGuide(
    val channelId: String? = null,
    val programs: List<EpgEntity> = emptyList(),
    val loading: Boolean = false
)

/** Canales de la lista agrupados por categoría (clave sin limpiar), con el nombre ya limpio. */
@Immutable
data class LiveChannels(
    val byGroup: Map<String, List<ChannelEntity>> = emptyMap(),
    val byId: Map<String, ChannelEntity> = emptyMap()
)

/**
 * En vivo para TV: categorías, canales y un mini reproductor con el reproductor compartido.
 * Recorrer la lista no cambia lo que suena; solo OK reproduce.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TvLiveViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    settings: SettingsRepository,
    val playerManager: PlayerManager
) : ViewModel() {

    private val playlistId = MutableStateFlow<String?>(null)

    fun setPlaylist(id: String?) {
        playlistId.value = id
    }

    val channels: StateFlow<LiveChannels> = combine(
        playlistId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.channels(id) },
        repository.hiddenItems,
        settings.catalogPrefs
    ) { list, hidden, prefs ->
        val hiddenKeys = hidden.mapTo(HashSet()) { it.contentKey }
        val shown = list.filter { it.id !in hiddenKeys }
            .map { if (prefs.cleanNames) it.copy(name = TitleCleaner.clean(it.name, true)) else it }
        LiveChannels(byGroup = shown.groupBy { it.groupTitle }, byId = shown.associateBy { it.id })
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveChannels())

    /** Canales en "Mi lista", en el orden en que se agregaron. */
    val favoriteIds: StateFlow<List<String>> = repository.favorites
        .map { list -> list.filter { it.kind == "LIVE" }.sortedBy { it.addedAt }.map { it.contentKey } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categoría abierta en la columna central; la pantalla elige la primera si es null. */
    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category

    fun selectCategory(name: String) {
        _category.value = name
    }

    /** Canal que suena en el mini reproductor (o el que quedó al volver de pantalla completa). */
    val playing: StateFlow<ChannelEntity?> = combine(playerManager.currentKey, channels) { key, all ->
        key?.let { all.byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val focused = MutableStateFlow<ChannelEntity?>(null)

    fun focus(channel: ChannelEntity) {
        focused.value = channel
    }

    /** Última consulta al servidor por canal: recorrer la lista no repite pedidos. */
    private val fetchedAt = HashMap<String, Long>()

    /** Se espera a que el foco se quede quieto: pasar rápido por la lista no pide nada. */
    val guide: StateFlow<LiveGuide> = focused
        .debounce(GUIDE_DEBOUNCE_MS)
        .transformLatest { channel ->
            if (channel == null) {
                emit(LiveGuide())
                return@transformLatest
            }
            val cached = repository.upcomingEpg(channel, GUIDE_SIZE)
            val now = System.currentTimeMillis()
            val stale = now - (fetchedAt[channel.id] ?: 0L) > GUIDE_REFRESH_MS
            val fetch = stale && cached.size < GUIDE_SIZE
            emit(LiveGuide(channel.id, cached, loading = fetch))
            if (fetch) {
                fetchedAt[channel.id] = now
                repository.refreshEpg(channel)
                emit(LiveGuide(channel.id, repository.upcomingEpg(channel, GUIDE_SIZE), loading = false))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveGuide())

    fun play(channel: ChannelEntity) {
        playerManager.play(channel.streamUrl, channel.name, TitleCleaner.clean(channel.groupTitle, true), isLive = true, contentKey = channel.id)
    }

    /** Pantalla completa abierta desde el mini reproductor: al salir de Inicio no se corta. */
    var openingFullscreen = false

    /** Al volver a la pantalla (de pantalla completa o de otra app) el canal sigue en el directo. */
    fun resume() {
        openingFullscreen = false
        if (playerManager.currentKey.value == null) return
        val player = playerManager.player
        if (!player.isPlaying) {
            player.seekToDefaultPosition()
            player.play()
        }
    }

    fun pause() {
        if (playerManager.currentKey.value != null) playerManager.player.pause()
    }

    /** Al salir de En vivo: se corta el canal para no gastar datos. */
    fun stop() {
        if (playerManager.currentKey.value != null) playerManager.stop()
    }

    private companion object {
        const val GUIDE_DEBOUNCE_MS = 400L
        const val GUIDE_SIZE = 4
        const val GUIDE_REFRESH_MS = 10 * 60_000L
    }
}
