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
}
