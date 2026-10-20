package com.entretv.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/** [keepPlaying]: viene del mini reproductor de En vivo; al volver el canal sigue sonando ahí. */
@Serializable
data class PlayerRoute(val contentKey: String, val kind: String, val keepPlaying: Boolean = false)

/** Ficha de película ("MOVIE") o serie ("SERIES"). */
@Serializable
data class DetailsRoute(val contentKey: String, val kind: String)

@Serializable
data object DownloadsRoute
