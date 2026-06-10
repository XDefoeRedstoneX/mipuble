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

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Insert
    suspend fun insertAll(books: List<BookEntity>)

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET last_chapter_index = :chapter, progress = :progress WHERE id = :id")
    suspend fun updatePosition(id: Long, chapter: Int, progress: Float)

    @Query("UPDATE books SET cover_path = :coverPath WHERE id = :id")
    suspend fun updateCover(id: Long, coverPath: String)
}
