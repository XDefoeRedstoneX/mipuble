package com.mipuble.ui.library

import com.mipuble.MainDispatcherRule
import com.mipuble.data.remote.UnconfiguredDriveAuthProvider
import com.mipuble.domain.model.Book
import com.mipuble.domain.model.Category
import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.CategoryRepository
import com.mipuble.domain.repository.RemoteLibraryRepository
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.usecase.AssignBookCategoryUseCase
import com.mipuble.domain.usecase.CreateCategoryUseCase
import com.mipuble.domain.usecase.DeleteBookUseCase
import com.mipuble.domain.usecase.DeleteCategoryUseCase
import com.mipuble.domain.usecase.DismissReviewUseCase
import com.mipuble.domain.usecase.DownloadBookUseCase
import com.mipuble.domain.usecase.EvictBookUseCase
import com.mipuble.domain.usecase.ImportEpubUseCase
import com.mipuble.domain.usecase.ObserveCategoriesUseCase
import com.mipuble.domain.usecase.ObserveDownloadsUseCase
import com.mipuble.domain.usecase.ObserveLibraryUseCase
import com.mipuble.domain.usecase.ObserveReviewQueueUseCase
import com.mipuble.domain.usecase.ObserveUploadsUseCase
import com.mipuble.domain.usecase.QueueBooksForReviewUseCase
import com.mipuble.domain.usecase.ResolveReviewUseCase
import com.mipuble.domain.usecase.SaveCustomOrderUseCase
import com.mipuble.domain.usecase.SearchCatalogUseCase
import com.mipuble.domain.usecase.SyncRemoteLibraryUseCase
import com.mipuble.domain.usecase.UpdateCategoryUseCase
import com.mipuble.domain.model.ImportOutcome
import com.mipuble.domain.model.UploadSummary
import com.mipuble.domain.usecase.UploadBooksToDriveUseCase
import com.mipuble.domain.usecase.UploadFolderToDriveUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun book(id: Long, title: String, addedAt: Long = 0L) = Book(
        id = id,
        title = title,
        author = "Author",
        addedAtEpochMillis = addedAt,
        progress = 0f,
    )

    private class FakeBookRepository(
        val books: MutableStateFlow<List<Book>>,
    ) : BookRepository {
        var importResult: Result<ImportOutcome> = Result.success(ImportOutcome.Added(1L))
        var savedOrder: List<Long>? = null

        override fun observeBooks(): Flow<List<Book>> = books
        override suspend fun getBook(id: Long): Book? = books.value.firstOrNull { it.id == id }
        override suspend fun updateReadingPosition(id: Long, chapter: Int, progress: Float) = Unit
        override suspend fun importBook(uriString: String): Result<ImportOutcome> = importResult
        override suspend fun setBookCategory(bookId: Long, categoryId: Long?) = Unit
        override suspend fun saveCustomOrder(orderedBookIds: List<Long>) {
            savedOrder = orderedBookIds
        }
        override suspend fun rebuildMissingCovers(): Int = 0
        override suspend fun applyCanonicalName(bookId: Long, canonicalSeries: String) = Unit
        override suspend fun dismissReview(bookId: Long) = Unit
        override suspend fun markForReview(bookId: Long, suggestions: List<String>) = Unit
    }

    private class FakeCategoryRepository : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun createCategory(name: String, colorArgb: Int): Long = 1L
        override suspend fun ensureCategory(name: String, colorArgb: Int): Long = 1L
        override suspend fun updateCategory(id: Long, name: String, colorArgb: Int) = Unit
        override suspend fun deleteCategory(id: Long) = Unit
    }

    private class FakeCatalogRepository : com.mipuble.domain.repository.CatalogRepository {
        override suspend fun catalog() = com.mipuble.domain.title.SeriesCatalog(emptyList())
        override suspend fun addSeries(name: String) = Unit
    }

    private class FakePreferencesRepository :
        com.mipuble.domain.repository.ReaderPreferencesRepository {
        override val preferences =
            MutableStateFlow(com.mipuble.domain.model.ReaderPreferences())
        override suspend fun setTheme(theme: com.mipuble.domain.model.ReaderTheme) = Unit
        override suspend fun setFontScalePercent(value: Int) = Unit
        override suspend fun setLineSpacingPercent(value: Int) = Unit
        override suspend fun setBrightnessPercent(value: Int) = Unit
        override suspend fun setFollowSystemBrightness(enabled: Boolean) = Unit
        override suspend fun setFont(font: com.mipuble.domain.model.ReaderFont) = Unit
        override suspend fun setPageTurnMode(mode: com.mipuble.domain.model.PageTurnMode) = Unit
        override suspend fun setAutoShelveBySeries(enabled: Boolean) = Unit
    }

    private class FakeRemoteRepository : RemoteLibraryRepository {
        override val downloads: Flow<Map<Long, DownloadStatus>> = MutableStateFlow(emptyMap())
        override val uploads: Flow<com.mipuble.domain.model.UploadProgress?> = MutableStateFlow(null)
        override suspend fun isAvailable(): Boolean = true
        override suspend fun sync(): Result<Int> = Result.success(2)
        override suspend fun download(bookId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun evict(bookId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun uploadBooks(uriStrings: List<String>): Result<UploadSummary> =
            Result.success(UploadSummary(uriStrings.size, 0))
        override suspend fun uploadFolder(treeUriString: String): Result<UploadSummary> =
            Result.success(UploadSummary(0, 0))
        override suspend fun resetToDrive(uploadLocalFirst: Boolean): Result<Int> = Result.success(0)
        override suspend fun deleteBook(bookId: Long, alsoFromDrive: Boolean): Result<Unit> = Result.success(Unit)
    }

    private val bookRepository = FakeBookRepository(
        MutableStateFlow(
            listOf(
                book(1, "Vol 10", addedAt = 30),
                book(2, "Vol 1", addedAt = 10),
                book(3, "Vol 2", addedAt = 20),
            ),
        ),
    )

    private fun viewModel(): LibraryViewModel {
        val categoryRepository = FakeCategoryRepository()
        val remoteRepository = FakeRemoteRepository()
        return LibraryViewModel(
            observeLibrary = ObserveLibraryUseCase(bookRepository),
            observeCategories = ObserveCategoriesUseCase(categoryRepository),
            observeDownloads = ObserveDownloadsUseCase(remoteRepository),
            observeUploads = ObserveUploadsUseCase(remoteRepository),
            importEpub = ImportEpubUseCase(bookRepository),
            uploadBooks = UploadBooksToDriveUseCase(remoteRepository),
            uploadFolder = UploadFolderToDriveUseCase(remoteRepository),
            createCategory = CreateCategoryUseCase(categoryRepository),
            updateCategory = UpdateCategoryUseCase(categoryRepository),
            deleteCategory = DeleteCategoryUseCase(categoryRepository),
            assignBookCategory = AssignBookCategoryUseCase(bookRepository),
            saveCustomOrder = SaveCustomOrderUseCase(bookRepository),
            syncRemoteLibrary = SyncRemoteLibraryUseCase(remoteRepository),
            downloadBook = DownloadBookUseCase(remoteRepository),
            evictBook = EvictBookUseCase(remoteRepository),
            deleteBook = DeleteBookUseCase(remoteRepository),
            observeReviewQueue = ObserveReviewQueueUseCase(bookRepository),
            resolveReview = ResolveReviewUseCase(
                bookRepository,
                FakeCatalogRepository(),
                categoryRepository,
                FakePreferencesRepository(),
            ),
            dismissReview = DismissReviewUseCase(bookRepository),
            searchCatalog = SearchCatalogUseCase(FakeCatalogRepository()),
            queueBooksForReview = QueueBooksForReviewUseCase(bookRepository, FakeCatalogRepository()),
            driveAuthProvider = UnconfiguredDriveAuthProvider(),
        )
    }

    @Test
    fun `default state shows books in natural title order`() = runTest {
        val vm = viewModel()
        val collector = launch { vm.uiState.collect {} } // activate WhileSubscribed
        advanceUntilIdle()

        assertEquals(
            listOf("Vol 1", "Vol 2", "Vol 10"),
            vm.uiState.value.books.map { it.title },
        )
        assertEquals(BookSortOption.TITLE_NATURAL, vm.uiState.value.sortOption)

        collector.cancel()
    }

    @Test
    fun `changing the sort re-orders the same books`() = runTest {
        val vm = viewModel()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onSortSelected(BookSortOption.DATE_ADDED)
        advanceUntilIdle()

        assertEquals(
            listOf("Vol 10", "Vol 2", "Vol 1"),
            vm.uiState.value.books.map { it.title },
        )

        collector.cancel()
    }

    @Test
    fun `reordering is only enabled in custom sort with no filter`() = runTest {
        val vm = viewModel()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.isReorderingEnabled)

        vm.onSortSelected(BookSortOption.CUSTOM)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReorderingEnabled)

        vm.onCategorySelected(5L)
        advanceUntilIdle()
        assertTrue(!vm.uiState.value.isReorderingEnabled)

        collector.cancel()
    }

    @Test
    fun `a failed import surfaces a user-facing message`() = runTest {
        bookRepository.importResult = Result.failure(RuntimeException("boom"))
        val vm = viewModel()

        vm.onImport("content://whatever")
        advanceUntilIdle()

        assertTrue(vm.messages.value.orEmpty().contains("Couldn't import"))
    }

    @Test
    fun `drop persists the dragged order`() = runTest {
        val vm = viewModel()

        vm.onReorder(listOf(3L, 1L, 2L))
        advanceUntilIdle()

        assertEquals(listOf(3L, 1L, 2L), bookRepository.savedOrder)
    }

    @Test
    fun `sync reports how many new books arrived`() = runTest {
        val vm = viewModel()

        vm.onSync()
        advanceUntilIdle()

        assertTrue(vm.messages.value.orEmpty().contains("2 new book"))
    }
}
