package com.entretv.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class XtreamLogin(
    @SerialName("user_info") val userInfo: UserInfo? = null,
    // Crudo: algunos paneles mandan [] en vez de objeto y no debe romper el inicio de sesión.
    @SerialName("server_info") val serverInfo: JsonElement? = null
) {
    /** Zona horaria del servidor ("America/Mexico_City"); vacía si no la informa. */
    val serverTimezone: String
        get() = ((serverInfo as? JsonObject)?.get("timezone") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    @Serializable
    data class UserInfo(
        val username: String = "",
        val status: String = "",
        // 1/0, "1"/"0" o true/false según el panel.
        val auth: JsonElement? = null,
        @SerialName("exp_date") val expDate: JsonElement? = null,
        @SerialName("is_trial") val isTrial: JsonElement? = null,
        @SerialName("max_connections") val maxConnections: JsonElement? = null,
        @SerialName("active_cons") val activeConnections: JsonElement? = null
    )
}

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String
)

@Serializable
data class XtreamStream(
    @SerialName("stream_id") val streamId: Int,
    val name: String,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    // 1/0 o "1"/"0" según el panel.
    @SerialName("tv_archive") val tvArchive: JsonElement? = null,
    @SerialName("tv_archive_duration") val tvArchiveDuration: JsonElement? = null
) {
    /** Días de programas pasados que guarda el servidor; 0 = sin catch-up. */
    val archiveDays: Int
        get() = if (tvArchive.asField() != "1") 0 else (tvArchiveDuration.asField().toIntOrNull() ?: 1).coerceIn(1, 30)
}

@Serializable
data class XtreamVodStream(
    @SerialName("stream_id") val streamId: Int,
    val name: String,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("container_extension") val containerExtension: String = "mp4",
    // Los paneles mandan número, texto o vacío: se leen crudos.
    val added: JsonElement? = null,
    val rating: JsonElement? = null,
    val tmdb: JsonElement? = null,
    @SerialName("tmdb_id") val tmdbId: JsonElement? = null,
    val year: JsonElement? = null
)

@Serializable
data class XtreamSeries(
    @SerialName("series_id") val seriesId: Int,
    val name: String,
    @SerialName("cover") val cover: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    val plot: String? = null,
    @SerialName("last_modified") val lastModified: JsonElement? = null,
    val rating: JsonElement? = null,
    val tmdb: JsonElement? = null,
    @SerialName("tmdb_id") val tmdbId: JsonElement? = null,
    // Muchos paneles mandan las dos variantes a la vez.
    val releaseDate: JsonElement? = null,
    @SerialName("release_date") val releaseDateAlt: JsonElement? = null
)

/** Fecha Unix (segundos) de un campo crudo; 0 si no viene o no es número. */
fun JsonElement?.asEpochSeconds(): Long =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.toLongOrNull() ?: 0L

/** Puntuación 0–10 de un campo crudo; 0 si no viene. */
fun JsonElement?.asRating(): Float =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.replace(',', '.')?.toFloatOrNull()
        ?.takeIf { it.isFinite() }?.coerceIn(0f, 10f) ?: 0f

/** Texto de un campo crudo ("123", 123 o null). */
fun JsonElement?.asField(): String =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it != "null" && it != "0" }.orEmpty()

/** Año de un campo crudo ("2021", "2021-05-03"); 0 si no hay. */
fun JsonElement?.asYear(): Int {
    val s = asField()
    for (i in 0..s.length - 4) {
        if (isYearAt(s, i)) return s.substring(i, i + 4).toInt()
    }
    return 0
}

/** "19xx" o "20xx" en esa posición. Se recorre a mano: son miles de títulos por actualización. */
private fun isYearAt(s: String, i: Int): Boolean =
    ((s[i] == '1' && s[i + 1] == '9') || (s[i] == '2' && s[i + 1] == '0')) && s[i + 2] in '0'..'9' && s[i + 3] in '0'..'9'

private fun isRegexSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

/** Año que muchos paneles pegan al nombre: "Película (2021)" o "Película - 2021". */
fun yearFromName(name: String): Int {
    val s = name.trim()
    var i = s.length - 1
    while (i >= 0 && isRegexSpace(s[i])) i--
    if (i >= 0 && (s[i] == ')' || s[i] == ']')) i--
    while (i >= 0 && isRegexSpace(s[i])) i--
    if (i < 3 || !isYearAt(s, i - 3)) return 0
    val year = s.substring(i - 3, i + 1).toInt()
    i -= 4
    while (i >= 0 && isRegexSpace(s[i])) i--
    return if (i >= 0 && s[i] in "([-") year else 0
}

@Serializable
data class XtreamEpgResponse(
    @SerialName("epg_listings") val listings: List<Listing> = emptyList()
) {
    @Serializable
    data class Listing(
        @SerialName("title") val titleBase64: String = "",
        @SerialName("description") val descriptionBase64: String = "",
        @SerialName("start_timestamp") val startTimestamp: Long = 0,
        @SerialName("stop_timestamp") val stopTimestamp: Long = 0,
        /** Solo en get_simple_data_table: 1 si el programa está grabado en el servidor. */
        @SerialName("has_archive") val hasArchive: JsonElement? = null
    )
}
