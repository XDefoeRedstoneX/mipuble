package com.mipuble.data.repository

import com.mipuble.data.epub.EpubImporter
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.toDomain
import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val importer: EpubImporter,
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBook(id: Long): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun updateReadingPosition(id: Long, chapter: Int, progress: Float) =
        bookDao.updatePosition(id, chapter, progress)

    override suspend fun importBook(uriString: String): Result<Long> =
        importer.import(uriString)
}
