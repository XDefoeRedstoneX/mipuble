package com.mipuble.domain.title

/**
 * Turns a messy book name (filename or EPUB title) into a clean series + volume
 * and a canonical display title, plus a logical [dedupKey] so two releases of
 * the *same* volume — even with different bytes — collapse together, while
 * different volumes of a series stay distinct.
 *
 * Examples:
 *   "xxx Vol 1 [Premium]{Translated}" -> series "xxx", vol 1, "xxx, Vol. 1"
 *   "xxx v2 - rahhh"                  -> series "xxx", vol 2, "xxx, Vol. 2"
 *   "Re:Zero v05 (Yen Press)"         -> series "Re:Zero", vol 5
 *   "Piranesi"                        -> series "Piranesi", no volume, no key
 */
object TitleNormalizer {

    data class Normalized(
        val series: String,
        val volume: Int?,
        val displayTitle: String,
        /** series+volume identity, or null for volume-less standalone books. */
        val dedupKey: String?,
    )

    // Bracketed source/quality tags: [..], {..}, (..).
    private val TAGS = Regex("""[\[{(][^\]})]*[\]})]""")
    private val WHITESPACE = Regex("""\s+""")

    // Explicit volume markers (allow leading zeros). Trailing bare number is a
    // last resort capped at 3 digits so it won't grab a 4-digit year.
    private val VOLUME_PATTERNS = listOf(
        Regex("""\bvolume\s*0*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bvol\.?\s*0*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bv\s*0*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""#\s*0*(\d{1,4})\b"""),
        Regex("""\b0*(\d{1,3})\s*$"""),
    )

    fun normalize(raw: String): Normalized {
        val stripped = WHITESPACE.replace(
            TAGS.replace(raw.trim().removeSuffix(".epub").trim(), " "),
            " ",
        ).trim()

        var volume: Int? = null
        var seriesPart = stripped
        for (pattern in VOLUME_PATTERNS) {
            val match = pattern.find(stripped) ?: continue
            volume = match.groupValues[1].toIntOrNull()
            // The series is whatever precedes the volume marker; fall back to
            // the remainder if the marker is at the very start.
            val before = stripped.substring(0, match.range.first)
            seriesPart = if (before.isNotBlank()) before else stripped.removeRange(match.range)
            break
        }

        val series = cleanup(seriesPart).ifBlank { cleanup(stripped) }.ifBlank { raw.trim() }
        val displayTitle = if (volume != null) "$series, Vol. $volume" else series
        val dedupKey = volume?.let { "${key(series)}|$it" }

        return Normalized(series = series, volume = volume, displayTitle = displayTitle, dedupKey = dedupKey)
    }

    private fun cleanup(value: String): String =
        WHITESPACE.replace(value.trim().trim('-', '~', ':', ',', '.', ' '), " ").trim()

    /**
     * Collapses a series name to a stable matching key — case-, punctuation- and
     * space-insensitive, so "Re:Zero" and "re zero" hash to the same identity.
     */
    private fun key(series: String): String =
        series.lowercase().filter { it.isLetterOrDigit() }
}
