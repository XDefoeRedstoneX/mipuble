package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CatalogRepository
import com.mipuble.domain.title.TitleNormalizer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Books whose catalog match was uncertain and need the user to confirm a name. */
class ObserveReviewQueueUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    operator fun invoke(): Flow<List<Book>> =
        repository.observeBooks().map { books -> books.filter { it.needsReview } }
}

/**
 * Confirms a reviewed book's official series name. Optionally teaches the
 * catalog the name (so future imports match it automatically) before applying.
 */
class ResolveReviewUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(bookId: Long, canonicalSeries: String, addToCatalog: Boolean) {
        if (addToCatalog) catalogRepository.addSeries(canonicalSeries)
        bookRepository.applyCanonicalName(bookId, canonicalSeries)
    }
}

/** Dismisses a review, keeping the book's current title. */
class DismissReviewUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(bookId: Long) = repository.dismissReview(bookId)
}

/** Free-text catalog lookup powering the review sheet's per-book search box. */
class SearchCatalogUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(query: String, limit: Int = 10): List<String> =
        repository.catalog().search(query, limit)
}

/**
 * Queues already-imported books for name review, recomputing each one's closest
 * catalog suggestions from its current title — so books added before naming (or
 * synced metadata-only from Drive) can be named too.
 */
class QueueBooksForReviewUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(bookIds: List<Long>) {
        if (bookIds.isEmpty()) return
        val catalog = catalogRepository.catalog()
        bookIds.forEach { id ->
            val book = bookRepository.getBook(id) ?: return@forEach
            val suggestions = TitleNormalizer.normalize(book.title, catalog).suggestions
            bookRepository.markForReview(id, suggestions)
        }
    }
}
