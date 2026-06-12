package com.mipuble.domain.usecase

import com.mipuble.domain.model.Category
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = repository.observeCategories()
}

class CreateCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(name: String, colorArgb: Int): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name can't be empty"))
        return runCatching { repository.createCategory(trimmed, colorArgb) }
    }
}

class UpdateCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: Long, name: String, colorArgb: Int): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name can't be empty"))
        return runCatching { repository.updateCategory(id, trimmed, colorArgb) }
    }
}

class DeleteCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteCategory(id)
}

class AssignBookCategoryUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(bookId: Long, categoryId: Long?) =
        repository.setBookCategory(bookId, categoryId)
}

/** Persists the order produced by a drag-and-drop session. */
class SaveCustomOrderUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(orderedBookIds: List<Long>) =
        repository.saveCustomOrder(orderedBookIds)
}
