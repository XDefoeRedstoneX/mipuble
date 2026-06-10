package com.mipuble.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** Room re-emits this Flow automatically whenever the books table changes. */
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Insert
    suspend fun insertAll(books: List<BookEntity>)
}
