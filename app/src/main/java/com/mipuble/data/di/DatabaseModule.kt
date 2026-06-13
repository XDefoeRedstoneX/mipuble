package com.mipuble.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.CategoryDao
import com.mipuble.data.local.DatabaseSeeder
import com.mipuble.data.local.MipubleDatabase
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
        // Provider breaks the circular dependency: the seeder needs the DAO,
        // but the DAO comes from the database being built here. By the time
        // onCreate fires (first query), the singleton already exists.
        seederProvider: Provider<DatabaseSeeder>,
        @ApplicationScope scope: CoroutineScope,
    ): MipubleDatabase =
        Room.databaseBuilder(context, MipubleDatabase::class.java, "mipuble.db")
            .addMigrations(
                MipubleDatabase.MIGRATION_1_2,
                MipubleDatabase.MIGRATION_2_3,
                MipubleDatabase.MIGRATION_3_4,
                MipubleDatabase.MIGRATION_4_5,
            )
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        scope.launch { seederProvider.get().seed() }
                    }
                },
            )
            .build()

    @Provides
    fun provideBookDao(database: MipubleDatabase): BookDao = database.bookDao()

    @Provides
    fun provideCategoryDao(database: MipubleDatabase): CategoryDao = database.categoryDao()
}
