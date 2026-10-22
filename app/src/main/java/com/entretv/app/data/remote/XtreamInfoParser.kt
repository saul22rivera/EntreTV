package com.entretv.app.data.remote

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Persona del reparto; [photoUrl] solo llega desde TMDB. */
@Serializable
data class CastMember(
    val name: String,
    val character: String = "",
    val photoUrl: String = ""
)

/**
 * Ficha de una película o serie: lo que manda el servidor (datos de TMDB) y, si hay
 * clave de TMDB, lo que se completa desde allí. Se guarda como JSON para verla sin conexión.
 */
@Serializable
@Immutable
data class ContentDetails(
    val plot: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val durationLabel: String = "",
    val cast: String = "",
    val director: String = "",
    val backdropUrl: String = "",
    val posterUrl: String = "",
    val trailerUrl: String = "",
    val tmdbId: String = "",
    val tagline: String = "",
    val releaseDate: String = "",
    val originalTitle: String = "",
    val originalLanguage: String = "",
    val countries: String = "",
    val companies: String = "",
    val certification: String = "",
    val voteCount: Int = 0,
    val budget: Long = 0,
    val revenue: Long = 0,
    val seasons: Int = 0,
    val episodes: Int = 0,
    val networks: String = "",
    val status: String = "",
    val creators: String = "",
    val castMembers: List<CastMember> = emptyList()
)

data class ParsedEpisode(
    val id: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String,
    val plot: String,
    val imageUrl: String,
    val durationSecs: Int
)

/**
 * Lectura tolerante de get_vod_info / get_series_info: los paneles Xtream mandan
 * "info": [] cuando no hay datos, números como texto y portadas como lista o texto.
 */
object XtreamInfoParser {

    private val yearRegex = Regex("(19|20)\\d{2}")

    fun movie(root: JsonObject): ContentDetails {
        val info = root["info"] as? JsonObject
        val data = root["movie_data"] as? JsonObject
        val base = details(info)
        return if (base.originalTitle.isEmpty() && data != null) {
            base.copy(originalTitle = data.str("o_name").takeIf { it != data.str("name") }.orEmpty())
        } else base
    }

    fun series(root: JsonObject): ContentDetails {
        val base = details(root["info"] as? JsonObject)
        val seasons = (root["seasons"] as? JsonArray)?.size ?: 0
        val episodes = episodes(root)
        return base.copy(
            seasons = seasons.takeIf { it > 0 } ?: episodes.map { it.season }.distinct().size,
            episodes = episodes.size
        )
    }

    fun episodes(root: JsonObject): List<ParsedEpisode> {
        val groups: List<Pair<Int?, JsonArray>> = when (val eps = root["episodes"]) {
            is JsonObject -> eps.entries.mapNotNull { (key, value) -> (value as? JsonArray)?.let { key.toIntOrNull() to it } }
            is JsonArray -> eps.mapIndexedNotNull { index, value -> (value as? JsonArray)?.let { (index + 1) to it } }
            else -> emptyList()
        }
        return groups.flatMap { (seasonKey, list) ->
            list.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val id = o.str("id").ifEmpty { return@mapNotNull null }
                val info = o["info"] as? JsonObject
                ParsedEpisode(
                    id = id,
                    season = o.str("season").toIntOrNull() ?: seasonKey ?: 1,
                    episodeNum = o.str("episode_num").toIntOrNull() ?: 0,
                    title = o.str("title"),
                    containerExtension = o.str("container_extension").ifEmpty { "mp4" },
                    plot = info?.str("plot", "description").orEmpty(),
                    imageUrl = info?.str("movie_image", "cover_big").orEmpty(),
                    durationSecs = info?.str("duration_secs")?.toIntOrNull()
                        ?: parseHms(info?.str("duration").orEmpty())
                )
            }
        }
    }

    private fun details(info: JsonObject?): ContentDetails {
        if (info == null) return ContentDetails()
        val durationSecs = info.str("duration_secs").toIntOrNull()
            ?: parseHms(info.str("duration")).takeIf { it > 0 }
            ?: info.str("episode_run_time", "runtime").toIntOrNull()?.times(60)
            ?: 0
        val release = info.str("releasedate", "releaseDate", "release_date", "year")
        val name = info.str("name", "title")
        return ContentDetails(
            plot = info.str("plot", "description"),
            genre = info.str("genre"),
            year = yearRegex.find(release)?.value.orEmpty(),
            rating = rating(info),
            durationLabel = durationLabel(durationSecs),
            cast = info.str("cast", "actors"),
            director = info.str("director"),
            backdropUrl = info.str("backdrop_path", "backdrop"),
            posterUrl = info.str("movie_image", "cover_big", "cover"),
            trailerUrl = trailer(info.str("youtube_trailer", "trailer")),
            tmdbId = info.str("tmdb_id", "tmdb").takeIf { it.all(Char::isDigit) && it != "0" }.orEmpty(),
            releaseDate = release.takeIf { it.length >= 8 }.orEmpty(),
            originalTitle = info.str("o_name", "original_name").takeIf { it.isNotEmpty() && it != name }.orEmpty(),
            countries = info.str("country"),
            certification = info.str("mpaa_rating", "age", "certification").takeIf { it != "0" }.orEmpty()
        )
    }

    private fun rating(info: JsonObject): String {
        val tenBased = info.str("rating").replace(',', '.').toDoubleOrNull()
            ?: info.str("rating_5based").replace(',', '.').toDoubleOrNull()?.times(2)
            ?: return ""
        return if (tenBased > 0) "%.1f".format(java.util.Locale.US, tenBased.coerceAtMost(10.0)) else ""
    }

    private fun trailer(raw: String): String = when {
        raw.isBlank() -> ""
        raw.startsWith("http") -> raw
        else -> "https://www.youtube.com/watch?v=$raw"
    }

    fun durationLabel(seconds: Int): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    private fun parseHms(raw: String): Int {
        val parts = raw.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }

    /** Primer valor no vacío entre varias claves; acepta texto, número o lista. */
    private fun JsonObject.str(vararg keys: String): String {
        for (key in keys) {
            val value = this[key]?.asText().orEmpty().trim()
            if (value.isNotEmpty() && value != "null") return value
        }
        return ""
    }

    private fun JsonElement.asText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonArray -> firstOrNull()?.asText()
        else -> null
    }
}
