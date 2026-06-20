package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CatalogRepository
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
