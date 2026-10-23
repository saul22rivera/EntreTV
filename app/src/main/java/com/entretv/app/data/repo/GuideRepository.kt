package com.entretv.app.data.repo

import android.content.Context
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.entretv.app.data.local.ChannelDao
import com.entretv.app.data.local.ChannelEntity
import com.entretv.app.data.local.EpgDao
import com.entretv.app.data.local.EpgEntity
import com.entretv.app.data.local.PlaylistDao
import com.entretv.app.data.local.PlaylistEntity
import com.entretv.app.data.remote.XtreamApi
import com.entretv.app.data.remote.XtreamEpgResponse
import com.entretv.app.data.remote.XtreamLogin
import com.entretv.app.data.remote.asField
import com.entretv.app.ui.isLowRamDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Un programa de la guía en cuadrícula. Horas en segundos Unix. */
@Immutable
data class GuideProgram(
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    /** El servidor lo tiene grabado: se puede ver de nuevo (catch-up). */
    val archived: Boolean = false
)

/**
 * Guía en cuadrícula de En vivo: programas por canal (memoria → Room → servidor), qué
 * canales tienen catch-up y la dirección para ver un programa pasado.
 *
 * Las direcciones llevan usuario y contraseña: no se registran nunca.
 */
@Singleton
class GuideRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val xtreamApi: XtreamApi,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val json: Json
) {
    private class Entry(val programs: List<GuideProgram>, val fetchedAt: Long)

    private val cache = LruCache<String, Entry>(if (isLowRamDevice(context)) 120 else 400)

    /** Pocos pedidos a la vez: la guía no debe quitarle conexión al video ni saturar el panel. */
    private val requestSlots = Semaphore(3)

    private val metaLock = Mutex()
    private val zones = HashMap<String, TimeZone?>()
    private val categoryIds = HashMap<String, Map<String, String>>()
    private val archiveCheckedAt = HashMap<String, Long>()

    /** Programas ya bajados en esta sesión (sin ir a la red ni a la base). */
    fun cached(channel: ChannelEntity): List<GuideProgram>? = cache.get(channel.id)?.programs

    fun isFresh(channel: ChannelEntity): Boolean =
        cache.get(channel.id)?.let { System.currentTimeMillis() - it.fetchedAt < FRESH_MS } == true

    /** Lo guardado en Room para estos canales entre [from] y [to]: se muestra al instante al abrir. */
    suspend fun stored(channels: List<ChannelEntity>, from: Long, to: Long): Map<String, List<GuideProgram>> =
        withContext(Dispatchers.IO) {
            val byKey = channels.groupBy { it.epgKey }
            val result = HashMap<String, List<GuideProgram>>()
            byKey.keys.chunked(500).forEach { keys ->
                epgDao.between(keys, from, to).groupBy { it.channelKey }.forEach { (key, rows) ->
                    val programs = rows.map { GuideProgram(it.title, it.description, it.start, it.end) }
                    byKey[key]?.forEach { ch -> result[ch.id] = programs }
                }
            }
            result
        }

    /**
     * Programas del canal desde el servidor. Con catch-up se pide la guía completa (trae los
     * pasados y cuáles están grabados); si no, la corta con [UPCOMING_LIMIT] programas.
     * Devuelve null si no se pudo bajar (se queda lo que hubiera).
     */
    suspend fun load(channel: ChannelEntity): List<GuideProgram>? {
        cache.get(channel.id)?.takeIf { System.currentTimeMillis() - it.fetchedAt < FRESH_MS }?.let { return it.programs }
        return withContext(Dispatchers.IO) {
            val p = playlistDao.get(channel.playlistId) ?: return@withContext null
            val streamId = channel.streamKey.toIntOrNull()
            if (p.type != "XTREAM" || streamId == null) return@withContext null
            val now = System.currentTimeMillis() / 1000
            val api = PlaylistRepository.apiUrl(p.url)
            val response = try {
                requestSlots.withPermit {
                    if (channel.archiveDays > 0) xtreamApi.simpleDataTable(api, p.username, p.password, streamId)
                    else xtreamApi.shortEpg(api, p.username, p.password, streamId, limit = UPCOMING_LIMIT)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext null
            }
            val oldest = if (channel.archiveDays > 0) now - channel.archiveDays * DAY_S else now - 6 * HOUR_S
            val newest = now + 2 * DAY_S
            val programs = response.listings.asSequence()
                .filter { it.stopTimestamp > it.startTimestamp && it.stopTimestamp > oldest && it.startTimestamp < newest }
                .mapNotNull { it.toProgram(channel, now) }
                .sortedBy { it.start }
                .distinctBy { it.start }
                .toList()
            cache.put(channel.id, Entry(programs, System.currentTimeMillis()))
            val keep = programs.filter { it.end > now - 6 * HOUR_S }
            if (keep.isNotEmpty()) {
                runCatching {
                    epgDao.upsertAll(keep.map { EpgEntity(channel.epgKey, it.title, it.description, it.start, it.end) })
                }
            }
            programs
        }
    }

    private fun XtreamEpgResponse.Listing.toProgram(channel: ChannelEntity, now: Long): GuideProgram? {
        val title = titleBase64.decodeBase64()?.takeIf { it.isNotBlank() } ?: return null
        val inWindow = channel.archiveDays > 0 && stopTimestamp <= now && startTimestamp >= now - channel.archiveDays * DAY_S
        // has_archive manda cuando viene; si no, se supone grabado lo que entra en los días del canal.
        val archived = if (hasArchive != null) inWindow && hasArchive.asField() == "1" else inWindow
        return GuideProgram(title, descriptionBase64.decodeBase64().orEmpty(), startTimestamp, stopTimestamp, archived)
    }

    /**
     * Pide al servidor solo los canales de [groupTitle] para saber cuáles tienen catch-up (el
     * catálogo guardado puede ser de antes). Como mucho cada [ARCHIVE_CHECK_MS] por categoría.
     */
    suspend fun refreshArchiveFlags(playlistId: String, groupTitle: String) {
        withContext(Dispatchers.IO) {
            val key = "$playlistId|$groupTitle"
            val p = playlistDao.get(playlistId) ?: return@withContext
            if (p.type != "XTREAM") return@withContext
            metaLock.withLock {
                val last = archiveCheckedAt[key] ?: 0L
                if (System.currentTimeMillis() - last < ARCHIVE_CHECK_MS) return@withContext
                archiveCheckedAt[key] = System.currentTimeMillis()
            }
            runCatching {
                val categoryId = categoryId(p, groupTitle) ?: return@runCatching
                val streams = requestSlots.withPermit {
                    xtreamApi.liveStreamsIn(PlaylistRepository.apiUrl(p.url), p.username, p.password, categoryId)
                }
                streams.forEach { s -> channelDao.setArchiveDays("$playlistId:${s.streamId}", s.archiveDays) }
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private suspend fun categoryId(p: PlaylistEntity, groupTitle: String): String? {
        val known = metaLock.withLock { categoryIds[p.id] }
        val map = known ?: requestSlots.withPermit {
            xtreamApi.liveCategories(PlaylistRepository.apiUrl(p.url), p.username, p.password)
        }.associate { it.categoryName to it.categoryId }.also { metaLock.withLock { categoryIds[p.id] = it } }
        // Sin nombres al actualizar, el canal queda con el id de la categoría como grupo.
        return map[groupTitle] ?: groupTitle.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    /**
     * Dirección del programa grabado (timeshift de Xtream). El servidor espera el inicio en su
     * hora local (server_info.timezone); si no la informa se usa la del equipo.
     */
    suspend fun catchupUrl(channel: ChannelEntity, start: Long, end: Long): String? = withContext(Dispatchers.IO) {
        val p = playlistDao.get(channel.playlistId) ?: return@withContext null
        val streamId = channel.streamKey.toIntOrNull()
        if (p.type != "XTREAM" || streamId == null || end <= start) return@withContext null
        val zone = serverZone(p) ?: TimeZone.getDefault()
        val format = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = zone }
        val minutes = ((end - start + 59) / 60).coerceAtLeast(1)
        val root = PlaylistRepository.serverRoot(p.url)
        "${root}timeshift/${p.username}/${p.password}/$minutes/${format.format(Date(start * 1000))}/$streamId.ts"
    }

    private suspend fun serverZone(p: PlaylistEntity): TimeZone? {
        metaLock.withLock { if (zones.containsKey(p.id)) return zones[p.id] }
        // Si el pedido falla no se anota: se vuelve a intentar con el próximo programa.
        val id = runCatching {
            val text = requestSlots.withPermit {
                xtreamApi.loginRaw(PlaylistRepository.apiUrl(p.url), p.username, p.password).string()
            }
            json.decodeFromString(XtreamLogin.serializer(), text).serverTimezone
        }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
        val zone = TimeZone.getTimeZone(id).takeIf { id.isNotEmpty() && it.id == id }
        metaLock.withLock { zones[p.id] = zone }
        return zone
    }

    private fun String.decodeBase64(): String? = runCatching {
        String(Base64.decode(replace("\n", "").replace("\r", ""), Base64.DEFAULT), Charsets.UTF_8).trim()
    }.getOrNull()

    companion object {
        const val HOUR_S = 3600L
        const val DAY_S = 24 * HOUR_S
        private const val UPCOMING_LIMIT = 30
        private const val FRESH_MS = 30 * 60_000L
        private const val ARCHIVE_CHECK_MS = 30 * 60_000L
    }
}

/** Clave de la guía en Room: la misma que usa el panel de En vivo. */
val ChannelEntity.epgKey: String get() = epgChannelId.ifEmpty { streamKey }
