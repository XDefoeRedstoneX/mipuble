package com.mipuble.data.repository

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
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }
}
