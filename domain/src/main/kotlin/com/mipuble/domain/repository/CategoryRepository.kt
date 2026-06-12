package com.mipuble.domain.repository

import com.mipuble.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>

    suspend fun createCategory(name: String, colorArgb: Int): Long

    /** Deleting a category un-assigns its books rather than deleting them. */
    suspend fun deleteCategory(id: Long)
}
