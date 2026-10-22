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
            if (text.isNotEmpty() && (text[0].isWhitespace() || text[0] in DECORATIONS)) text = decorations.replace(text, "")
            if (text.startsWith('[') || text.startsWith('(')) leadingBracket.find(text)?.let { m ->
                if (!year.matches(m.groupValues[1].trim())) text = text.substring(m.range.last + 1)
            }
            if (text.indexOf('|') >= 0) pipePrefix.find(text)?.let { m -> text = text.substring(m.range.last + 1) }
            if (text.indexOf(':') >= 0 || text.indexOf('-') >= 0 || text.indexOf('–') >= 0) wordPrefix.find(text)?.let { m ->
                val token = m.groupValues[1]
                val isCountry = token.length == 2 && token.all { it.isLetter() } && token !in TITLE_WORDS
                if (isCountry || token in KNOWN_PREFIXES) text = text.substring(m.range.last + 1)
            }
        } while (text != previous && text.isNotEmpty())

        // Cada expresión corre solo si el texto puede coincidir: son miles de títulos por sección.
        if (text.indexOf('[') >= 0 || text.indexOf('(') >= 0) text = qualityTag.replace(text, "")
        if (text.indexOf('[') >= 0) text = squareTag.replace(text, "")
        if (text.isNotEmpty() && text.last().isLetterOrDigit() && text.any { it.isWhitespace() }) {
            text = trailingQuality.replace(text, "")
        }
        if (text.isNotEmpty() && (text.last().isWhitespace() || text.last() in SEPARATORS)) {
            text = trailingSeparators.replace(text, "")
        }
        if (hasDoubleSpace(text)) text = spaces.replace(text, " ")
        text = text.trim()
        return text.ifEmpty { raw.trim() }
    }

    private const val SEPARATORS = "|┃│-–—:"
    private const val DECORATIONS = "|┃│▎▌★☆•·*=~-–—:"

    private fun hasDoubleSpace(text: String): Boolean {
        for (i in 1 until text.length) if (text[i].isWhitespace() && text[i - 1].isWhitespace()) return true
        return false
    }

    fun clean(raw: String, enabled: Boolean): String = if (enabled) clean(raw) else raw
}
