package com.entretv.app.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.entretv.app.R
import com.entretv.app.data.download.DownloadWorker
import com.entretv.app.data.download.displayName
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.ui.theme.EntreBrand

/** "42 % · 1.2 GB" o, si el servidor no informa el tamaño, lo bajado hasta ahora. */
@Composable
fun DownloadEntity.progressLabel(): String {
    val percent = progress?.let { stringResource(R.string.download_percent, (it * 100).toInt()) }
    val size = if (totalBytes > 0) DownloadWorker.formatBytes(totalBytes) else DownloadWorker.formatBytes(downloadedBytes)
    return listOfNotNull(percent, size.takeIf { downloadedBytes > 0 || totalBytes > 0 }).joinToString(" · ")
}

@Composable
fun DownloadEntity.statusLabel(): String = when (status) {
    DownloadStatus.QUEUED -> stringResource(if (error.isNotEmpty()) R.string.download_waiting_connection else R.string.download_queued)
    DownloadStatus.RUNNING -> stringResource(R.string.download_downloading, progressLabel()).trim()
    DownloadStatus.PAUSED -> stringResource(R.string.download_paused, progressLabel()).trim()
    DownloadStatus.FAILED -> error.ifEmpty { stringResource(R.string.download_failed) }
    else -> if (totalBytes > 0) stringResource(R.string.download_done_size, DownloadWorker.formatBytes(totalBytes)) else stringResource(R.string.download_done)
}

/** Botones de la ficha de una película según el estado de su descarga. */
@Composable
fun MovieDownloadButtons(
    download: DownloadEntity?,
    onDownload: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (DownloadEntity) -> Unit
) {
    when (download?.status) {
        null -> EntreButton(stringResource(R.string.download_action), Icons.Default.Download, onDownload)
        DownloadStatus.DONE -> EntreButton(stringResource(R.string.download_done), Icons.Default.DownloadDone, { onDelete(download) })
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> {
            EntreButton(download.statusLabel(), Icons.Default.Pause, { onPause(download.contentKey) })
            EntreButton(stringResource(R.string.download_cancel), Icons.Default.Close, { onDelete(download) })
        }
        DownloadStatus.PAUSED -> {
            EntreButton(
                stringResource(R.string.download_resume, download.progressLabel()).trim(),
                Icons.Default.Download,
                { onResume(download.contentKey) }
            )
            EntreButton(stringResource(R.string.download_cancel), Icons.Default.Close, { onDelete(download) })
        }
        else -> {
            EntreButton(stringResource(R.string.download_retry), Icons.Default.Download, { onResume(download.contentKey) })
            EntreButton(stringResource(R.string.download_remove), Icons.Default.Close, { onDelete(download) })
        }
    }
}

/** Ícono redondo al costado de cada episodio: descargar, progreso/pausa, reanudar o borrar. */
@Composable
fun EpisodeDownloadIcon(
    download: DownloadEntity?,
    onDownload: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val status = download?.status
    val description = stringResource(
        when (status) {
            null -> R.string.download_episode
            DownloadStatus.DONE -> R.string.download_delete_download
            DownloadStatus.QUEUED, DownloadStatus.RUNNING -> R.string.download_pause
            else -> R.string.download_resume_download
        }
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, EntreBrand.Accent, CircleShape) else Modifier)
            .clickable(interactionSource = source, indication = LocalIndication.current) {
                when (status) {
                    null -> onDownload()
                    DownloadStatus.DONE -> onDelete(download)
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING -> onPause(download.contentKey)
                    else -> onResume(download.contentKey)
                }
            }
    ) {
        when (status) {
            DownloadStatus.QUEUED, DownloadStatus.RUNNING -> {
                val p = download.progress
                if (p != null && status == DownloadStatus.RUNNING) {
                    CircularProgressIndicator(
                        progress = { p },
                        color = EntreBrand.Accent,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    CircularProgressIndicator(color = EntreBrand.Accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                }
                Icon(Icons.Default.Pause, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            DownloadStatus.DONE ->
                Icon(Icons.Default.DownloadDone, contentDescription = description, tint = EntreBrand.Accent)
            DownloadStatus.FAILED ->
                Icon(Icons.Default.ErrorOutline, contentDescription = description, tint = Color(0xFFFF8A80))
            DownloadStatus.PAUSED ->
                Icon(Icons.Default.Download, contentDescription = description, tint = EntreBrand.TextDim)
            else ->
                Icon(Icons.Default.Download, contentDescription = description, tint = Color.White)
        }
    }
}

/** Confirmación antes de borrar o cancelar; en TV el foco arranca en "Cancelar" para no borrar sin querer. */
@Composable
fun ConfirmDeleteDownloadDialog(download: DownloadEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val keepFocus = remember { FocusRequester() }
    val name = download.displayName(LocalContext.current)
    val finished = download.isDone
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EntreBrand.Panel,
        title = {
            Text(
                stringResource(if (finished) R.string.download_delete_title else R.string.download_cancel_title),
                color = Color.White
            )
        },
        text = {
            Text(
                stringResource(if (finished) R.string.download_delete_text else R.string.download_cancel_text, name),
                color = EntreBrand.TextDim
            )
        },
        confirmButton = {
            EntreButton(
                stringResource(if (finished) R.string.download_delete else R.string.download_cancel_download),
                null,
                onConfirm,
                compact = true
            )
        },
        dismissButton = {
            EntreButton(
                stringResource(if (finished) R.string.download_cancel else R.string.download_keep_downloading),
                null,
                onDismiss,
                compact = true,
                modifier = Modifier.focusRequester(keepFocus)
            )
        }
    )
    LaunchedEffect(Unit) { runCatching { keepFocus.requestFocus() } }
}

/** Igual que la anterior pero con textos libres (borrar varias, borrar todo, detener una serie). */
@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.download_cancel)
) {
    val keepFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EntreBrand.Panel,
        title = { Text(title, color = Color.White) },
        text = { Text(text, color = EntreBrand.TextDim) },
        confirmButton = { EntreButton(confirmLabel, null, onConfirm, compact = true) },
        dismissButton = {
            EntreButton(dismissLabel, null, onDismiss, compact = true, modifier = Modifier.focusRequester(keepFocus))
        }
    )
    LaunchedEffect(Unit) { runCatching { keepFocus.requestFocus() } }
}
