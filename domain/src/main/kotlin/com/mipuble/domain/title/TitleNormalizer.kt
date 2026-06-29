package com.mipuble.domain.title

/**
 * Turns a messy book name (filename or EPUB title) into a clean series + volume
 * and a canonical display title, plus a logical [dedupKey] so two releases of
 * the *same* book — even with different bytes or translators — collapse
 * together, while different volumes of a series stay distinct.
 *
 * Volume is a *string* so decimal/side volumes work ("Vol 1.5", "v1,5" → "1.5").
 * The [dedupKey] is `seriesKey|volume` for a numbered volume, or just the
 * `seriesKey` for a volume-less book — so two same-named copies (e.g. different
 * translations of a standalone) also collapse.
 *
 * When a [SeriesCatalog] of official names is supplied, the messy series is
 * snapped to the closest official one. A confident match is applied
 * automatically and *unlocks bare-number volume parsing* ("Spice and Wolf 4" →
 * vol 4) which is otherwise too risky ("Fahrenheit 451"); a weak match is
 * surfaced as a suggestion for the user to confirm.
 *
 * Examples:
 *   "xxx Vol 1 [Premium]{Translated}" -> series "xxx", vol "1",   "xxx, Vol. 1"
 *   "xxx v2 - rahhh"                  -> series "xxx", vol "2",   "xxx, Vol. 2"
 *   "Re:Zero v05 (Yen Press)"         -> series "Re:Zero", vol "5"
 *   "Spice and Wolf Vol 1.5"          -> series "Spice and Wolf", vol "1.5"
 *   "Piranesi"                        -> series "Piranesi", no volume, key "piranesi"
 */
object TitleNormalizer {

    /** How sure we are about a catalog match, and thus how the UI should treat it. */
    enum class MatchConfidence {
        /** No catalog consulted (or it was empty): plain cleanup, today's behavior. */
        NONE,

        /** Strong match — pre-selected as the best guess in the review sheet. */
        AUTO,

        /** Weaker match — pre-selected too, but with closer scrutiny expected. */
        REVIEW,
    }

    data class Normalized(
        val series: String,
        /** Canonical volume token ("1", "1.5"), or null for a volume-less book. */
        val volume: String?,
        val displayTitle: String,
        /** series(+volume) identity; null only when the series collapses to nothing. */
        val dedupKey: String?,
        val confidence: MatchConfidence = MatchConfidence.NONE,
        /** Closest official names (best first); empty only when no catalog was consulted. */
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
        /** Below this score a candidate is too weak to suggest — don't pre-select it. */
        val suggestionFloor: Float = 0.5f,
    )

    // Bracketed source/quality tags: [..], {..}, (..).
    private val TAGS = Regex("""[\[{(][^\]})]*[\]})]""")
    private val WHITESPACE = Regex("""\s+""")

    // A volume number: integer with an optional decimal part using '.' or ',' .
    private const val NUM = """\d{1,4}(?:[.,]\d{1,2})?"""
    private val VOLUME_TOKEN = Regex("""(\d{1,4})(?:[.,](\d{1,2}))?""")

    // Explicit volume markers (allow leading zeros). A bare trailing number is
    // only treated as a volume once a catalog match confirms the series.
    private val VOLUME_PATTERNS = listOf(
        Regex("""\bvolume\s*0*($NUM)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bvol\.?\s*0*($NUM)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bv\s*0*($NUM)\b""", RegexOption.IGNORE_CASE),
        Regex("""#\s*0*($NUM)\b"""),
    )

    // A number at the very end, optionally after a separator: "Series 4", "Series - 04".
    private val BARE_TRAILING = Regex("""[\s\-_:#]*0*($NUM)\s*$""")

    /** Normalizes a raw volume token to canonical form: "01"→"1", "1,5"→"1.5", "1.0"→"1". */
    fun canonicalVolume(raw: String): String? {
        val m = VOLUME_TOKEN.find(raw) ?: return null
        val intPart = m.groupValues[1].trimStart('0').ifEmpty { "0" }
        val frac = m.groupValues[2].trimEnd('0')
        return if (frac.isEmpty()) intPart else "$intPart.$frac"
    }

    /** Strips the `.epub` suffix and bracketed tags, then collapses whitespace. */
    private fun preclean(raw: String): String =
        WHITESPACE.replace(
            TAGS.replace(raw.trim().removeSuffix(".epub").trim(), " "),
            " ",
        ).trim()

    /** First explicit volume marker (volume/vol/v/#) in [stripped], if any. */
    private fun explicitVolume(stripped: String): Pair<String?, String> {
        for (pattern in VOLUME_PATTERNS) {
            val match = pattern.find(stripped) ?: continue
            val before = stripped.substring(0, match.range.first)
            val seriesPart = if (before.isNotBlank()) before else stripped.removeRange(match.range)
            return canonicalVolume(match.groupValues[1]) to seriesPart
        }
        return null to stripped
    }

    fun normalize(raw: String, catalog: SeriesCatalog? = null, thresholds: Thresholds = Thresholds()): Normalized {
        val stripped = preclean(raw)
        val (parsedVolume, seriesPart) = explicitVolume(stripped)
        var volume = parsedVolume
        val cleanedSeries = cleanup(seriesPart).ifBlank { cleanup(stripped) }.ifBlank { raw.trim() }

        // Without a catalog, behave exactly as before.
        if (catalog == null || catalog.size == 0) {
            return result(cleanedSeries, volume, MatchConfidence.NONE, emptyList())
        }

        val ranked = catalog.ranked(cleanedSeries, thresholds.suggestionCount)
        val best = ranked.firstOrNull()
            ?: return result(cleanedSeries, volume, MatchConfidence.NONE, emptyList())
        val runnerUp = ranked.getOrNull(1)?.score ?: 0f
        // Only surface candidates strong enough to be worth a click — a wall of
        // unrelated names (for a true standalone) would otherwise be pre-selected.
        val suggestions = ranked.filter { it.score >= thresholds.suggestionFloor }.map { it.canonical }

        return if (best.score >= thresholds.auto && best.score - runnerUp >= thresholds.margin) {
            // Confident: adopt the official name as the pre-selected guess, and a
            // leftover number is now safe to read as a volume — unless the number
            // is part of the official name itself (e.g. "Mob Psycho 100").
            if (volume == null) volume = bareVolume(stripped, best.canonical)
            result(best.canonical, volume, MatchConfidence.AUTO, suggestions)
        } else {
            // Uncertain: keep the cleaned original as the fallback title, but hand
            // the UI the closest official names (if any are close) to confirm.
            result(cleanedSeries, volume, MatchConfidence.REVIEW, suggestions)
        }
    }

    /**
     * The volume in [rawTitle] once its series is known/confirmed — explicit
     * markers, plus a bare trailing number (safe now that [canonicalSeries] is
     * settled). Used when the user confirms a name so a bare-number volume isn't lost.
     */
    fun volumeForConfirmed(rawTitle: String, canonicalSeries: String): String? {
        val stripped = preclean(rawTitle)
        return explicitVolume(stripped).first ?: bareVolume(stripped, canonicalSeries)
    }

    /** Pulls a trailing bare number as a volume, ignoring numbers that belong to the series name. */
    private fun bareVolume(stripped: String, canonical: String): String? {
        val match = BARE_TRAILING.find(stripped) ?: return null
        val v = canonicalVolume(match.groupValues[1]) ?: return null
        val remainder = stripped.removeRange(match.range).trim()
        if (remainder.isBlank()) return null // the whole title is the number
        // Skip only when the number is part of the official name as a *standalone*
        // number ("Mob Psycho 100"), not merely a digit inside one ("86" → "6").
        val standalone = Regex("(?<!\\d)${Regex.escape(v)}(?!\\d)")
        if (standalone.containsMatchIn(canonical)) return null
        return v
    }

    /** The canonical display title for a confirmed series + optional volume. */
    fun displayTitleFor(series: String, volume: String?): String =
        if (volume != null) "$series, Vol. $volume" else series

    /**
     * The dedup identity: `seriesKey|volume` for a numbered volume, or just the
     * `seriesKey` for a volume-less book (so same-named copies collapse). Null
     * only when the series has no letters/digits at all.
     */
    fun dedupKeyFor(series: String, volume: String?): String? {
        val k = key(series)
        if (k.isEmpty()) return null
        return if (volume != null) "$k|$volume" else k
    }

    private fun result(
        series: String,
        volume: String?,
        confidence: MatchConfidence,
        suggestions: List<String>,
    ): Normalized = Normalized(
        series = series,
        volume = volume,
        displayTitle = displayTitleFor(series, volume),
        dedupKey = dedupKeyFor(series, volume),
        confidence = confidence,
        suggestions = suggestions,
    )

    private fun cleanup(value: String): String =
        WHITESPACE.replace(value.trim().trim('-', '~', ':', ',', '.', ' '), " ").trim()

    /**
     * Collapses a series name to a stable matching key — case-, punctuation- and
     * space-insensitive, so "Re:Zero" and "re zero" hash to the same identity.
     * Shares [StringSimilarity.collapse] so the dedup key and catalog matching
     * canonicalize identically.
     */
    private fun key(series: String): String = StringSimilarity.collapse(series)
}
