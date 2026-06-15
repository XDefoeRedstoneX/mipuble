package com.mipuble.domain.usecase

import com.mipuble.domain.model.ImportOutcome
import com.mipuble.domain.repository.BookRepository
import javax.inject.Inject

/** Imports an EPUB the user picked; duplicates come back as [ImportOutcome.Duplicate]. */
class ImportEpubUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(uriString: String): Result<ImportOutcome> =
        repository.importBook(uriString)
}

/** Backfills covers for downloaded books still showing a placeholder. */
class RebuildCoversUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(): Result<Int> =
        runCatching { repository.rebuildMissingCovers() }
}
