package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CatalogRepository
import com.mipuble.domain.repository.CategoryRepository
import com.mipuble.domain.repository.ReaderPreferencesRepository
import com.mipuble.domain.title.SeriesShelfColor
import com.mipuble.domain.title.TitleNormalizer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Books whose catalog match was uncertain and need the user to confirm a name. */
class ObserveReviewQueueUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    operator fun invoke(): Flow<List<Book>> =
        repository.observeBooks().map { books -> books.filter { it.needsReview } }
}

/**
 * Confirms a reviewed book's official series name: optionally teaches the
 * catalog the name (so future imports match it automatically), applies it, and
 * — when the auto-shelve setting is on — files the book into a per-series shelf
 * (a category named after the series, created on first use).
 */
class ResolveReviewUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val catalogRepository: CatalogRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
) {
    suspend operator fun invoke(bookId: Long, canonicalSeries: String, addToCatalog: Boolean) {
        if (addToCatalog) catalogRepository.addSeries(canonicalSeries)
        bookRepository.applyCanonicalName(bookId, canonicalSeries)

        if (preferencesRepository.preferences.first().autoShelveBySeries) {
            val categoryId = categoryRepository.ensureCategory(
                name = canonicalSeries,
                colorArgb = SeriesShelfColor.forName(canonicalSeries),
            )
            bookRepository.setBookCategory(bookId, categoryId)
        }
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
 * Manual per-book rename: the user supplies the series and an optional (decimal-
 * capable) volume directly. Rebuilds the title and dedup key, and optionally
 * files the book into a per-series bookmark (independent of the auto-shelve
 * setting — here the user asks for it explicitly).
 */
class RenameBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(
        bookId: Long,
        series: String,
        volume: String?,
        addToBookmark: Boolean,
    ) {
        val cleanSeries = series.trim()
        if (cleanSeries.isEmpty()) return
        val v = volume?.trim()?.takeIf { it.isNotEmpty() }?.let { TitleNormalizer.canonicalVolume(it) }
        bookRepository.renameBook(
            bookId = bookId,
            title = TitleNormalizer.displayTitleFor(cleanSeries, v),
            dedupKey = TitleNormalizer.dedupKeyFor(cleanSeries, v),
        )
        if (addToBookmark) {
            val categoryId = categoryRepository.ensureCategory(cleanSeries, SeriesShelfColor.forName(cleanSeries))
            bookRepository.setBookCategory(bookId, categoryId)
        }
    }
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
