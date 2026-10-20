package com.entretv.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class XtreamLogin(
    @SerialName("user_info") val userInfo: UserInfo? = null
) {
    @Serializable
    data class UserInfo(
        val username: String = "",
        val status: String = "",
        // 1/0, "1"/"0" o true/false según el panel.
        val auth: JsonElement? = null,
        @SerialName("exp_date") val expDate: JsonElement? = null,
        @SerialName("is_trial") val isTrial: JsonElement? = null
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
    @SerialName("category_id") val categoryId: String = ""
)

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

private val yearRegex = Regex("(19|20)\\d{2}")

/** Año de un campo crudo ("2021", "2021-05-03"); 0 si no hay. */
fun JsonElement?.asYear(): Int = yearRegex.find(asField())?.value?.toIntOrNull() ?: 0

/** Año que muchos paneles pegan al nombre: "Película (2021)" o "Película - 2021". */
fun yearFromName(name: String): Int =
    Regex("[(\\[-]\\s*((?:19|20)\\d{2})\\s*[)\\]]?\\s*$").find(name.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 0

@Serializable
data class XtreamEpgResponse(
    @SerialName("epg_listings") val listings: List<Listing> = emptyList()
) {
    @Serializable
    data class Listing(
        @SerialName("title") val titleBase64: String = "",
        @SerialName("description") val descriptionBase64: String = "",
        @SerialName("start_timestamp") val startTimestamp: Long = 0,
        @SerialName("stop_timestamp") val stopTimestamp: Long = 0
    )
}
