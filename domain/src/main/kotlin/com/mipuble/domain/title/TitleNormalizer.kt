package com.mipuble.domain.title

/**
 * Turns a messy book name (filename or EPUB title) into a clean series + volume
 * and a canonical display title, plus a logical [dedupKey] so two releases of
 * the *same* volume — even with different bytes — collapse together, while
 * different volumes of a series stay distinct.
 *
 * When a [SeriesCatalog] of official names is supplied, the messy series is
 * snapped to the closest official one. A confident match is applied
 * automatically and *unlocks bare-number volume parsing* ("Spice and Wolf 4" →
 * vol 4) which is otherwise too risky ("Fahrenheit 451"); a weak match is
 * surfaced as a suggestion for the user to confirm.
 *
 * Examples:
 *   "xxx Vol 1 [Premium]{Translated}" -> series "xxx", vol 1, "xxx, Vol. 1"
 *   "xxx v2 - rahhh"                  -> series "xxx", vol 2, "xxx, Vol. 2"
 *   "Re:Zero v05 (Yen Press)"         -> series "Re:Zero", vol 5
 *   "Piranesi"                        -> series "Piranesi", no volume, no key
 */
object TitleNormalizer {

    /** How sure we are about a catalog match, and thus how the UI should treat it. */
    enum class MatchConfidence {
        /** No catalog consulted (or it was empty): plain cleanup, today's behavior. */
        NONE,

        /** Strong, unambiguous match — applied automatically without asking. */
        AUTO,

        /** Uncertain — show [Normalized.suggestions] and let the user confirm/add. */
        REVIEW,
    }

    data class Normalized(
        val series: String,
        val volume: Int?,
        val displayTitle: String,
        /** series+volume identity, or null for volume-less standalone books. */
        val dedupKey: String?,
        val confidence: MatchConfidence = MatchConfidence.NONE,
        /** Closest official names (best first) when [confidence] is REVIEW. */
        val suggestions: List<String> = emptyList(),
    )

    /** Tunable cut-offs for turning match scores into a confidence. */
    data class Thresholds(
        /** At/above this score (and clearing [margin]) a match is applied silently. */
        val auto: Float = 0.82f,
        /** The winner must beat the runner-up by this much to auto-apply. */
        val margin: Float = 0.05f,
        /** How many suggestions to surface for review. */
        val suggestionCount: Int = 3,
    )

    // Bracketed source/quality tags: [..], {..}, (..).
    private val TAGS = Regex("""[\[{(][^\]})]*[\]})]""")
    private val WHITESPACE = Regex("""\s+""")

    // Explicit volume markers (allow leading zeros). A bare trailing number is
    // only treated as a volume once a catalog match confirms the series.
    private val VOLUME_PATTERNS = listOf(
        Regex("""\bvolume\s*0*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bvol\.?\s*0*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bv\s*0*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""#\s*0*(\d{1,4})\b"""),
    )

    // A number at the very end, optionally after a separator: "Series 4", "Series - 04".
    private val BARE_TRAILING = Regex("""[\s\-_:#]*0*(\d{1,4})\s*$""")

    fun normalize(raw: String, catalog: SeriesCatalog? = null, thresholds: Thresholds = Thresholds()): Normalized {
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

        val cleanedSeries = cleanup(seriesPart).ifBlank { cleanup(stripped) }.ifBlank { raw.trim() }

        // Without a catalog, behave exactly as before.
        if (catalog == null || catalog.size == 0) {
            return result(cleanedSeries, volume, MatchConfidence.NONE, emptyList())
        }

        val ranked = catalog.ranked(cleanedSeries, thresholds.suggestionCount)
        val best = ranked.firstOrNull()
            ?: return result(cleanedSeries, volume, MatchConfidence.NONE, emptyList())
        val runnerUp = ranked.getOrNull(1)?.score ?: 0f

        return if (best.score >= thresholds.auto && best.score - runnerUp >= thresholds.margin) {
            // Confident: adopt the official name, and now a leftover number is
            // safe to read as a volume — unless the number is part of the
            // official name itself (e.g. "Mob Psycho 100").
            if (volume == null) volume = bareVolume(stripped, best.canonical)
            result(best.canonical, volume, MatchConfidence.AUTO, emptyList())
        } else {
            // Uncertain: keep the cleaned original as the fallback title, but
            // hand the UI the closest official names to confirm or add.
            result(cleanedSeries, volume, MatchConfidence.REVIEW, ranked.map { it.canonical })
        }
    }

    /** Pulls a trailing bare number as a volume, ignoring numbers that belong to the series name. */
    private fun bareVolume(stripped: String, canonical: String): Int? {
        val match = BARE_TRAILING.find(stripped) ?: return null
        val n = match.groupValues[1].toIntOrNull() ?: return null
        val remainder = stripped.removeRange(match.range).trim()
        if (remainder.isBlank()) return null            // the whole title is the number
        if (canonical.contains(n.toString())) return null // number is part of the official name
        return n
    }

    private fun result(
        series: String,
        volume: Int?,
        confidence: MatchConfidence,
        suggestions: List<String>,
    ): Normalized {
        val displayTitle = if (volume != null) "$series, Vol. $volume" else series
        val dedupKey = volume?.let { "${key(series)}|$it" }
        return Normalized(series, volume, displayTitle, dedupKey, confidence, suggestions)
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
