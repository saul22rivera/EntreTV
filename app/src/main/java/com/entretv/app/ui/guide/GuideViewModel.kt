package com.entretv.app.ui.guide

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.repo.GuideProgram
import com.entretv.app.data.repo.GuideRepository
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.MY_LIST_ROW
import com.entretv.app.ui.SortOrder
import com.entretv.app.ui.TitleCleaner
import com.entretv.app.ui.navigation.GuideRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Categoría de la guía: [raw] es la clave, [label] lo que se muestra (vacío en "Mi lista"). */
@Immutable
data class GuideCategory(val raw: String, val label: String)

/** Canales de [category]; [settled] = ya se sabe qué mostrar (antes no se dice "no hay canales"). */
@Immutable
data class GuideChannels(
    val category: String? = null,
    val list: List<ChannelEntity> = emptyList(),
    val settled: Boolean = false
)

/** Programas por canal (id) y qué canales ya respondieron (aunque sea sin datos). */
@Immutable
data class GuidePrograms(
    val byChannel: Map<String, List<GuideProgram>> = emptyMap(),
    val loaded: Set<String> = emptySet()
)

@HiltViewModel
class GuideViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val guide: GuideRepository,
    private val settings: SettingsRepository,
    private val parental: ParentalControl
) : ViewModel() {

    private val route: GuideRoute = savedStateHandle.toRoute()
    val playlistId: String = route.playlistId

    private var cleanNames = true

    /** Canales que se pueden ver: sin ocultos, sin categorías ocultas y, con PIN, sin adultos. */
    private val _all = MutableStateFlow<List<ChannelEntity>>(emptyList())
    private val _order = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _favoriteIds = MutableStateFlow<List<String>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    private val _category = MutableStateFlow(route.category)

    val categories: StateFlow<List<GuideCategory>> = combine(_all, _order, _favoriteIds) { all, order, favorites ->
        buildList {
            if (favorites.any { id -> all.any { it.id == id } }) add(GuideCategory(MY_LIST_ROW, ""))
            all.mapTo(LinkedHashSet()) { it.groupTitle }
                .sortedBy { order[it] ?: Int.MAX_VALUE }
                .forEach { add(GuideCategory(it, TitleCleaner.clean(it, cleanNames).ifBlank { it })) }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Categoría abierta: la pedida si existe, si no la primera. */
    val category: StateFlow<String?> = combine(_category, categories) { chosen, cats ->
        chosen?.takeIf { c -> cats.any { it.raw == c } } ?: cats.firstOrNull()?.raw
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Canales de la categoría abierta, con el nombre ya limpio (el grupo queda sin limpiar). */
    val channels: StateFlow<GuideChannels> = combine(_all, category, _favoriteIds, _loaded) { all, cat, favorites, loaded ->
        val list = when (cat) {
            null -> emptyList()
            MY_LIST_ROW -> {
                val byId = all.associateBy { it.id }
                favorites.mapNotNull { byId[it] }
            }
            else -> all.filter { it.groupTitle == cat }
        }
        GuideChannels(
            category = cat,
            list = if (cleanNames) list.map { it.copy(name = TitleCleaner.clean(it.name)) } else list,
            settled = loaded && (cat != null || all.isEmpty())
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, GuideChannels())

    private val _programs = MutableStateFlow(GuidePrograms())
    val programs: StateFlow<GuidePrograms> = _programs

    private val jobs = HashMap<String, Job>()
    private var publishJob: Job? = null
    private val pending = HashMap<String, List<GuideProgram>?>()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            repository.favorites.collect { list ->
                _favoriteIds.value = list.filter { it.kind == "LIVE" }.sortedBy { it.addedAt }.map { it.contentKey }
            }
        }
        // Al cambiar de categoría: lo guardado en Room al instante y, aparte, quién tiene catch-up.
        viewModelScope.launch {
            var shown: String? = null
            channels.collect { state ->
                val cat = state.category
                val list = state.list
                if (cat == shown || list.isEmpty()) return@collect
                shown = cat
                showStored(list)
                if (cat != null && cat != MY_LIST_ROW) launch { refreshArchive(cat) }
            }
        }
    }

    private suspend fun load() {
        cleanNames = settings.catalogPrefs.first().cleanNames
        val all = repository.channelSnapshot(playlistId)
        val hidden = repository.hiddenKeys()
        val locked = parental.locked.first()
        val kids = parental.kids.value
        val prefs = repository.categoryPrefsSnapshot(playlistId)
            .filter { it.section == PlaylistRepository.SECTION_LIVE }
            .associateBy { it.name }
        val server = repository.categoriesSnapshot(playlistId, PlaylistRepository.SECTION_LIVE)
            .associate { it.name to it.position }
        val sortAz = settings.catalogPrefs.first().defaultSort == SortOrder.AZ.name
        withContext(Dispatchers.Default) {
            val excluded = HashMap<String, Boolean>()
            val visible = all.filter { ch ->
                ch.id !in hidden && !(kids && ParentalControl.looksAdult(ch.name)) && !excluded.getOrPut(ch.groupTitle) {
                    val pref = prefs[ch.groupTitle]
                    pref?.hidden == true || (locked && ParentalControl.isAdult(ch.groupTitle, pref))
                }
            }
            // Mismo orden que En vivo: el del usuario, si no el del servidor (o A-Z si es el orden elegido).
            val names = visible.mapTo(LinkedHashSet()) { it.groupTitle }.toList()
            val userOrdered = prefs.values.any { it.userPosition != null }
            val sorted = if (!userOrdered && sortAz) {
                names.sortedBy { searchKey(TitleCleaner.clean(it, cleanNames)) }
            } else {
                names.sortedWith(compareBy({ prefs[it]?.userPosition ?: Int.MAX_VALUE }, { server[it] ?: Int.MAX_VALUE }))
            }
            _order.value = sorted.withIndex().associate { it.value to it.index }
            _all.value = visible
        }
        _loaded.value = true
    }

    /** Recarga los canales (y su catch-up) si el servidor informó algo nuevo. */
    private suspend fun refreshArchive(category: String) {
        guide.refreshArchiveFlags(playlistId, category)
        val fresh = repository.channelSnapshot(playlistId).associateBy { it.id }
        val current = _all.value
        if (current.any { (fresh[it.id]?.archiveDays ?: it.archiveDays) != it.archiveDays }) {
            _all.value = current.map { ch -> fresh[ch.id]?.let { ch.copy(archiveDays = it.archiveDays) } ?: ch }
            // Los canales con catch-up se vuelven a pedir con la guía completa.
            val withArchive = _all.value.filter { it.archiveDays > 0 }.mapTo(HashSet()) { it.id }
            withArchive.forEach { jobs.remove(it)?.cancel() }
            _programs.value = _programs.value.copy(loaded = _programs.value.loaded - withArchive)
        }
    }

    fun selectCategory(raw: String) {
        if (raw == category.value) return
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _category.value = raw
    }

    private suspend fun showStored(list: List<ChannelEntity>) {
        val now = System.currentTimeMillis() / 1000
        val stored = guide.stored(list, now - GuideRepository.DAY_S, now + 2 * GuideRepository.DAY_S)
        val current = _programs.value
        val merged = HashMap(current.byChannel)
        list.forEach { ch ->
            val fromMemory = guide.cached(ch)
            when {
                fromMemory != null -> merged[ch.id] = fromMemory
                ch.id !in merged -> stored[ch.id]?.let { merged[ch.id] = it }
            }
        }
        val loaded = current.loaded + list.filter { guide.isFresh(it) }.map { it.id }
        _programs.value = GuidePrograms(merged, loaded)
    }

    /**
     * Filas a la vista (y unas cuantas más): se piden las que faltan, de a pocas a la vez.
     * Las que quedaron lejos se cancelan para no gastar red en lo que ya no se ve.
     */
    fun requestVisible(visible: List<ChannelEntity>, keep: Set<String>) {
        jobs.entries.removeAll { (id, job) ->
            val drop = id !in keep || !job.isActive
            if (drop && job.isActive) job.cancel()
            drop
        }
        val loaded = _programs.value.loaded
        visible.forEach { ch ->
            if (ch.id in loaded || ch.id in jobs) return@forEach
            jobs[ch.id] = viewModelScope.launch {
                val programs = guide.load(ch)
                deliver(ch.id, programs)
            }
        }
    }

    /** Junta las respuestas que llegan casi juntas: la grilla se rearma una vez y no por cada canal. */
    private fun deliver(id: String, programs: List<GuideProgram>?) {
        pending[id] = programs
        if (publishJob?.isActive == true) return
        publishJob = viewModelScope.launch {
            delay(PUBLISH_DELAY_MS)
            val batch = HashMap(pending)
            pending.clear()
            val current = _programs.value
            val merged = HashMap(current.byChannel)
            batch.forEach { (channelId, list) ->
                // Si el servidor no respondió se queda lo guardado.
                if (list != null && (list.isNotEmpty() || channelId !in merged)) merged[channelId] = list
            }
            _programs.value = GuidePrograms(merged, current.loaded + batch.keys)
        }
    }

    private fun searchKey(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")
        const val PUBLISH_DELAY_MS = 120L
    }
}
