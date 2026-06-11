package com.mipuble.domain.sort

import com.mipuble.domain.model.Book

enum class BookSortOption {
    TITLE_NATURAL,

    /**
     * Plain character-by-character order — the broken behavior most readers
     * ship with. Kept as a visible option so the difference is demonstrable.
     */
    TITLE_LEXICOGRAPHIC,
    AUTHOR,
    DATE_ADDED,

    /** The user's own drag-and-drop arrangement, persisted per book. */
    CUSTOM,
}

private val natural = NaturalOrderComparator()

fun BookSortOption.comparator(): Comparator<Book> = when (this) {
    BookSortOption.TITLE_NATURAL ->
        compareBy(natural) { it.title }

    BookSortOption.TITLE_LEXICOGRAPHIC ->
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }

    BookSortOption.AUTHOR ->
        compareBy<Book, String>(natural) { it.author }
            .then(compareBy(natural) { it.title })

    BookSortOption.DATE_ADDED ->
        compareByDescending { it.addedAtEpochMillis }

    BookSortOption.CUSTOM ->
        compareBy({ it.customOrder }, { it.id })
}
