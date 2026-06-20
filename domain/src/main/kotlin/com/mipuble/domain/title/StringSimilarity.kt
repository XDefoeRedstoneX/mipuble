package com.mipuble.domain.title

/**
 * Pure text-similarity helpers used to snap a messy book title to a canonical
 * series name. Kept dependency-free (and in `:domain`) so the matching rules
 * are unit-tested without Android.
 *
 * Two complementary signals are combined by the caller:
 *  - token coverage — how many of the query's words appear in a candidate, which
 *    catches abbreviations ("Re Zero" ⊂ "Re:Zero - Starting Life…");
 *  - a collapsed character ratio — edit distance over the spaceless lowercased
 *    forms, which catches merged-word filenames ("SpiceWolf" ≈ "Spice and Wolf").
 */
object StringSimilarity {

    // Tiny, language-light stop list: words that carry almost no identifying
    // signal for a series and would otherwise inflate coverage.
    private val STOPWORDS = setOf("the", "a", "an", "of", "and", "to", "in", "no")
    private val NON_ALNUM = Regex("""[^\p{L}\p{Nd}]+""")

    /** Lowercased, punctuation-free word tokens, with stopwords and 1-char noise dropped. */
    fun tokenize(value: String): Set<String> =
        NON_ALNUM.split(value.lowercase())
            .filter { it.isNotBlank() }
            .filter { it.length > 1 && it !in STOPWORDS }
            .toSet()

    /** Lowercased letters-and-digits only, no separators — for the merged-word signal. */
    fun collapse(value: String): String =
        buildString {
            for (c in value.lowercase()) if (c.isLetterOrDigit()) append(c)
        }

    /**
     * Fraction of [query] tokens that also appear in [candidate] (0f..1f). High
     * when the query is an abbreviation or subset of a longer official name.
     */
    fun tokenCoverage(query: Set<String>, candidate: Set<String>): Float {
        if (query.isEmpty()) return 0f
        val hits = query.count { it in candidate }
        return hits.toFloat() / query.size
    }

    /** Dice coefficient over two token sets (0f..1f); penalizes wildly different lengths. */
    fun diceCoefficient(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val inter = a.count { it in b }
        return (2f * inter) / (a.size + b.size)
    }

    /** Normalized similarity of two strings via Levenshtein: 1f identical, 0f nothing in common. */
    fun ratio(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / longest
    }

    /** Classic two-row Levenshtein edit distance. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // insertion
                    prev[j] + 1,          // deletion
                    prev[j - 1] + cost,   // substitution
                )
            }
            val swap = prev; prev = curr; curr = swap
        }
        return prev[b.length]
    }
}
