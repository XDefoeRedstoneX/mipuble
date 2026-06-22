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
        /** [significant] tokens concatenated, for the merged-word character ratio. */
        val significantCollapsed: String,
    )

    private val entries: List<Entry> = names
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // Dedupe case-insensitively, keeping the first spelling seen.
        .distinctBy { it.lowercase() }
        .map {
            val tokens = StringSimilarity.tokenize(it)
            val significant = tokens.filter { t -> t.length >= 3 }
            Entry(
                canonical = it,
                tokens = tokens,
                collapsed = StringSimilarity.collapse(it),
                significant = significant,
                // The collapsed form with stopwords dropped, so "Spice and Wolf"
                // ("spicewolf") matches the merged filename "SpiceWolf" exactly.
                significantCollapsed = significant.joinToString(""),
            )
        }

    val size: Int get() = entries.size

    /** Best canonical match for [query], or null when the catalog is empty. */
    fun match(query: String): Match? = ranked(query, limit = 1).firstOrNull()

    /**
     * Free-text lookup for the review sheet's search box: names that *contain*
     * the query (shortest first, so the tightest title wins) topped up with the
     * closest fuzzy matches. Empty query yields nothing.
     */
    fun search(query: String, limit: Int): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val contains = entries
            .filter { it.canonical.lowercase().contains(q) }
            .sortedWith(compareBy({ it.canonical.length }, { it.canonical }))
            .map { it.canonical }
        if (contains.size >= limit) return contains.take(limit)
        // Top up with fuzzy matches, but only relevant ones — never pad the list
        // with unrelated titles just to reach the limit. Rank enough to refill
        // after dropping the substring hits we already have.
        val containsSet = contains.toHashSet()
        val fuzzy = ranked(query, limit + contains.size)
            .filter { it.score >= 0.5f && it.canonical !in containsSet }
            .map { it.canonical }
        return (contains + fuzzy).take(limit)
    }

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
                // Merged-word case ("spicewolf" -> "Spice and Wolf"): compare the
                // query to the official name with stopwords removed. Using a whole-
                // string ratio (not per-word substring) avoids matching a word that
                // merely sits *inside* another ("air" within "repair"). Restricted
                // to multi-word names so a single word can't hijack the match.
                val mergedScore = if (entry.significant.size >= 2) {
                    StringSimilarity.ratio(qCollapsed, entry.significantCollapsed)
                } else {
                    0f
                }
                val dice = StringSimilarity.diceCoefficient(qTokens, entry.tokens)
                Scored(entry, maxOf(tokenScore, charScore, mergedScore), dice)
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
