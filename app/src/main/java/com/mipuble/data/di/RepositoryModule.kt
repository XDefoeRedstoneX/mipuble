package com.mipuble.data.di

import com.mipuble.data.preferences.ReaderPreferencesRepositoryImpl
import com.mipuble.data.repository.BookRepositoryImpl
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.ReaderPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun bindReaderPreferencesRepository(
        impl: ReaderPreferencesRepositoryImpl,
    ): ReaderPreferencesRepository
}
