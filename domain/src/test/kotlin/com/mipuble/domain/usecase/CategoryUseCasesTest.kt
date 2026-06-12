package com.mipuble.domain.usecase

import com.mipuble.domain.model.Category
import com.mipuble.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryUseCasesTest {

    private class RecordingCategoryRepository : CategoryRepository {
        var created: Pair<String, Int>? = null
        var updated: Triple<Long, String, Int>? = null
        var deletedId: Long? = null

        override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())

        override suspend fun createCategory(name: String, colorArgb: Int): Long {
            created = name to colorArgb
            return 1L
        }

        override suspend fun updateCategory(id: Long, name: String, colorArgb: Int) {
            updated = Triple(id, name, colorArgb)
        }

        override suspend fun deleteCategory(id: Long) {
            deletedId = id
        }
    }

    @Test
    fun `create trims the name before saving`() = runTest {
        val repository = RecordingCategoryRepository()

        val result = CreateCategoryUseCase(repository)("  Reading now  ", 0xFF00FF00.toInt())

        assertTrue(result.isSuccess)
        assertEquals("Reading now" to 0xFF00FF00.toInt(), repository.created)
    }

    @Test
    fun `create rejects blank names without touching the repository`() = runTest {
        val repository = RecordingCategoryRepository()

        val result = CreateCategoryUseCase(repository)("   ", 0)

        assertTrue(result.isFailure)
        assertEquals(null, repository.created)
    }

    @Test
    fun `update trims the name and passes the new color`() = runTest {
        val repository = RecordingCategoryRepository()

        val result = UpdateCategoryUseCase(repository)(5L, "  Renamed  ", 0xFF112233.toInt())

        assertTrue(result.isSuccess)
        assertEquals(Triple(5L, "Renamed", 0xFF112233.toInt()), repository.updated)
    }

    @Test
    fun `update rejects blank names`() = runTest {
        val repository = RecordingCategoryRepository()

        val result = UpdateCategoryUseCase(repository)(5L, " ", 0)

        assertTrue(result.isFailure)
        assertEquals(null, repository.updated)
    }

    @Test
    fun `delete passes through`() = runTest {
        val repository = RecordingCategoryRepository()

        DeleteCategoryUseCase(repository)(42L)

        assertEquals(42L, repository.deletedId)
    }
}
