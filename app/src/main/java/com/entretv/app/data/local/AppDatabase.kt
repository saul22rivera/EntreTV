package com.entretv.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY addedAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY addedAt")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number, name")
    fun observeByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number, name")
    suspend fun getByPlaylist(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: String): ChannelEntity?

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun count(playlistId: String): Int

    @Query("SELECT id FROM channels WHERE playlistId = :playlistId")
    suspend fun ids(playlistId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    /** Cambia la raíz del servidor en las direcciones guardadas (cuando el servidor se muda). */
    @Query(
        "UPDATE channels SET streamUrl = :newRoot || substr(streamUrl, length(:oldRoot) + 1) " +
            "WHERE playlistId = :playlistId AND substr(streamUrl, 1, length(:oldRoot)) = :oldRoot"
    )
    suspend fun rewriteRoot(playlistId: String, oldRoot: String, newRoot: String)

    /** Solo escribe si cambió: sin filas tocadas Room no avisa y las listas no se rearman. */
    @Query("UPDATE channels SET archiveDays = :days WHERE id = :id AND archiveDays != :days")
    suspend fun setArchiveDays(id: String, days: Int): Int
}

@Dao
interface VodDao {
    @Query("SELECT * FROM vod_movies WHERE id = :id")
    suspend fun getMovie(id: String): VodMovieEntity?

    @Query("SELECT * FROM vod_movies WHERE playlistId = :playlistId ORDER BY position, name")
    fun observeMovies(playlistId: String): Flow<List<VodMovieEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY position, name")
    fun observeSeries(playlistId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM vod_movies WHERE playlistId = :playlistId")
    suspend fun getMovies(playlistId: String): List<VodMovieEntity>

    @Query("SELECT COUNT(*) FROM vod_movies WHERE playlistId = :playlistId")
    suspend fun countMovies(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun countSeries(playlistId: String): Int

    @Query("SELECT id FROM vod_movies WHERE playlistId = :playlistId")
    suspend fun movieIds(playlistId: String): List<String>

    @Query("SELECT id FROM series WHERE playlistId = :playlistId")
    suspend fun seriesIds(playlistId: String): List<String>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId")
    suspend fun getSeriesList(playlistId: String): List<SeriesEntity>

    @Query("SELECT * FROM vod_movies WHERE playlistId = :playlistId ORDER BY added DESC, position LIMIT :limit")
    suspend fun recentMovies(playlistId: String, limit: Int): List<VodMovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY added DESC, position LIMIT :limit")
    suspend fun recentSeries(playlistId: String, limit: Int): List<SeriesEntity>

    /** Solo lo que necesita la descarga de pósters: de lo más nuevo a lo más viejo. */
    @Query(
        "SELECT id, name, categoryName, icon AS url FROM vod_movies " +
            "WHERE playlistId = :playlistId AND icon != '' ORDER BY added DESC, position"
    )
    suspend fun moviePosters(playlistId: String): List<PosterRow>

    @Query(
        "SELECT id, name, categoryName, cover AS url FROM series " +
            "WHERE playlistId = :playlistId AND cover != '' ORDER BY added DESC, position"
    )
    suspend fun seriesPosters(playlistId: String): List<PosterRow>

    /** Todas las imágenes que una tarjeta puede mostrar, de todas las cuentas. */
    @Query(
        "SELECT icon FROM vod_movies WHERE icon != '' " +
            "UNION SELECT cover FROM series WHERE cover != '' " +
            "UNION SELECT imageUrl FROM episodes WHERE imageUrl != '' " +
            "UNION SELECT imageUrl FROM downloads WHERE imageUrl != '' " +
            "UNION SELECT imageUrl FROM favorites WHERE imageUrl != '' " +
            "UNION SELECT imageUrl FROM playback_progress WHERE imageUrl != '' " +
            "UNION SELECT imageUrl FROM hidden_items WHERE imageUrl != ''"
    )
    suspend fun allPosterUrls(): List<String>

    @Query("SELECT id FROM vod_movies WHERE playlistId = :playlistId AND categoryName IN (:categories)")
    suspend fun movieIdsIn(playlistId: String, categories: List<String>): List<String>

    @Query("SELECT id FROM series WHERE playlistId = :playlistId AND categoryName IN (:categories)")
    suspend fun seriesIdsIn(playlistId: String, categories: List<String>): List<String>

    @Query("SELECT * FROM episodes WHERE seriesKey = :seriesKey ORDER BY season, episodeNum")
    fun observeEpisodes(seriesKey: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesKey = :seriesKey ORDER BY season, episodeNum")
    suspend fun getEpisodes(seriesKey: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: String): EpisodeEntity?

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeries(id: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovies(movies: List<VodMovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(series: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM vod_movies WHERE playlistId = :playlistId")
    suspend fun deleteMoviesByPlaylist(playlistId: String)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeriesByPlaylist(playlistId: String)

    @Query("DELETE FROM episodes WHERE seriesKey LIKE :playlistId || ':%'")
    suspend fun deleteEpisodesByPlaylist(playlistId: String)

    @Query("DELETE FROM episodes WHERE seriesKey = :seriesKey")
    suspend fun deleteEpisodesForSeries(seriesKey: String)

    @Query(
        "UPDATE vod_movies SET streamUrl = :newRoot || substr(streamUrl, length(:oldRoot) + 1) " +
            "WHERE playlistId = :playlistId AND substr(streamUrl, 1, length(:oldRoot)) = :oldRoot"
    )
    suspend fun rewriteMovieRoot(playlistId: String, oldRoot: String, newRoot: String)

    @Query(
        "UPDATE episodes SET streamUrl = :newRoot || substr(streamUrl, length(:oldRoot) + 1) " +
            "WHERE seriesKey LIKE :playlistId || ':%' AND substr(streamUrl, 1, length(:oldRoot)) = :oldRoot"
    )
    suspend fun rewriteEpisodeRoot(playlistId: String, oldRoot: String, newRoot: String)
}

/** Póster de una película o serie, sin el resto de la fila. */
data class PosterRow(
    val id: String,
    val name: String,
    val categoryName: String,
    val url: String
)

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PlaybackProgressEntity)

    /** Fila "Seguir viendo" estilo Netflix: lo último visto primero (de una serie llegan varios episodios). */
    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT 300")
    fun observeContinueWatching(profileId: String): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun get(profileId: String, contentKey: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND contentKey IN (:keys)")
    suspend fun getMany(profileId: String, keys: List<String>): List<PlaybackProgressEntity>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND contentKey = :contentKey")
    fun observe(profileId: String, contentKey: String): Flow<PlaybackProgressEntity?>

    @Query(
        "SELECT * FROM playback_progress WHERE profileId = :profileId AND contentKey LIKE :prefix || '%' " +
            "ORDER BY updatedAt DESC"
    )
    fun observeByPrefix(profileId: String, prefix: String): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun delete(profileId: String, contentKey: String)

    /** Reinicia una serie completa: borra el progreso de todos sus episodios. */
    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND contentKey LIKE :seriesPrefix || '%'")
    suspend fun deleteSeriesProgress(profileId: String, seriesPrefix: String)

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY sortOrder, createdAt")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programs: List<EpgEntity>)

    @Query("SELECT * FROM epg WHERE channelKey = :channelKey AND end > :now ORDER BY start LIMIT :limit")
    suspend fun upcomingFor(channelKey: String, now: Long, limit: Int): List<EpgEntity>

    @Query("SELECT * FROM epg WHERE channelKey IN (:channelKeys) AND end > :from AND start < :to ORDER BY start")
    suspend fun between(channelKeys: List<String>, from: Long, to: Long): List<EpgEntity>

    @Query("DELETE FROM epg WHERE end < :threshold")
    suspend fun purgeOld(threshold: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND contentKey = :contentKey)")
    fun observeIsFavorite(profileId: String, contentKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND contentKey = :contentKey)")
    suspend fun isFavorite(profileId: String, contentKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun delete(profileId: String, contentKey: String)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)
}

@Dao
interface DetailsCacheDao {
    @Query("SELECT * FROM details_cache WHERE cacheKey = :key")
    suspend fun get(key: String): DetailsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DetailsCacheEntity)

    @Query("DELETE FROM details_cache WHERE fetchedAt < :threshold")
    suspend fun purgeOlderThan(threshold: Long)
}

@Dao
abstract class CatalogDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY position")
    abstract fun observeCategories(playlistId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND section = :section ORDER BY position")
    abstract suspend fun getCategories(playlistId: String, section: String): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND section = :section")
    abstract suspend fun deleteCategories(playlistId: String, section: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCategories(categories: List<CategoryEntity>)

    @Transaction
    open suspend fun replaceCategories(playlistId: String, section: String, categories: List<CategoryEntity>) {
        deleteCategories(playlistId, section)
        insertCategories(categories)
    }

    @Query("SELECT * FROM category_prefs WHERE playlistId = :playlistId")
    abstract fun observePrefs(playlistId: String): Flow<List<CategoryPrefEntity>>

    @Query("SELECT * FROM category_prefs WHERE playlistId = :playlistId")
    abstract suspend fun getPrefs(playlistId: String): List<CategoryPrefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPrefs(prefs: List<CategoryPrefEntity>)

    @Query("SELECT * FROM hidden_items ORDER BY title")
    abstract fun observeHidden(): Flow<List<HiddenItemEntity>>

    @Query("SELECT contentKey FROM hidden_items")
    abstract suspend fun hiddenKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun hide(item: HiddenItemEntity)

    @Query("DELETE FROM hidden_items WHERE contentKey = :contentKey")
    abstract suspend fun unhide(contentKey: String)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    abstract suspend fun deleteAllCategories(playlistId: String)

    @Query("DELETE FROM category_prefs WHERE playlistId = :playlistId")
    abstract suspend fun deleteAllPrefs(playlistId: String)

    @Query("DELETE FROM hidden_items WHERE playlistId = :playlistId")
    abstract suspend fun deleteHiddenByPlaylist(playlistId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE contentKey = :contentKey")
    fun observe(contentKey: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE seriesKey = :seriesKey")
    fun observeSeries(seriesKey: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE contentKey = :contentKey")
    suspend fun get(contentKey: String): DownloadEntity?

    /** La siguiente en la cola; RUNNING incluye las que quedaron a medias si se cerró la app. */
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'RUNNING') ORDER BY addedAt LIMIT 1")
    suspend fun nextPending(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun pendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total WHERE contentKey = :contentKey")
    suspend fun updateProgress(contentKey: String, downloaded: Long, total: Long)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE contentKey = :contentKey")
    suspend fun setStatus(contentKey: String, status: String, error: String = "")

    @Query("DELETE FROM downloads WHERE contentKey = :contentKey")
    suspend fun delete(contentKey: String)

    /** Episodios de una serie en cola, bajándose o en pausa (lo que "Detener descargas" cancela). */
    @Query("SELECT * FROM downloads WHERE seriesKey = :seriesKey AND status IN ('QUEUED', 'RUNNING', 'PAUSED')")
    suspend fun pendingForSeries(seriesKey: String): List<DownloadEntity>

    @Query(
        "UPDATE downloads SET sourceUrl = :newRoot || substr(sourceUrl, length(:oldRoot) + 1) " +
            "WHERE playlistId = :playlistId AND substr(sourceUrl, 1, length(:oldRoot)) = :oldRoot"
    )
    suspend fun rewriteRoot(playlistId: String, oldRoot: String, newRoot: String)
}

@Database(
    entities = [
        PlaylistEntity::class, ChannelEntity::class, VodMovieEntity::class,
        SeriesEntity::class, EpisodeEntity::class, EpgEntity::class,
        PlaybackProgressEntity::class, FavoriteEntity::class,
        CategoryEntity::class, CategoryPrefEntity::class, HiddenItemEntity::class,
        DownloadEntity::class, DetailsCacheEntity::class, ProfileEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun vodDao(): VodDao
    abstract fun progressDao(): ProgressDao
    abstract fun epgDao(): EpgDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun catalogDao(): CatalogDao
    abstract fun downloadDao(): DownloadDao
    abstract fun detailsCacheDao(): DetailsCacheDao
    abstract fun profileDao(): ProfileDao

    companion object {
        /** Una instalación nueva arranca con un perfil, igual que una actualizada. */
        val CREATE_DEFAULT_PROFILE = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                insertDefaultProfile(db)
            }
        }

        private fun insertDefaultProfile(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO profiles (id, name, avatar, isKids, pinHash, pinSalt, createdAt, sortOrder) " +
                    "VALUES (?, '', 0, 0, NULL, NULL, ?, 0)",
                arrayOf<Any>(DEFAULT_PROFILE_ID, System.currentTimeMillis())
            )
        }

        /**
         * Perfiles: el progreso y Mi lista pasan a ser de un perfil. Todo lo guardado queda en el
         * perfil por defecto; las tablas se rehacen porque la clave primaria suma profileId.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS profiles (
                        id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, avatar INTEGER NOT NULL,
                        isKids INTEGER NOT NULL, pinHash TEXT, pinSalt TEXT,
                        createdAt INTEGER NOT NULL, sortOrder INTEGER NOT NULL)"""
                )
                insertDefaultProfile(db)

                db.execSQL(
                    """CREATE TABLE playback_progress_new (
                        profileId TEXT NOT NULL, contentKey TEXT NOT NULL, kind TEXT NOT NULL,
                        playlistId TEXT NOT NULL, refId TEXT NOT NULL,
                        title TEXT NOT NULL, imageUrl TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '',
                        positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL, PRIMARY KEY(profileId, contentKey))"""
                )
                db.execSQL(
                    """INSERT OR REPLACE INTO playback_progress_new
                        (profileId, contentKey, kind, playlistId, refId, title, imageUrl, subtitle, positionMs, durationMs, updatedAt)
                        SELECT ?, contentKey, kind, playlistId, refId, title, imageUrl, subtitle, positionMs, durationMs, updatedAt
                        FROM playback_progress""",
                    arrayOf<Any>(DEFAULT_PROFILE_ID)
                )
                db.execSQL("DROP TABLE playback_progress")
                db.execSQL("ALTER TABLE playback_progress_new RENAME TO playback_progress")

                db.execSQL(
                    """CREATE TABLE favorites_new (
                        profileId TEXT NOT NULL, contentKey TEXT NOT NULL, kind TEXT NOT NULL,
                        playlistId TEXT NOT NULL, title TEXT NOT NULL, imageUrl TEXT NOT NULL,
                        addedAt INTEGER NOT NULL, PRIMARY KEY(profileId, contentKey))"""
                )
                db.execSQL(
                    """INSERT OR REPLACE INTO favorites_new
                        (profileId, contentKey, kind, playlistId, title, imageUrl, addedAt)
                        SELECT ?, contentKey, kind, playlistId, title, imageUrl, addedAt FROM favorites""",
                    arrayOf<Any>(DEFAULT_PROFILE_ID)
                )
                db.execSQL("DROP TABLE favorites")
                db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN archiveDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vod_movies ADD COLUMN tmdbId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vod_movies ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN tmdbId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE series ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS details_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL)"""
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS downloads (
                        contentKey TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL,
                        playlistId TEXT NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL,
                        imageUrl TEXT NOT NULL, seriesKey TEXT NOT NULL, season INTEGER NOT NULL,
                        episodeNum INTEGER NOT NULL, sourceUrl TEXT NOT NULL, fileName TEXT NOT NULL,
                        totalBytes INTEGER NOT NULL, downloadedBytes INTEGER NOT NULL,
                        status TEXT NOT NULL, error TEXT NOT NULL, addedAt INTEGER NOT NULL)"""
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vod_movies ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS categories (
                        playlistId TEXT NOT NULL, section TEXT NOT NULL, name TEXT NOT NULL,
                        position INTEGER NOT NULL, PRIMARY KEY(playlistId, section, name))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS category_prefs (
                        playlistId TEXT NOT NULL, section TEXT NOT NULL, name TEXT NOT NULL,
                        hidden INTEGER NOT NULL, userPosition INTEGER, adult INTEGER,
                        PRIMARY KEY(playlistId, section, name))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS hidden_items (
                        contentKey TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL,
                        kind TEXT NOT NULL, title TEXT NOT NULL, imageUrl TEXT NOT NULL)"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vod_movies ADD COLUMN added INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vod_movies ADD COLUMN rating REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN added INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN rating REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN plot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE episodes ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE episodes ADD COLUMN durationSecs INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS favorites (
                        contentKey TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL,
                        playlistId TEXT NOT NULL, title TEXT NOT NULL, imageUrl TEXT NOT NULL,
                        addedAt INTEGER NOT NULL)"""
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS vod_movies (
                        id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL,
                        movieId TEXT NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL,
                        categoryId TEXT NOT NULL, categoryName TEXT NOT NULL, streamUrl TEXT NOT NULL)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS series (
                        id TEXT NOT NULL PRIMARY KEY, playlistId TEXT NOT NULL,
                        seriesId TEXT NOT NULL, name TEXT NOT NULL, cover TEXT NOT NULL,
                        categoryName TEXT NOT NULL, plot TEXT NOT NULL DEFAULT '')"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS episodes (
                        id TEXT NOT NULL PRIMARY KEY, seriesKey TEXT NOT NULL,
                        episodeId TEXT NOT NULL, season INTEGER NOT NULL, episodeNum INTEGER NOT NULL,
                        title TEXT NOT NULL, streamUrl TEXT NOT NULL)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS playback_progress (
                        contentKey TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL,
                        playlistId TEXT NOT NULL, refId TEXT NOT NULL,
                        title TEXT NOT NULL, imageUrl TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '',
                        positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_movies_playlistId ON vod_movies(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_movies_categoryId ON vod_movies(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_playlistId ON series(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_seriesKey ON episodes(seriesKey)")
            }
        }
    }
}
