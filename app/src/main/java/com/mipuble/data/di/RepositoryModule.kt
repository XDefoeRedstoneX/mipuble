package com.mipuble.data.di

import com.mipuble.data.repository.BookRepositoryImpl
import com.mipuble.domain.repository.BookRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository
}
