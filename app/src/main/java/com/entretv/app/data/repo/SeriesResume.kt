package com.entretv.app.data.repo

import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.PlaybackProgressEntity

/** Qué episodio abre "Continuar" en una serie y por qué. */
data class SeriesResume(
    /** null solo si la serie todavía no tiene episodios guardados. */
    val episode: EpisodeEntity?,
    val mode: Mode,
    /** Progreso del episodio a retomar (solo [Mode.RESUME]). */
    val progress: PlaybackProgressEntity?,
    /** Episodio que dejó el usuario (el último visto de verdad). */
    val anchor: PlaybackProgressEntity?
) {
    enum class Mode { START, RESUME, NEXT, REWATCH }

    val fraction: Float get() = if (mode == Mode.RESUME) progress?.fraction ?: 0f else 0f
}

/**
 * Regla única de "Seguir viendo" para series (fila de Inicio, ficha y reproductor):
 * se toma el episodio más avanzado de la serie visto más de 3 minutos; si ya lo terminó
 * (95 % o más), sigue el próximo. Si nada pasó de 3 minutos, el último que abrió.
 */
object SeriesResumeRule {
    const val MIN_WATCH_MS = 3 * 60_000L

    private val codeRegex = Regex("T(\\d+):E(\\d+)")

    fun resolve(episodes: List<EpisodeEntity>, progress: List<PlaybackProgressEntity>): SeriesResume {
        val byId = episodes.withIndex().associateBy { it.value.id }
        fun rank(p: PlaybackProgressEntity): Long {
            byId[p.contentKey]?.let { return it.value.season * 100_000L + it.value.episodeNum }
            val m = codeRegex.find(p.subtitle) ?: return -1
            return m.groupValues[1].toLong() * 100_000L + m.groupValues[2].toLong()
        }
        val anchor = progress.filter { it.positionMs >= MIN_WATCH_MS || it.isCompleted }.maxByOrNull(::rank)
            ?: progress.maxByOrNull { it.updatedAt }
            ?: return SeriesResume(episodes.firstOrNull(), SeriesResume.Mode.START, null, null)
        val indexed = byId[anchor.contentKey]
        if (!anchor.isCompleted) {
            return SeriesResume(indexed?.value, SeriesResume.Mode.RESUME, anchor, anchor)
        }
        if (indexed == null) {
            // Episodios aún no guardados: el siguiente se busca por número.
            val r = rank(anchor)
            val next = episodes.firstOrNull { it.season * 100_000L + it.episodeNum > r }
            return SeriesResume(next, if (next != null || episodes.isEmpty()) SeriesResume.Mode.NEXT else SeriesResume.Mode.REWATCH, null, anchor)
        }
        val next = episodes.getOrNull(indexed.index + 1)
        return if (next != null) SeriesResume(next, SeriesResume.Mode.NEXT, null, anchor)
        else SeriesResume(episodes.first(), SeriesResume.Mode.REWATCH, null, anchor)
    }
}
