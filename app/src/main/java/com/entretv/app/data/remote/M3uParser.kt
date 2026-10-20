package com.entretv.app.data.remote

import com.entretv.app.data.local.ChannelEntity

/**
 * Parser M3U/M3U8 optimizado: procesa en una sola pasada sin cargar
 * todo el archivo a memoria de golpe y tolera atributos mal formados.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9\-_]+)="([^"]*)"""")

    fun parse(playlistId: String, content: String): List<ChannelEntity> {
        val channels = ArrayList<ChannelEntity>(1024)
        var name = ""
        var attrs: Map<String, String> = emptyMap()

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val commaIdx = line.indexOf(',')
                    name = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
                    attrs = if (commaIdx >= 0) {
                        ATTR_REGEX.findAll(line.substring(0, commaIdx))
                            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    } else emptyMap()
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (attrs.isNotEmpty() || name.isNotEmpty()) {
                        val key = channels.size.toString()
                        channels += ChannelEntity(
                            id = "$playlistId:$key",
                            playlistId = playlistId,
                            streamKey = key,
                            name = name.ifEmpty { "Canal ${channels.size + 1}" },
                            logoUrl = attrs["tvg-logo"].orEmpty(),
                            groupTitle = attrs["group-title"].orEmpty(),
                            streamUrl = line,
                            epgChannelId = attrs["tvg-id"].orEmpty(),
                            // Sin número propio, el orden en que vienen en la lista.
                            number = attrs["tvg-chno"]?.toIntOrNull() ?: (channels.size + 1)
                        )
                    }
                    name = ""
                    attrs = emptyMap()
                }
            }
        }
        return channels
    }
}
