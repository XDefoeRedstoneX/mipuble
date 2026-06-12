package com.mipuble.domain.repository

import com.mipuble.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>

    suspend fun createCategory(name: String, colorArgb: Int): Long

    /** Renames and/or recolors an existing category. */
    suspend fun updateCategory(id: Long, name: String, colorArgb: Int)

    /** Deleting a category un-assigns its books rather than deleting them. */
    suspend fun deleteCategory(id: Long)
}
