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
import androidx.compose.ui.unit.dp
import com.entretv.app.data.download.DownloadWorker
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.ui.theme.EntreBrand

/** "42 % · 1.2 GB" o, si el servidor no informa el tamaño, lo bajado hasta ahora. */
fun DownloadEntity.progressLabel(): String {
    val percent = progress?.let { "${(it * 100).toInt()} %" }
    val size = if (totalBytes > 0) DownloadWorker.formatBytes(totalBytes) else DownloadWorker.formatBytes(downloadedBytes)
    return listOfNotNull(percent, size.takeIf { downloadedBytes > 0 || totalBytes > 0 }).joinToString(" · ")
}

fun DownloadEntity.statusLabel(): String = when (status) {
    DownloadStatus.QUEUED -> if (error.isNotEmpty()) "Esperando conexión" else "En cola"
    DownloadStatus.RUNNING -> "Descargando ${progressLabel()}".trim()
    DownloadStatus.PAUSED -> "En pausa ${progressLabel()}".trim()
    DownloadStatus.FAILED -> error.ifEmpty { "No se pudo descargar" }
    else -> if (totalBytes > 0) "Descargada · ${DownloadWorker.formatBytes(totalBytes)}" else "Descargada"
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
        null -> EntreButton("Descargar", Icons.Default.Download, onDownload)
        DownloadStatus.DONE -> EntreButton("Descargada", Icons.Default.DownloadDone, { onDelete(download) })
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> {
            EntreButton(download.statusLabel(), Icons.Default.Pause, { onPause(download.contentKey) })
            EntreButton("Cancelar", Icons.Default.Close, { onDelete(download) })
        }
        DownloadStatus.PAUSED -> {
            EntreButton("Reanudar ${download.progressLabel()}".trim(), Icons.Default.Download, { onResume(download.contentKey) })
            EntreButton("Cancelar", Icons.Default.Close, { onDelete(download) })
        }
        else -> {
            EntreButton("Reintentar descarga", Icons.Default.Download, { onResume(download.contentKey) })
            EntreButton("Quitar", Icons.Default.Close, { onDelete(download) })
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
    val description = when (status) {
        null -> "Descargar episodio"
        DownloadStatus.DONE -> "Borrar descarga"
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> "Pausar descarga"
        else -> "Reanudar descarga"
    }
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
    val name = if (download.kind == "EPISODE") "${download.title} · ${download.subtitle}" else download.title
    val finished = download.isDone
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EntreBrand.Panel,
        title = { Text(if (finished) "¿Borrar la descarga?" else "¿Cancelar la descarga?", color = Color.White) },
        text = {
            Text(
                if (finished) "$name dejará de estar disponible sin conexión." else "Se borrará lo descargado de $name.",
                color = EntreBrand.TextDim
            )
        },
        confirmButton = {
            EntreButton(if (finished) "Borrar" else "Cancelar descarga", null, onConfirm, compact = true)
        },
        dismissButton = {
            EntreButton(
                if (finished) "Cancelar" else "Seguir descargando",
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
    dismissLabel: String = "Cancelar"
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
