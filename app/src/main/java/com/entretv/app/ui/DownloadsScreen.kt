package com.entretv.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.entretv.app.R
import com.entretv.app.data.download.episodeSubtitle
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.ui.theme.EntreBrand
import kotlinx.coroutines.launch

/** Lo descargado para ver sin conexión, con lo que está en curso arriba de todo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (DownloadEntity) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val profile = rememberDeviceProfile()
    val items by viewModel.items.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var confirmMany by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val notReady = stringResource(R.string.download_not_ready)

    val sorted = remember(items) {
        items.orEmpty().sortedWith(compareBy<DownloadEntity> { it.isDone }.thenByDescending { it.addedAt })
    }
    val loaded = items != null
    val keys = remember(sorted) { sorted.map { it.contentKey }.toSet() }
    val chosen = selected intersect keys

    LaunchedEffect(sorted.isEmpty()) {
        if (sorted.isEmpty()) {
            selecting = false
            selected = emptySet()
        }
    }

    LaunchedEffect(loaded, sorted.isEmpty()) {
        if (profile.isTv && loaded) {
            runCatching { if (sorted.isNotEmpty()) firstFocus.requestFocus() else backFocus.requestFocus() }
        }
    }

    BackHandler(enabled = selecting) {
        selecting = false
        selected = emptySet()
    }

    fun toggle(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }

    Scaffold(
        containerColor = EntreBrand.Ink,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = profile.edge / 2, end = profile.edge, top = 8.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back), tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selecting) pluralStringResource(R.plurals.download_selected_count, chosen.size, chosen.size)
                        else stringResource(R.string.download_title),
                        style = if (profile.isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    storage?.takeIf { sorted.isNotEmpty() }?.let {
                        Text(it.asString(), style = MaterialTheme.typography.labelMedium, color = EntreBrand.TextDim)
                    }
                }
            }

            if (loaded && sorted.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = profile.edge, vertical = 6.dp)
                ) {
                    if (selecting) {
                        val all = chosen.size == keys.size
                        EntreButton(
                            text = stringResource(if (all) R.string.download_deselect_all else R.string.download_select_all),
                            icon = if (all) Icons.Default.Deselect else Icons.Default.SelectAll,
                            compact = true,
                            onClick = { selected = if (all) emptySet() else keys }
                        )
                        if (chosen.isNotEmpty()) {
                            EntreButton(
                                text = stringResource(R.string.download_delete_count, chosen.size),
                                icon = Icons.Default.Delete,
                                primary = true,
                                compact = true,
                                onClick = { confirmMany = true }
                            )
                        }
                        EntreButton(
                            text = stringResource(R.string.download_done_selecting),
                            icon = Icons.Default.Close,
                            compact = true,
                            onClick = {
                                selecting = false
                                selected = emptySet()
                            }
                        )
                    } else {
                        EntreButton(
                            text = stringResource(R.string.download_select),
                            icon = Icons.Default.Checklist,
                            compact = true,
                            onClick = { selecting = true }
                        )
                        EntreButton(
                            text = stringResource(R.string.download_delete_all),
                            icon = Icons.Default.DeleteSweep,
                            compact = true,
                            onClick = { confirmAll = true }
                        )
                    }
                }
                if (!profile.isTv && !selecting) {
                    Text(
                        stringResource(R.string.download_long_press_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = EntreBrand.TextDim,
                        modifier = Modifier.padding(horizontal = profile.edge)
                    )
                }
            }

            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EntreBrand.Accent)
                }
                sorted.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = EntreBrand.TextDim, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.download_empty),
                            color = EntreBrand.TextDim,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = profile.edge - 6.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sorted, key = { it.contentKey }) { download ->
                        DownloadRow(
                            download = download,
                            wide = !profile.compactRail,
                            selection = if (selecting) download.contentKey in chosen else null,
                            modifier = if (download == sorted.first()) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = {
                                when {
                                    selecting -> toggle(download.contentKey)
                                    download.isDone -> onPlay(download)
                                    download.status == DownloadStatus.FAILED -> viewModel.resume(download.contentKey)
                                    else -> scope.launch { snackbar.showSnackbar(notReady) }
                                }
                            },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selected = setOf(download.contentKey)
                                } else {
                                    toggle(download.contentKey)
                                }
                            },
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onDelete = { confirmDelete = it }
                        )
                    }
                }
            }
        }
    }

    if (confirmMany) {
        val count = chosen.size
        val deletedMessage = pluralStringResource(R.plurals.download_deleted_many, count, count)
        ConfirmActionDialog(
            title = pluralStringResource(R.plurals.download_delete_many_title, count, count),
            text = pluralStringResource(R.plurals.download_delete_many_text, count),
            confirmLabel = stringResource(R.string.download_delete),
            onConfirm = {
                viewModel.deleteMany(chosen)
                confirmMany = false
                selecting = false
                selected = emptySet()
                scope.launch { snackbar.showSnackbar(deletedMessage) }
            },
            onDismiss = { confirmMany = false }
        )
    }

    if (confirmAll) {
        val allDeletedMessage = stringResource(R.string.download_all_deleted)
        ConfirmActionDialog(
            title = stringResource(R.string.download_delete_all_title),
            text = pluralStringResource(R.plurals.download_delete_all_text, sorted.size, sorted.size),
            confirmLabel = stringResource(R.string.download_delete_all),
            onConfirm = {
                viewModel.deleteAll()
                confirmAll = false
                scope.launch { snackbar.showSnackbar(allDeletedMessage) }
            },
            onDismiss = { confirmAll = false }
        )
    }

    confirmDelete?.let { download ->
        ConfirmDeleteDownloadDialog(
            download = download,
            onConfirm = {
                viewModel.delete(download.contentKey)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** [selection] es null fuera del modo selección; si no, si la fila está marcada. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
    download: DownloadEntity,
    wide: Boolean,
    selection: Boolean?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (DownloadEntity) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "downloadScale")
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        focused -> Color.White.copy(alpha = 0.10f)
        selection == true -> EntreBrand.Accent.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .weight(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(background)
                .then(if (focused) Modifier.border(2.dp, EntreBrand.Accent, shape) else Modifier)
                .combinedClickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(6.dp)
        ) {
            if (selection != null) {
                Icon(
                    if (selection) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = stringResource(if (selection) R.string.download_selected else R.string.download_not_selected),
                    tint = if (selection) EntreBrand.Accent else EntreBrand.TextDim,
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp).size(26.dp)
                )
            }
            Box(
                Modifier
                    .width(if (wide) 200.dp else 128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EntreBrand.PanelHigh),
                contentAlignment = Alignment.Center
            ) {
                if (download.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = download.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (download.isDone) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    download.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (download.kind == "EPISODE") {
                    Text(
                        download.episodeSubtitle(LocalContext.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = EntreBrand.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    download.statusLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (download.status == DownloadStatus.FAILED) Color(0xFFFF8A80) else EntreBrand.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!download.isDone && download.status != DownloadStatus.FAILED) {
                    Spacer(Modifier.height(6.dp))
                    val p = download.progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            color = EntreBrand.Accent,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    } else if (download.status == DownloadStatus.RUNNING) {
                        LinearProgressIndicator(
                            color = EntreBrand.Accent,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
        if (selection == null) {
            Spacer(Modifier.width(6.dp))
            EpisodeDownloadIcon(
                download = download,
                onDownload = {},
                onPause = onPause,
                onResume = onResume,
                onDelete = onDelete
            )
            if (!download.isDone) {
                IconButton(onClick = { onDelete(download) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.download_cancel_download), tint = EntreBrand.TextDim)
                }
            }
        }
    }
}
