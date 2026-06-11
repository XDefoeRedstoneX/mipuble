package com.mipuble.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE books SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun clearCategoryFromBooks(categoryId: Long)

    /** Deletes the category and un-assigns its books atomically. */
    @Transaction
    suspend fun deleteAndUnassign(id: Long) {
        clearCategoryFromBooks(id)
        deleteById(id)
    }
}
