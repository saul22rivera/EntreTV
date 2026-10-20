package com.entretv.app.data.repo

import com.entretv.app.data.local.DetailsCacheDao
import com.entretv.app.data.local.DetailsCacheEntity
import com.entretv.app.data.remote.CastMember
import com.entretv.app.data.remote.ContentDetails
import com.entretv.app.data.remote.XtreamInfoParser
import com.entretv.app.data.settings.SettingsRepository
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Título de las listas de tendencias de TMDB, para cruzarlo con el catálogo. */
@Serializable
data class TrendingTitle(
    val tmdbId: String,
    val title: String,
    val originalTitle: String = "",
    val year: Int = 0
)

/**
 * Datos de TMDB (fichas ampliadas, reparto con fotos y tendencias) y caché en Room de
 * las fichas para verlas sin conexión. Sin clave de TMDB todo cae a lo que manda el servidor.
 */
@Singleton
class MetadataRepository @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
    private val cacheDao: DetailsCacheDao,
    private val settings: SettingsRepository
) {
    private companion object {
        const val API = "https://api.themoviedb.org/3/"
        const val IMAGES = "https://image.tmdb.org/t/p/"
        const val LANGUAGE = "es-MX"
        const val DETAILS_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val TRENDING_TTL_MS = 12L * 60 * 60 * 1000
        const val MAX_CAST = 20
    }

    private val http = client.newBuilder()
        .cache(null)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val trendingLock = Mutex()
    private val trendingMemory = HashMap<Boolean, List<TrendingTitle>>()

    private suspend fun apiKey(): String = settings.tmdbApiKey.first().trim()

    suspend fun hasTmdb(): Boolean = apiKey().isNotEmpty()

    // ============================ Caché de fichas ============================

    suspend fun cachedDetails(contentKey: String): ContentDetails? = withContext(Dispatchers.IO) {
        runCatching {
            cacheDao.get("d:$contentKey")?.let { json.decodeFromString(ContentDetails.serializer(), it.json) }
        }.getOrNull()
    }

    private suspend fun isFresh(contentKey: String): Boolean =
        cacheDao.get("d:$contentKey")?.let { System.currentTimeMillis() - it.fetchedAt < DETAILS_TTL_MS } == true

    suspend fun saveDetails(contentKey: String, details: ContentDetails) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDao.upsert(DetailsCacheEntity("d:$contentKey", json.encodeToString(ContentDetails.serializer(), details)))
        }
    }

    /**
     * Completa la ficha del servidor con TMDB. Lo que manda el servidor tiene prioridad
     * (suele venir en español); TMDB llena huecos y agrega reparto con fotos.
     * Ante cualquier error devuelve [base] tal cual.
     */
    suspend fun enrich(
        contentKey: String,
        base: ContentDetails,
        isSeries: Boolean,
        title: String,
        year: Int
    ): ContentDetails = withContext(Dispatchers.IO) {
        val key = apiKey()
        if (key.isEmpty()) return@withContext base
        try {
            if (isFresh(contentKey)) {
                cachedDetails(contentKey)?.takeIf { it.tmdbId.isNotEmpty() }?.let { cached ->
                    return@withContext merge(base, cached)
                }
            }
            val id = base.tmdbId.ifEmpty { search(key, isSeries, title, year) } ?: return@withContext base
            val tmdb = if (isSeries) tvDetails(key, id) else movieDetails(key, id)
            merge(base, tmdb)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            base
        }
    }

    private fun merge(base: ContentDetails, tmdb: ContentDetails): ContentDetails = base.copy(
        plot = base.plot.ifEmpty { tmdb.plot },
        genre = tmdb.genre.ifEmpty { base.genre },
        year = base.year.ifEmpty { tmdb.year },
        rating = base.rating.ifEmpty { tmdb.rating },
        durationLabel = base.durationLabel.ifEmpty { tmdb.durationLabel },
        cast = base.cast.ifEmpty { tmdb.castMembers.take(8).joinToString(", ") { it.name } },
        director = base.director.ifEmpty { tmdb.director },
        backdropUrl = base.backdropUrl.ifEmpty { tmdb.backdropUrl },
        posterUrl = base.posterUrl.ifEmpty { tmdb.posterUrl },
        trailerUrl = base.trailerUrl,
        tmdbId = tmdb.tmdbId.ifEmpty { base.tmdbId },
        tagline = tmdb.tagline,
        releaseDate = tmdb.releaseDate.ifEmpty { base.releaseDate },
        originalTitle = tmdb.originalTitle.ifEmpty { base.originalTitle },
        originalLanguage = tmdb.originalLanguage,
        countries = tmdb.countries.ifEmpty { base.countries },
        companies = tmdb.companies,
        certification = tmdb.certification.ifEmpty { base.certification },
        voteCount = tmdb.voteCount,
        budget = tmdb.budget,
        revenue = tmdb.revenue,
        seasons = tmdb.seasons.takeIf { it > 0 } ?: base.seasons,
        episodes = tmdb.episodes.takeIf { it > 0 } ?: base.episodes,
        networks = tmdb.networks,
        status = tmdb.status,
        creators = tmdb.creators,
        castMembers = tmdb.castMembers.ifEmpty { base.castMembers }
    )

    // ============================ Tendencias ============================

    /**
     * Títulos en tendencia y populares de TMDB, en orden. Se guardan 12 h; sin red se usa
     * lo último guardado. Vacío si no hay clave.
     */
    suspend fun trending(isSeries: Boolean): List<TrendingTitle> = withContext(Dispatchers.IO) {
        trendingLock.withLock {
            trendingMemory[isSeries]?.let { return@withContext it }
            val key = apiKey()
            if (key.isEmpty()) return@withContext emptyList()
            val cacheKey = if (isSeries) "trend:tv" else "trend:movie"
            val serializer = ListSerializer(TrendingTitle.serializer())
            val cached = cacheDao.get(cacheKey)
            val cachedList = cached?.let { runCatching { json.decodeFromString(serializer, it.json) }.getOrNull() }
            if (cached != null && cachedList != null && System.currentTimeMillis() - cached.fetchedAt < TRENDING_TTL_MS) {
                trendingMemory[isSeries] = cachedList
                return@withContext cachedList
            }
            val fresh = runCatching {
                val type = if (isSeries) "tv" else "movie"
                val paths = listOf("trending/$type/day" to 1, "trending/$type/week" to 1, "trending/$type/week" to 2, "$type/popular" to 1, "$type/popular" to 2)
                paths.flatMap { (path, page) ->
                    val results = get(key, path, mapOf("page" to page.toString()))["results"] as? JsonArray
                    results.orEmpty().mapNotNull { (it as? JsonObject)?.toTrending(isSeries) }
                }.distinctBy { it.tmdbId }
            }.getOrNull()
            val list = fresh?.takeIf { it.isNotEmpty() } ?: cachedList.orEmpty()
            if (!fresh.isNullOrEmpty()) {
                runCatching { cacheDao.upsert(DetailsCacheEntity(cacheKey, json.encodeToString(serializer, fresh))) }
            }
            if (list.isNotEmpty()) trendingMemory[isSeries] = list
            list
        }
    }

    private fun JsonObject.toTrending(isSeries: Boolean): TrendingTitle? {
        val id = this["id"].text().ifEmpty { return null }
        val title = (if (isSeries) this["name"] else this["title"]).text()
        val original = (if (isSeries) this["original_name"] else this["original_title"]).text()
        val date = (if (isSeries) this["first_air_date"] else this["release_date"]).text()
        return TrendingTitle(id, title, original, date.take(4).toIntOrNull() ?: 0)
    }

    // ============================ API de TMDB ============================

    private fun search(key: String, isSeries: Boolean, title: String, year: Int): String? {
        if (title.isBlank()) return null
        val params = buildMap {
            put("query", title)
            if (year > 0) put(if (isSeries) "first_air_date_year" else "year", year.toString())
        }
        val results = get(key, if (isSeries) "search/tv" else "search/movie", params)["results"] as? JsonArray
        return (results?.firstOrNull() as? JsonObject)?.get("id").text().ifEmpty { null }
    }

    private fun movieDetails(key: String, id: String): ContentDetails {
        val o = get(key, "movie/$id", mapOf("append_to_response" to "credits,release_dates"))
        val credits = o["credits"] as? JsonObject
        val runtime = o["runtime"].text().toIntOrNull() ?: 0
        return ContentDetails(
            plot = o["overview"].text(),
            genre = o.names("genres"),
            year = o["release_date"].text().take(4),
            rating = rating(o),
            durationLabel = XtreamInfoParser.durationLabel(runtime * 60),
            director = (credits?.get("crew") as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it["job"].text() == "Director" }
                .joinToString(", ") { it["name"].text() },
            backdropUrl = image(o["backdrop_path"].text(), "w1280"),
            posterUrl = image(o["poster_path"].text(), "w500"),
            tmdbId = id,
            tagline = o["tagline"].text(),
            releaseDate = o["release_date"].text(),
            originalTitle = o["original_title"].text().takeIf { it != o["title"].text() }.orEmpty(),
            originalLanguage = languageName(o["original_language"].text()),
            countries = o.names("production_countries"),
            companies = o.names("production_companies", limit = 4),
            certification = movieCertification(o),
            voteCount = o["vote_count"].text().toIntOrNull() ?: 0,
            budget = o["budget"].text().toLongOrNull() ?: 0,
            revenue = o["revenue"].text().toLongOrNull() ?: 0,
            status = status(o["status"].text()),
            castMembers = cast(credits?.get("cast") as? JsonArray) { it["character"].text() }
        )
    }

    private fun tvDetails(key: String, id: String): ContentDetails {
        val o = get(key, "tv/$id", mapOf("append_to_response" to "aggregate_credits,content_ratings"))
        val credits = o["aggregate_credits"] as? JsonObject
        val runtime = (o["episode_run_time"] as? JsonArray)?.firstOrNull().text().toIntOrNull() ?: 0
        return ContentDetails(
            plot = o["overview"].text(),
            genre = o.names("genres"),
            year = o["first_air_date"].text().take(4),
            rating = rating(o),
            durationLabel = XtreamInfoParser.durationLabel(runtime * 60),
            backdropUrl = image(o["backdrop_path"].text(), "w1280"),
            posterUrl = image(o["poster_path"].text(), "w500"),
            tmdbId = id,
            tagline = o["tagline"].text(),
            releaseDate = o["first_air_date"].text(),
            originalTitle = o["original_name"].text().takeIf { it != o["name"].text() }.orEmpty(),
            originalLanguage = languageName(o["original_language"].text()),
            countries = o.names("production_countries"),
            companies = o.names("production_companies", limit = 4),
            certification = tvCertification(o),
            voteCount = o["vote_count"].text().toIntOrNull() ?: 0,
            seasons = o["number_of_seasons"].text().toIntOrNull() ?: 0,
            episodes = o["number_of_episodes"].text().toIntOrNull() ?: 0,
            networks = o.names("networks", limit = 3),
            status = status(o["status"].text()),
            creators = o.names("created_by", limit = 3),
            castMembers = cast(credits?.get("cast") as? JsonArray) { member ->
                ((member["roles"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("character").text()
            }
        )
    }

    private fun cast(list: JsonArray?, character: (JsonObject) -> String): List<CastMember> =
        list.orEmpty().mapNotNull { it as? JsonObject }.take(MAX_CAST).map {
            CastMember(
                name = it["name"].text(),
                character = character(it),
                photoUrl = image(it["profile_path"].text(), "w185")
            )
        }.filter { it.name.isNotEmpty() }

    private fun rating(o: JsonObject): String {
        val value = o["vote_average"].text().toDoubleOrNull() ?: return ""
        return if (value > 0) "%.1f".format(Locale.US, value) else ""
    }

    private fun movieCertification(o: JsonObject): String {
        val results = ((o["release_dates"] as? JsonObject)?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        for (country in listOf("MX", "US", "ES")) {
            val entry = results.firstOrNull { it["iso_3166_1"].text() == country } ?: continue
            val cert = (entry["release_dates"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.get("certification").text().ifEmpty { null } }
                .firstOrNull()
            if (cert != null) return cert
        }
        return ""
    }

    private fun tvCertification(o: JsonObject): String {
        val results = ((o["content_ratings"] as? JsonObject)?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        for (country in listOf("MX", "US", "ES")) {
            results.firstOrNull { it["iso_3166_1"].text() == country }?.get("rating").text().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    private fun status(raw: String): String = when (raw) {
        "Returning Series" -> "En emisión"
        "Ended" -> "Finalizada"
        "Canceled" -> "Cancelada"
        "In Production" -> "En producción"
        "Planned" -> "Anunciada"
        "Pilot" -> "Piloto"
        "Released" -> "Estrenada"
        "Post Production" -> "Posproducción"
        "Rumored" -> "Rumor"
        else -> raw
    }

    private fun languageName(code: String): String {
        if (code.isBlank()) return ""
        val name = Locale(code).getDisplayLanguage(Locale("es", "MX"))
        return name.replaceFirstChar { it.titlecase(Locale("es", "MX")) }.takeIf { it != code } ?: code.uppercase()
    }

    private fun image(path: String, size: String): String = if (path.isBlank()) "" else "$IMAGES$size$path"

    private fun JsonObject.names(key: String, limit: Int = 6): String =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("name").text().ifEmpty { null } }
            .take(limit).joinToString(", ")

    private fun JsonElement?.text(): String = when (this) {
        is JsonPrimitive -> contentOrNull?.trim()?.takeIf { it != "null" }.orEmpty()
        else -> ""
    }

    /** Petición a TMDB. La clave v3 va como parámetro; un token v4 (largo) como Bearer. */
    private fun get(key: String, path: String, params: Map<String, String>): JsonObject {
        val url: HttpUrl = (API + path).toHttpUrl().newBuilder().apply {
            addQueryParameter("language", LANGUAGE)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            if (key.length <= 40) addQueryParameter("api_key", key)
        }.build()
        val request = Request.Builder().url(url).apply {
            if (key.length > 40) header("Authorization", "Bearer $key")
            header("Accept", "application/json")
        }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("TMDB ${response.code}")
            return json.parseToJsonElement(response.body!!.string()) as JsonObject
        }
    }
}
