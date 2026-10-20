package com.entretv.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,          // "M3U" | "XTREAM"
    val url: String,
    val username: String = "",
    val password: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "channels",
    indices = [Index(value = ["playlistId"]), Index(value = ["groupTitle"])]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val streamKey: String,
    val name: String,
    val logoUrl: String,
    val groupTitle: String,
    val streamUrl: String,
    val epgChannelId: String = "",
    val number: Int = 0,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "vod_movies",
    indices = [Index(value = ["playlistId"]), Index(value = ["categoryId"])]
)
data class VodMovieEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val movieId: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    /** Fecha Unix (segundos) en que el servidor la agregó; 0 si no informa. */
    val added: Long = 0,
    /** Puntuación 0–10; 0 si no informa. */
    val rating: Float = 0f,
    /** Lugar en la lista del servidor ("como viene de fábrica"). */
    val position: Int = 0,
    /** Id de TMDB si el panel lo informa; vacío si no. */
    val tmdbId: String = "",
    /** Año de estreno si el panel lo informa; 0 si no. */
    val year: Int = 0
)

@Entity(
    tableName = "series",
    indices = [Index(value = ["playlistId"])]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val seriesId: String,
    val name: String,
    val cover: String,
    val categoryName: String,
    val plot: String = "",
    val added: Long = 0,
    val rating: Float = 0f,
    val position: Int = 0,
    val tmdbId: String = "",
    val year: Int = 0
)

/** Ficha ampliada (Xtream + TMDB) o listas de TMDB guardadas como JSON para verlas sin conexión. */
@Entity(tableName = "details_cache")
data class DetailsCacheEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

/** Orden de las categorías tal como las manda el servidor. Se reescribe en cada actualización. */
@Entity(tableName = "categories", primaryKeys = ["playlistId", "section", "name"])
data class CategoryEntity(
    val playlistId: String,
    /** "LIVE" | "MOVIES" | "SERIES" */
    val section: String,
    val name: String,
    val position: Int
)

/** Lo que el usuario decidió sobre una categoría; sobrevive a las actualizaciones. */
@Entity(tableName = "category_prefs", primaryKeys = ["playlistId", "section", "name"])
data class CategoryPrefEntity(
    val playlistId: String,
    val section: String,
    val name: String,
    val hidden: Boolean = false,
    /** null = sin orden propio, va según el servidor. */
    val userPosition: Int? = null,
    /** null = se decide por el nombre (adult, xxx, +18…). */
    val adult: Boolean? = null
)

/** Canal, película o serie que el usuario ocultó. */
@Entity(tableName = "hidden_items")
data class HiddenItemEntity(
    @PrimaryKey val contentKey: String,
    val playlistId: String,
    val kind: String,
    val title: String,
    val imageUrl: String
)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["seriesKey"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,            // seriesKey#episodeId
    val seriesKey: String,                 // playlistId:seriesId
    val episodeId: String,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val plot: String = "",
    val imageUrl: String = "",
    val durationSecs: Int = 0
)

/** "Mi lista": canales, películas y series guardados por el usuario. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val contentKey: String,    // id del canal, película o serie
    val kind: String,                      // "LIVE" | "MOVIE" | "SERIES"
    val playlistId: String,
    val title: String,
    val imageUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "epg",
    primaryKeys = ["channelKey", "start"]
)
data class EpgEntity(
    val channelKey: String,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long
)

/** Película o episodio guardado en el almacenamiento interno para ver sin conexión. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val contentKey: String,    // id de la película o del episodio
    val kind: String,                      // "MOVIE" | "EPISODE"
    val playlistId: String,
    val title: String,
    /** Episodios: "T1:E3 · Nombre"; películas: la categoría. */
    val subtitle: String,
    val imageUrl: String,
    /** Episodios: playlistId:seriesId, para agrupar por serie. */
    val seriesKey: String = "",
    val season: Int = 0,
    val episodeNum: Int = 0,
    val sourceUrl: String,
    /** Nombre del archivo dentro de filesDir/downloads. */
    val fileName: String,
    /** -1 mientras el servidor no informa el tamaño. */
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: String = DownloadStatus.QUEUED,
    val error: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    val progress: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    val isDone: Boolean get() = status == DownloadStatus.DONE
}

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

/** Progreso de reproducción estilo Netflix (Live / Película / Episodio). */
@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val contentKey: String,    // LIVE: playlistId:id  |  MOVIE: playlistId:id  |  EPISODE: playlistId:seriesId#epId
    val kind: String,                      // "LIVE" | "MOVIE" | "EPISODE"
    val playlistId: String,
    val refId: String,
    val title: String,
    val imageUrl: String,
    val subtitle: String = "",             // p.ej. "T2:E5"
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Visto al 95% o más → se considera terminado. */
    val isCompleted: Boolean get() = durationMs > 0 && positionMs >= durationMs * 95 / 100

    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
