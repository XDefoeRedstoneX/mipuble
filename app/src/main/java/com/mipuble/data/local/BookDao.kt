package com.mipuble.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** Room re-emits this Flow automatically whenever the books table changes. */
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(books: List<BookEntity>)

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET last_chapter_index = :chapter, progress = :progress WHERE id = :id")
    suspend fun updatePosition(id: Long, chapter: Int, progress: Float)

    @Query("UPDATE books SET cover_path = :coverPath WHERE id = :id")
    suspend fun updateCover(id: Long, coverPath: String)

    @Query("SELECT remote_id FROM books WHERE remote_id IS NOT NULL")
    suspend fun remoteIds(): List<String>

    @Query("SELECT * FROM books WHERE content_hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Query("UPDATE books SET file_path = :filePath WHERE id = :id")
    suspend fun updateFilePath(id: Long, filePath: String?)

    @Query("UPDATE books SET remote_id = :remoteId, remote_size_bytes = :remoteSize WHERE id = :id")
    suspend fun setRemote(id: Long, remoteId: String, remoteSize: Long)

    @Query("UPDATE books SET category_id = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    @Query("UPDATE books SET custom_order = :order WHERE id = :id")
    suspend fun updateCustomOrder(id: Long, order: Long)

    /** Writes a full drag-and-drop arrangement atomically. */
    @Transaction
    suspend fun saveCustomOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            updateCustomOrder(id, index.toLong())
        }
    }
}
