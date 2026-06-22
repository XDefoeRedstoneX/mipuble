package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.sort.NaturalOrderComparator
import com.mipuble.domain.sort.comparator
import com.mipuble.domain.title.StringSimilarity
import com.mipuble.domain.title.TitleNormalizer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The library stream the UI subscribes to: books (optionally filtered to one
 * category), sorted per the user's current choice. Sorting lives here (not in
 * SQL) because natural ordering is a domain rule SQLite can't express.
 */
class ObserveLibraryUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    operator fun invoke(
        sortOption: BookSortOption,
        categoryId: Long? = null,
    ): Flow<List<Book>> =
        repository.observeBooks().map { books ->
            val filtered = categoryId?.let { id -> books.filter { it.categoryId == id } } ?: books
            when (sortOption) {
                BookSortOption.AUTHOR -> filtered.sortedBySeriesAuthor()
                else -> filtered.sortedWith(sortOption.comparator())
            }
        }

    /**
     * Author sort, but series-aware. Volumes of one series often carry
     * inconsistent author metadata — some tagged, some "Unknown" — which would
     * otherwise scatter the series across the shelf. We pick a single canonical
     * author per series (the first real, non-blank/non-"Unknown" one) and sort
     * every volume of that series by it, so the series stays together.
     */
    private fun List<Book>.sortedBySeriesAuthor(): List<Book> {
        val natural = NaturalOrderComparator()
        // Collapse the series to a punctuation/space-insensitive key so volumes
        // titled slightly differently ("Re:Zero" vs "Re Zero") still group — the
        // same canonicalization dedup uses.
        val seriesKey = { book: Book -> StringSimilarity.collapse(TitleNormalizer.normalize(book.title).series) }

        val canonicalAuthor: Map<String, String> = this
            .groupBy(seriesKey)
            .mapValues { (_, books) ->
                books.map { it.author }
                    .firstOrNull { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                    ?: "Unknown" // stable sentinel so blank and "Unknown" don't sort differently
            }

        val byAuthor = compareBy<Book, String>(natural) { canonicalAuthor[seriesKey(it)] ?: it.author }
        return sortedWith(byAuthor.then(compareBy(natural) { it.title }))
    }
}
