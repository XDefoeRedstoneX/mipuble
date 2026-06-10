package com.mipuble.data.local

/**
 * Demo library inserted on first launch so the app is never empty during
 * development. The 12-volume series exists specifically to demonstrate the
 * natural-sort fix: lexicographic order shelves it 1, 10, 11, 12, 2, 3...
 * Replaced by real parsed EPUBs in Phase 2.
 */
object SeedData {

    private const val DAY_MS = 86_400_000L
    private const val BASE_ADDED_AT = 1_765_000_000_000L

    val books: List<BookEntity> = buildList {
        repeat(12) { index ->
            add(
                BookEntity(
                    title = "The Glass Archivist, Vol. ${index + 1}",
                    author = "Mira Holt",
                    addedAt = BASE_ADDED_AT + index * DAY_MS,
                    progress = if (index < 3) 1f else if (index == 3) 0.4f else 0f,
                ),
            )
        }
        add(BookEntity(title = "A Memory Called Empire", author = "Arkady Martine", addedAt = BASE_ADDED_AT + 20 * DAY_MS, progress = 0.75f))
        add(BookEntity(title = "Piranesi", author = "Susanna Clarke", addedAt = BASE_ADDED_AT + 25 * DAY_MS, progress = 1f))
        add(BookEntity(title = "The Left Hand of Darkness", author = "Ursula K. Le Guin", addedAt = BASE_ADDED_AT + 30 * DAY_MS))
        add(BookEntity(title = "Project Hail Mary", author = "Andy Weir", addedAt = BASE_ADDED_AT + 35 * DAY_MS, progress = 0.1f))
    }
}
