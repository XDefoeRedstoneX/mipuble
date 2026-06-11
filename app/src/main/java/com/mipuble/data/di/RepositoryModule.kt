package com.mipuble.data.di

import com.mipuble.data.preferences.ReaderPreferencesRepositoryImpl
import com.mipuble.data.repository.BookRepositoryImpl
import com.mipuble.data.repository.CategoryRepositoryImpl
import com.mipuble.data.repository.RemoteLibraryRepositoryImpl
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CategoryRepository
import com.mipuble.domain.repository.ReaderPreferencesRepository
import com.mipuble.domain.repository.RemoteLibraryRepository
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

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindRemoteLibraryRepository(
        impl: RemoteLibraryRepositoryImpl,
    ): RemoteLibraryRepository
}
