package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.model.Category
import com.mipuble.domain.model.ImportOutcome
import com.mipuble.domain.model.PageTurnMode
import com.mipuble.domain.model.ReaderFont
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderTheme
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CatalogRepository
import com.mipuble.domain.repository.CategoryRepository
import com.mipuble.domain.repository.ReaderPreferencesRepository
import com.mipuble.domain.title.SeriesCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewQueueUseCasesTest {

    private fun book(id: Long, needsReview: Boolean) = Book(
        id = id,
        title = "Book $id",
        author = "Author",
        addedAtEpochMillis = 0L,
        progress = 0f,
        needsReview = needsReview,
    )

    private class FakeBookRepository(private val books: List<Book>) : BookRepository {
        var applied: Pair<Long, String>? = null
        var dismissed: Long? = null
        var assignedCategory: Pair<Long, Long?>? = null

        override fun observeBooks(): Flow<List<Book>> = flowOf(books)
        override suspend fun getBook(id: Long): Book? = books.firstOrNull { it.id == id }
        override suspend fun updateReadingPosition(id: Long, chapter: Int, progress: Float) = Unit
        override suspend fun importBook(uriString: String): Result<ImportOutcome> =
            Result.success(ImportOutcome.Added(0L))
        override suspend fun setBookCategory(bookId: Long, categoryId: Long?) {
            assignedCategory = bookId to categoryId
        }
        override suspend fun saveCustomOrder(orderedBookIds: List<Long>) = Unit
        override suspend fun rebuildMissingCovers(): Int = 0
        override suspend fun applyCanonicalName(bookId: Long, canonicalSeries: String) {
            applied = bookId to canonicalSeries
        }
        override suspend fun dismissReview(bookId: Long) {
            dismissed = bookId
        }
        val marked = mutableListOf<Pair<Long, List<String>>>()
        override suspend fun markForReview(bookId: Long, suggestions: List<String>) {
            marked += bookId to suggestions
        }
    }

    private class FakeCatalogRepository : CatalogRepository {
        val added = mutableListOf<String>()
        override suspend fun catalog(): SeriesCatalog = SeriesCatalog(emptyList())
        override suspend fun addSeries(name: String) {
            added += name
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        val ensured = mutableListOf<String>()
        override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun createCategory(name: String, colorArgb: Int): Long = 1L
        override suspend fun ensureCategory(name: String, colorArgb: Int): Long {
            ensured += name
            return 42L
        }
        override suspend fun updateCategory(id: Long, name: String, colorArgb: Int) = Unit
        override suspend fun deleteCategory(id: Long) = Unit
    }

    private class FakePreferences(autoShelve: Boolean) : ReaderPreferencesRepository {
        override val preferences = flowOf(ReaderPreferences(autoShelveBySeries = autoShelve))
        override suspend fun setTheme(theme: ReaderTheme) = Unit
        override suspend fun setFontScalePercent(value: Int) = Unit
        override suspend fun setLineSpacingPercent(value: Int) = Unit
        override suspend fun setBrightnessPercent(value: Int) = Unit
        override suspend fun setFollowSystemBrightness(enabled: Boolean) = Unit
        override suspend fun setFont(font: ReaderFont) = Unit
        override suspend fun setPageTurnMode(mode: PageTurnMode) = Unit
        override suspend fun setAutoShelveBySeries(enabled: Boolean) = Unit
    }

    private fun resolveUseCase(
        repo: FakeBookRepository,
        catalog: FakeCatalogRepository,
        category: FakeCategoryRepository = FakeCategoryRepository(),
        autoShelve: Boolean = false,
    ) = ResolveReviewUseCase(repo, catalog, category, FakePreferences(autoShelve))

    @Test
    fun `review queue keeps only books needing review`() = runTest {
        val repo = FakeBookRepository(listOf(book(1, true), book(2, false), book(3, true)))

        val queue = ObserveReviewQueueUseCase(repo)().first()

        assertEquals(listOf(1L, 3L), queue.map { it.id })
    }

    @Test
    fun `resolving teaches the catalog when asked then applies the name`() = runTest {
        val repo = FakeBookRepository(emptyList())
        val catalog = FakeCatalogRepository()

        resolveUseCase(repo, catalog)(7L, "Spice and Wolf", addToCatalog = true)

        assertTrue(catalog.added.contains("Spice and Wolf"))
        assertEquals(7L to "Spice and Wolf", repo.applied)
        assertEquals(null, repo.assignedCategory) // auto-shelve off by default
    }

    @Test
    fun `resolving without add-to-catalog leaves the catalog untouched`() = runTest {
        val repo = FakeBookRepository(emptyList())
        val catalog = FakeCatalogRepository()

        resolveUseCase(repo, catalog)(7L, "Berserk", addToCatalog = false)

        assertTrue(catalog.added.isEmpty())
        assertEquals(7L to "Berserk", repo.applied)
    }

    @Test
    fun `auto-shelve files the book into a per-series category`() = runTest {
        val repo = FakeBookRepository(emptyList())
        val category = FakeCategoryRepository()

        resolveUseCase(repo, FakeCatalogRepository(), category, autoShelve = true)(
            7L,
            "Spice and Wolf",
            addToCatalog = false,
        )

        assertTrue(category.ensured.contains("Spice and Wolf"))
        assertEquals(7L to 42L, repo.assignedCategory)
    }

    @Test
    fun `queueing existing books marks each one for review`() = runTest {
        val repo = FakeBookRepository(listOf(book(1, false), book(2, false)))

        QueueBooksForReviewUseCase(repo, FakeCatalogRepository())(listOf(1L, 2L))

        assertEquals(listOf(1L, 2L), repo.marked.map { it.first })
    }

    @Test
    fun `dismiss delegates to the repository`() = runTest {
        val repo = FakeBookRepository(emptyList())

        DismissReviewUseCase(repo)(9L)

        assertEquals(9L, repo.dismissed)
        assertFalse(repo.applied != null)
    }
}
