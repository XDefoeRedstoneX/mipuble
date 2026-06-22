package com.mipuble.domain.repository

import com.mipuble.domain.model.Book
import com.mipuble.domain.model.ImportOutcome
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
     * stays free of Android types). Duplicates are auto-skipped.
     */
    suspend fun importBook(uriString: String): Result<ImportOutcome>

    /** Assigns the book to a category, or clears it with null. */
    suspend fun setBookCategory(bookId: Long, categoryId: Long?)

    /**
     * Persists a hand-arranged order: each book's customOrder becomes its
     * position in [orderedBookIds].
     */
    suspend fun saveCustomOrder(orderedBookIds: List<Long>)

    /**
     * Re-extracts covers for downloaded books that currently show a placeholder
     * (no stored cover). Returns how many gained a real cover. Used to backfill
     * artwork for books imported before the cover-fallback logic existed.
     */
    suspend fun rebuildMissingCovers(): Int

    /**
     * Confirms a reviewed book's series as [canonicalSeries]: rebuilds its title
     * (keeping any detected volume) and clears the review flag.
     */
    suspend fun applyCanonicalName(bookId: Long, canonicalSeries: String)

    /** Keeps a reviewed book's current title as-is and clears the review flag. */
    suspend fun dismissReview(bookId: Long)

    /** Flags an already-imported book for name review with the given suggestions. */
    suspend fun markForReview(bookId: Long, suggestions: List<String>)
}
