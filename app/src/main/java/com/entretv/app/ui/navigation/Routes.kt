package com.entretv.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/**
 * [keepPlaying]: viene del mini reproductor de En vivo; al volver el canal sigue sonando ahí.
 * Con kind "CATCHUP", [startSec]–[endSec] es el programa pasado del canal y [title] su nombre.
 */
@Serializable
data class PlayerRoute(
    val contentKey: String,
    val kind: String,
    val keepPlaying: Boolean = false,
    val startSec: Long = 0L,
    val endSec: Long = 0L,
    val title: String = ""
)

/** Ficha de película ("MOVIE") o serie ("SERIES"). */
@Serializable
data class DetailsRoute(val contentKey: String, val kind: String)

@Serializable
data object DownloadsRoute

/** Guía en cuadrícula de En vivo, abierta en [category] (la clave sin limpiar). */
@Serializable
data class GuideRoute(val playlistId: String, val category: String? = null)
