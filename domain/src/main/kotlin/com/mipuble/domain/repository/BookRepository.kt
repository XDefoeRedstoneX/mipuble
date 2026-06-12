package com.mipuble.domain.repository

import com.mipuble.domain.model.Book
import kotlinx.coroutines.flow.Flow

/**
 * Contract the data layer fulfills. The domain owns the interface so that
 * use-cases can be tested with fakes and never touch Room directly.
 */
interface BookRepository {
    /** Emits the full library, re-emitting whenever the underlying data changes. */
    fun observeBooks(): Flow<List<Book>>

    /** One-shot lookup for the reader; null if no such book. */
    suspend fun getBook(id: Long): Book?

    /** Persists the resume point and recomputed progress after a page turn. */
    suspend fun updateReadingPosition(id: Long, chapter: Int, progress: Float)

    /**
     * Imports an EPUB from a content Uri (passed as a String so the domain
     * stays free of Android types). Returns the new book's id.
     */
    suspend fun importBook(uriString: String): Result<Long>

    /** Assigns the book to a category, or clears it with null. */
    suspend fun setBookCategory(bookId: Long, categoryId: Long?)

    /**
     * Persists a hand-arranged order: each book's customOrder becomes its
     * position in [orderedBookIds].
     */
    suspend fun saveCustomOrder(orderedBookIds: List<Long>)
}
