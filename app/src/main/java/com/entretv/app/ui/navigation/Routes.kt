package com.entretv.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class PlayerRoute(val contentKey: String, val kind: String)

/** Ficha de película ("MOVIE") o serie ("SERIES"). */
@Serializable
data class DetailsRoute(val contentKey: String, val kind: String)

@Serializable
data object DownloadsRoute
