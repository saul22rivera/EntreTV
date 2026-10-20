package com.entretv.app.player

/** Foto del estado técnico de la reproducción (para "Información técnica"). */
data class TechInfo(
    val resolution: String?,
    val videoCodec: String?,
    val frameRate: Float?,
    val videoBitrate: Int?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val bufferedMs: Long
)
