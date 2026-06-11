package com.mipuble.data.local

/**
 * Demo library inserted on first launch so the app is never empty during
 * development. The 12-volume series exists specifically to demonstrate the
 * natural-sort fix: lexicographic order shelves it 1, 10, 11, 12, 2, 3...
 * Replaced by real parsed EPUBs in Phase 2+.
 */
object SeedData {

    private const val DAY_MS = 86_400_000L
    private const val BASE_ADDED_AT = 1_765_000_000_000L

    /** Demo shelves; colors are packed ARGB. */
    val categories = listOf(
        CategoryEntity(name = "Reading now", colorArgb = 0xFF00897B.toInt()), // teal
        CategoryEntity(name = "Finished", colorArgb = 0xFF7B1FA2.toInt()), // purple
    )

    /**
     * Builds the demo books, wiring category assignments to the ids returned
     * by inserting [categories] (same order).
     */
    fun books(categoryIds: List<Long>): List<BookEntity> {
        val readingNow = categoryIds.getOrNull(0)
        val finished = categoryIds.getOrNull(1)

        return buildList {
            repeat(12) { index ->
                add(
                    BookEntity(
                        title = "The Glass Archivist, Vol. ${index + 1}",
                        author = "Mira Holt",
                        addedAt = BASE_ADDED_AT + index * DAY_MS,
                        progress = if (index < 3) 1f else if (index == 3) 0.4f else 0f,
                        categoryId = when {
                            index < 3 -> finished
                            index == 3 -> readingNow
                            else -> null
                        },
                        customOrder = index.toLong(),
                    ),
                )
            }
            add(BookEntity(title = "A Memory Called Empire", author = "Arkady Martine", addedAt = BASE_ADDED_AT + 20 * DAY_MS, progress = 0.75f, categoryId = readingNow, customOrder = 12))
            add(BookEntity(title = "Piranesi", author = "Susanna Clarke", addedAt = BASE_ADDED_AT + 25 * DAY_MS, progress = 1f, categoryId = finished, customOrder = 13))
            add(BookEntity(title = "The Left Hand of Darkness", author = "Ursula K. Le Guin", addedAt = BASE_ADDED_AT + 30 * DAY_MS, customOrder = 14))
            add(BookEntity(title = "Project Hail Mary", author = "Andy Weir", addedAt = BASE_ADDED_AT + 35 * DAY_MS, progress = 0.1f, customOrder = 15))
        }
    }
}
