package com.mipuble.domain.title

/**
 * An in-memory set of canonical series names the importer snaps messy titles to.
 * Built from the bundled catalog plus the user's writable overlay (see
 * `CatalogRepository`). Matching is pure and order-independent so it's fully
 * unit-tested.
 *
 * Scoring blends two signals (see [StringSimilarity]): token coverage for
 * abbreviations/subsets and a collapsed character ratio for merged-word
 * filenames. The best of the two is the entry's score.
 */
class SeriesCatalog(names: Iterable<String>) {

    data class Match(val canonical: String, val score: Float)

    private class Entry(
        val canonical: String,
        val tokens: Set<String>,
        val collapsed: String,
        /** Tokens substantial enough to anchor a merged-word match. */
        val significant: List<String>,
    )

    private val entries: List<Entry> = names
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // Dedupe case-insensitively, keeping the first spelling seen.
        .distinctBy { it.lowercase() }
        .map {
            val tokens = StringSimilarity.tokenize(it)
            Entry(it, tokens, StringSimilarity.collapse(it), tokens.filter { t -> t.length >= 3 })
        }

    val size: Int get() = entries.size

    /** Best canonical match for [query], or null when the catalog is empty. */
    fun match(query: String): Match? = ranked(query, limit = 1).firstOrNull()

    /**
     * Top [limit] candidates for [query], best first — used both for the
     * confident auto-rename (top 1) and to populate the review sheet's
     * suggestions. Deterministic: ties break by tightness, then length, then name.
     */
    fun ranked(query: String, limit: Int): List<Match> {
        if (entries.isEmpty()) return emptyList()
        val qTokens = StringSimilarity.tokenize(query)
        val qCollapsed = StringSimilarity.collapse(query)
        if (qCollapsed.isEmpty()) return emptyList()

        return entries
            .map { entry ->
                val coverage = StringSimilarity.tokenCoverage(qTokens, entry.tokens)
                // Only let coverage count when at least one *substantial* word
                // matched, so a stray short token can't carry a false match.
                val significant = qTokens.any { it.length >= 3 && it in entry.tokens }
                val tokenScore = if (significant) coverage else 0f
                val charScore = StringSimilarity.ratio(qCollapsed, entry.collapsed)
                // Merged-word case ("spicewolf" -> "Spice and Wolf"): every
                // substantial official word appears in the collapsed query.
                // Restricted to multi-word names so a single common word can't
                // hijack the match ("Berserker's Tale" must not become "Berserk").
                val mergedExact = entry.significant.size >= 2 &&
                    entry.significant.all { qCollapsed.contains(it) }
                val dice = StringSimilarity.diceCoefficient(qTokens, entry.tokens)
                Scored(entry, maxOf(tokenScore, charScore, if (mergedExact) 1f else 0f), dice)
            }
            .sortedWith(
                compareByDescending<Scored> { it.score }
                    .thenByDescending { it.dice }
                    .thenBy { it.entry.canonical.length }
                    .thenBy { it.entry.canonical },
            )
            .take(limit)
            .map { Match(it.entry.canonical, it.score) }
    }

    private class Scored(val entry: Entry, val score: Float, val dice: Float)
}
