package com.mipuble.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.MipubleDatabase
import com.mipuble.data.local.SeedData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        // Provider breaks the circular dependency: the callback needs the DAO,
        // but the DAO comes from the database being built here. By the time
        // onCreate fires (first query), the singleton already exists.
        daoProvider: Provider<BookDao>,
        @ApplicationScope scope: CoroutineScope,
    ): MipubleDatabase =
        Room.databaseBuilder(context, MipubleDatabase::class.java, "mipuble.db")
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        scope.launch { daoProvider.get().insertAll(SeedData.books) }
                    }
                },
            )
            .build()

    @Provides
    fun provideBookDao(database: MipubleDatabase): BookDao = database.bookDao()
}
