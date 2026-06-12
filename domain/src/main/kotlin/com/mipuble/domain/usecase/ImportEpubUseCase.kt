package com.mipuble.domain.usecase

import com.mipuble.domain.repository.BookRepository
import javax.inject.Inject

/** Imports an EPUB the user picked, returning the new book's id on success. */
class ImportEpubUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(uriString: String): Result<Long> =
        repository.importBook(uriString)
}
