package com.entretv.app.ui

/**
 * Quita lo que los paneles Xtream le pegan a los nombres: prefijos de país o
 * idioma ("ES:", "US|", "|LAT|"), etiquetas de calidad ("[HD]", "FHD", "(4K)")
 * y símbolos decorativos. Si no queda nada legible devuelve el original.
 */
object TitleCleaner {

    /** Prefijos de 3 a 6 letras que se quitan seguidos de ":" o " - " (dos letras siempre). */
    private val KNOWN_PREFIXES = setOf(
        "LAT", "LATAM", "LATINO", "ESP", "SPA", "CAST", "ENG", "USA", "ARG", "MEX", "COL", "CHI",
        "PER", "VEN", "URU", "BRA", "POR", "FRA", "ITA", "GER", "DEU", "VIP", "FHD", "UHD", "HEVC",
        "4K", "8K", "MULTI", "SUB", "VOSE", "DUAL", "NF", "AMZ", "DSNP", "HMAX", "ATV", "24/7"
    )
    /** Dos letras que también son títulos ("IT: Capítulo 2", "UP: Una aventura de altura"). */
    private val TITLE_WORDS = setOf("IT", "UP")
    private const val QUALITY =
        "HD|FHD|UHD|SD|HQ|4K|8K|HEVC|H\\.?26[45]|X26[45]|1080P?|720P?|2160P?|60FPS|50FPS|MULTI|DUAL|HDR"

    private val decorations = Regex("^[\\s|┃│▎▌★☆•·*=~\\-–—:]+")
    private val leadingBracket = Regex("^[\\[(]([^\\])]{1,12})[\\])]\\s*[-:|]?\\s*")
    private val pipePrefix = Regex("^\\|?\\s*([A-Za-z0-9+/]{2,6})\\s*\\|\\s*")
    private val wordPrefix = Regex("^([A-Z0-9+/]{2,6})\\s*(?::|\\s[-–])\\s*")
    private val qualityTag = Regex("\\s*[\\[(](?:$QUALITY)[\\])]", RegexOption.IGNORE_CASE)
    private val squareTag = Regex("\\s*\\[(?!\\d{4}])[^\\]]{1,12}]")
    private val trailingQuality = Regex("(?:\\s+(?:$QUALITY))+$", RegexOption.IGNORE_CASE)
    private val trailingSeparators = Regex("[\\s|┃│\\-–—:]+$")
    private val spaces = Regex("\\s{2,}")
    private val year = Regex("\\d{4}")

    fun clean(raw: String): String {
        var text = raw.trim()
        var previous: String
        do {
            previous = text
            text = decorations.replace(text, "")
            leadingBracket.find(text)?.let { m ->
                if (!year.matches(m.groupValues[1].trim())) text = text.substring(m.range.last + 1)
            }
            pipePrefix.find(text)?.let { m -> text = text.substring(m.range.last + 1) }
            wordPrefix.find(text)?.let { m ->
                val token = m.groupValues[1]
                val isCountry = token.length == 2 && token.all { it.isLetter() } && token !in TITLE_WORDS
                if (isCountry || token in KNOWN_PREFIXES) text = text.substring(m.range.last + 1)
            }
        } while (text != previous && text.isNotEmpty())

        text = qualityTag.replace(text, "")
        text = squareTag.replace(text, "")
        text = trailingQuality.replace(text, "")
        text = trailingSeparators.replace(text, "")
        text = spaces.replace(text, " ").trim()
        return text.ifEmpty { raw.trim() }
    }

    fun clean(raw: String, enabled: Boolean): String = if (enabled) clean(raw) else raw
}
