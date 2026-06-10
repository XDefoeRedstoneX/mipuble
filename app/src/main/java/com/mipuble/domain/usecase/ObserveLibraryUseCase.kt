package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.sort.comparator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The library stream the UI subscribes to: all books, sorted per the user's
 * current choice. Sorting lives here (not in SQL) because natural ordering
 * is a domain rule SQLite can't express.
 */
class ObserveLibraryUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    operator fun invoke(sortOption: BookSortOption): Flow<List<Book>> =
        repository.observeBooks().map { books ->
            books.sortedWith(sortOption.comparator())
        }
}
