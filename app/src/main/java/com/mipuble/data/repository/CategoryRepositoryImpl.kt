package com.mipuble.data.repository

import com.mipuble.data.local.CategoryDao
import com.mipuble.data.local.CategoryEntity
import com.mipuble.data.local.toDomain
import com.mipuble.domain.model.Category
import com.mipuble.domain.repository.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createCategory(name: String, colorArgb: Int): Long =
        categoryDao.insert(CategoryEntity(name = name, colorArgb = colorArgb))

    override suspend fun deleteCategory(id: Long) = categoryDao.deleteAndUnassign(id)
}
