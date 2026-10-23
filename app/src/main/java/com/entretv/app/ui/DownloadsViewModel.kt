package com.entretv.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entretv.app.R
import com.entretv.app.data.download.DownloadRepository
import com.entretv.app.data.download.DownloadWorker
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.ParentalControl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    repository: PlaylistRepository,
    parental: ParentalControl
) : ViewModel() {

    /** Las descargas son de todos; el perfil de niños no ve las de adultos. */
    val items: StateFlow<List<DownloadEntity>?> = combine(downloads.downloads, parental.kids) { list, kids ->
        if (!kids) return@combine list
        val adult = list.map { it.playlistId }.distinct().flatMapTo(HashSet()) { repository.adultContentKeys(it) }
        list.filter { d ->
            (if (d.kind == "EPISODE") d.seriesKey else d.contentKey) !in adult && !ParentalControl.looksAdult(d.title)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Ocupan 3.2 GB · Libres 18.4 GB". */
    val storage: StateFlow<UiText?> = downloads.downloads
        .map {
            val dir = downloads.directory
            val used = dir.listFiles()?.sumOf { it.length() } ?: 0L
            uiText(R.string.download_storage, DownloadWorker.formatBytes(used), DownloadWorker.formatBytes(dir.usableSpace))
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pause(key: String) { viewModelScope.launch { downloads.pause(key) } }

    fun resume(key: String) { viewModelScope.launch { downloads.resume(key) } }

    fun delete(key: String) { viewModelScope.launch { downloads.delete(key) } }

    fun deleteMany(keys: Collection<String>) {
        val copy = keys.toList()
        viewModelScope.launch { downloads.deleteMany(copy) }
    }

    fun deleteAll() {
        val keys = items.value.orEmpty().map { it.contentKey }
        viewModelScope.launch { downloads.deleteMany(keys) }
    }
}
