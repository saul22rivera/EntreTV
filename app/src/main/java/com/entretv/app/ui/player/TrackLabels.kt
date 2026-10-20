@file:OptIn(UnstableApi::class)

package com.entretv.app.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** Una pista elegible en el panel ("Español · 5.1 · Dolby Digital"). */
data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean
) {
    val format: Format get() = group.getTrackFormat(index)
}

private val spanish = Locale("es")

fun Tracks.audioOptions(): List<TrackOption> = options(C.TRACK_TYPE_AUDIO) { audioLabel(it) }
    .distinctBy { it.label }

fun Tracks.subtitleOptions(): List<TrackOption> = options(C.TRACK_TYPE_TEXT) { subtitleLabel(it) }

/** Una opción por resolución (la de mayor bitrate), de mayor a menor. */
fun Tracks.qualityOptions(): List<TrackOption> = options(C.TRACK_TYPE_VIDEO) { videoLabel(it) }
    .filter { it.format.height > 0 }
    .sortedWith(compareByDescending<TrackOption> { it.format.height }.thenByDescending { it.format.bitrate })
    .distinctBy { it.format.height }

/** True si el usuario fijó una resolución (no está en automático). */
fun Tracks.hasFixedQuality(): Boolean = groups.any { group ->
    group.type == C.TRACK_TYPE_VIDEO && group.length > 1 &&
        (0 until group.length).count { group.isTrackSelected(it) } == 1
}

private fun Tracks.options(type: Int, label: (Format) -> String): List<TrackOption> =
    groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length)
            .filter { group.isTrackSupported(it) }
            .map { i -> TrackOption(group, i, label(group.getTrackFormat(i)), group.isTrackSelected(i)) }
    }

fun languageName(code: String?): String {
    if (code.isNullOrBlank() || code == C.LANGUAGE_UNDETERMINED) return "Desconocido"
    val name = Locale.forLanguageTag(code).getDisplayLanguage(spanish)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return code.uppercase()
    return name.replaceFirstChar { it.titlecase(spanish) }
}

fun audioLabel(format: Format): String = listOfNotNull(
    format.label?.takeIf { it.isNotBlank() } ?: languageName(format.language),
    channelsLabel(format.channelCount),
    audioCodecLabel(format.sampleMimeType),
    "Audiodescripción".takeIf { format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 }
).joinToString(" · ")

fun subtitleLabel(format: Format): String = listOfNotNull(
    languageName(format.language),
    format.label?.takeIf { it.isNotBlank() && !it.equals(languageName(format.language), true) },
    "Forzados".takeIf { format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
    "Para sordos".takeIf { format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0 }
).joinToString(" · ")

fun videoLabel(format: Format): String {
    val name = when {
        format.height >= 2000 -> "4K · ${format.height}p"
        format.height >= 1400 -> "2K · ${format.height}p"
        format.height >= 1000 -> "Full HD · ${format.height}p"
        format.height >= 700 -> "HD · ${format.height}p"
        format.height > 0 -> "${format.height}p"
        else -> "Video"
    }
    val rate = format.bitrate.takeIf { it > 0 }?.let { " · ${bitrateLabel(it)}" }.orEmpty()
    return name + rate
}

fun channelsLabel(count: Int): String? = when (count) {
    Format.NO_VALUE, 0 -> null
    1 -> "Mono"
    2 -> "Estéreo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count canales"
}

fun audioCodecLabel(mime: String?): String? = when (mime) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
    MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
    MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
    MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_RAW -> "PCM"
    null -> null
    else -> mime.substringAfter('/').uppercase()
}

fun videoCodecLabel(mime: String?): String? = when (mime) {
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "H.265 / HEVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_VP8 -> "VP8"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
    null -> null
    else -> mime.substringAfter('/').uppercase()
}

fun bitrateLabel(bitsPerSecond: Int): String =
    if (bitsPerSecond >= 1_000_000) "%.1f Mbps".format(bitsPerSecond / 1_000_000f)
    else "${bitsPerSecond / 1_000} kbps"
