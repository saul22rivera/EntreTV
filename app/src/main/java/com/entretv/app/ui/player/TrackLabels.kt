@file:OptIn(UnstableApi::class)

package com.entretv.app.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.entretv.app.R
import java.util.Locale

/** Una pista elegible en el panel ("Español · 5.1 · Dolby Digital"). */
@Immutable
data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean
) {
    val format: Format get() = group.getTrackFormat(index)
}

/** Idioma en que se muestran los textos: el de [context] (la actividad sigue el idioma de la app). */
private fun Context.displayLocale(): Locale = resources.configuration.locales[0]

fun Tracks.audioOptions(context: Context): List<TrackOption> = options(C.TRACK_TYPE_AUDIO) { audioLabel(context, it) }
    .distinctBy { it.label }

fun Tracks.subtitleOptions(context: Context): List<TrackOption> = options(C.TRACK_TYPE_TEXT) { subtitleLabel(context, it) }

/** Una opción por resolución (la de mayor bitrate), de mayor a menor. */
fun Tracks.qualityOptions(context: Context): List<TrackOption> = options(C.TRACK_TYPE_VIDEO) { videoLabel(context, it) }
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

fun languageName(context: Context, code: String?): String {
    if (code.isNullOrBlank() || code == C.LANGUAGE_UNDETERMINED) return context.getString(R.string.player_language_unknown)
    val locale = context.displayLocale()
    val name = Locale.forLanguageTag(code).getDisplayLanguage(locale)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return code.uppercase()
    return name.replaceFirstChar { it.titlecase(locale) }
}

fun audioLabel(context: Context, format: Format): String = listOfNotNull(
    format.label?.takeIf { it.isNotBlank() } ?: languageName(context, format.language),
    channelsLabel(context, format.channelCount),
    audioCodecLabel(format.sampleMimeType),
    context.getString(R.string.player_audio_description).takeIf { format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 }
).joinToString(" · ")

fun subtitleLabel(context: Context, format: Format): String {
    val language = languageName(context, format.language)
    return listOfNotNull(
        language,
        format.label?.takeIf { it.isNotBlank() && !it.equals(language, true) },
        context.getString(R.string.player_subtitles_forced).takeIf { format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
        context.getString(R.string.player_subtitles_sdh).takeIf { format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0 }
    ).joinToString(" · ")
}

fun videoLabel(context: Context, format: Format): String {
    val name = when {
        format.height >= 2000 -> "4K · ${format.height}p"
        format.height >= 1400 -> "2K · ${format.height}p"
        format.height >= 1000 -> "Full HD · ${format.height}p"
        format.height >= 700 -> "HD · ${format.height}p"
        format.height > 0 -> "${format.height}p"
        else -> context.getString(R.string.player_video)
    }
    val rate = format.bitrate.takeIf { it > 0 }?.let { " · ${bitrateLabel(it)}" }.orEmpty()
    return name + rate
}

fun channelsLabel(context: Context, count: Int): String? = when (count) {
    Format.NO_VALUE, 0 -> null
    1 -> context.getString(R.string.player_channels_mono)
    2 -> context.getString(R.string.player_channels_stereo)
    6 -> "5.1"
    8 -> "7.1"
    else -> context.resources.getQuantityString(R.plurals.player_channels_count, count, count)
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
