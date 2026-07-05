package com.mipuble.domain.usecase

import com.mipuble.domain.model.Book
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.sort.BookSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveLibraryUseCaseTest {

    private fun book(
        id: Long,
        title: String,
        author: String = "Author",
        addedAt: Long = 0L,
        categoryId: Long? = null,
        customOrder: Long = 0L,
    ) = Book(
        id = id,
        title = title,
        author = author,
        addedAtEpochMillis = addedAt,
        progress = 0f,
        categoryId = categoryId,
        customOrder = customOrder,
    )

    private val library = listOf(
        book(1, "Vol 10", addedAt = 30),
        book(2, "Vol 1", addedAt = 10),
        book(3, "Vol 2", addedAt = 20),
    )

    private fun repositoryOf(books: List<Book>) = object : BookRepository {
        override fun observeBooks(): Flow<List<Book>> = flowOf(books)
        override suspend fun getBook(id: Long): Book? = books.firstOrNull { it.id == id }
        override suspend fun updateReadingPosition(id: Long, chapter: Int, progress: Float) = Unit
        override suspend fun importBook(uriString: String): Result<com.mipuble.domain.model.ImportOutcome> =
            Result.success(com.mipuble.domain.model.ImportOutcome.Added(0L))
        override suspend fun setBookCategory(bookId: Long, categoryId: Long?) = Unit
        override suspend fun saveCustomOrder(orderedBookIds: List<Long>) = Unit
        override suspend fun rebuildMissingCovers(): Int = 0
        override suspend fun applyCanonicalName(bookId: Long, canonicalSeries: String) = Unit
        override suspend fun dismissReview(bookId: Long) = Unit
        override suspend fun markForReview(bookId: Long, suggestions: List<String>) = Unit
    }

    private val useCase = ObserveLibraryUseCase(repositoryOf(library))

    @Test
    fun `natural sort shelves volumes in reading order`() = runTest {
        val result = useCase(BookSortOption.TITLE_NATURAL).first()

        assertEquals(listOf("Vol 1", "Vol 2", "Vol 10"), result.map { it.title })
    }

    @Test
    fun `lexicographic option reproduces the classic bug on purpose`() = runTest {
        val result = useCase(BookSortOption.TITLE_LEXICOGRAPHIC).first()

        assertEquals(listOf("Vol 1", "Vol 10", "Vol 2"), result.map { it.title })
    }

    @Test
    fun `date added sorts newest first`() = runTest {
        val result = useCase(BookSortOption.DATE_ADDED).first()

        assertEquals(listOf("Vol 10", "Vol 2", "Vol 1"), result.map { it.title })
    }

    @Test
    fun `author sort groups by author then natural title`() = runTest {
        val mixed = listOf(
            book(1, "Vol 10", author = "Zoe"),
            book(2, "Vol 2", author = "Zoe"),
            book(3, "Standalone", author = "Adam"),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(mixed))

        val result = useCase(BookSortOption.AUTHOR).first()

        assertEquals(listOf("Standalone", "Vol 2", "Vol 10"), result.map { it.title })
    }

    @Test
    fun `author sort keeps a series together despite mixed unknown authors`() = runTest {
        // Same series, but only some volumes carry author metadata. A naive
        // by-author sort would scatter the "Unknown" volumes away from the rest;
        // the series-aware sort pins them all to the canonical author.
        val mixed = listOf(
            book(1, "Spice and Wolf, Vol. 2", author = "Unknown"),
            book(2, "Spice and Wolf, Vol. 1", author = "Isuna Hasekura"),
            book(3, "Spice and Wolf, Vol. 10", author = "Unknown"),
            book(4, "Berserk, Vol. 1", author = "Kentaro Miura"),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(mixed))

        val result = useCase(BookSortOption.AUTHOR).first()

        assertEquals(
            listOf(
                "Spice and Wolf, Vol. 1",
                "Spice and Wolf, Vol. 2",
                "Spice and Wolf, Vol. 10",
                "Berserk, Vol. 1",
            ),
            result.map { it.title },
        )
    }

    @Test
    fun `author sort groups a series despite punctuation and mixed authors`() = runTest {
        // Same series, written differently ("Re:Zero" vs "Re Zero") and with one
        // volume tagged, one Unknown — must still stay together under one author.
        val mixed = listOf(
            book(1, "Re:Zero, Vol. 2", author = "Unknown"),
            book(2, "Re Zero, Vol. 1", author = "Tappei"),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(mixed))

        val result = useCase(BookSortOption.AUTHOR).first()

        assertEquals(listOf("Re Zero, Vol. 1", "Re:Zero, Vol. 2"), result.map { it.title })
    }

    @Test
    fun `custom sort follows the persisted hand-arranged order`() = runTest {
        val arranged = listOf(
            book(1, "First imported", customOrder = 2),
            book(2, "Second imported", customOrder = 0),
            book(3, "Third imported", customOrder = 1),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(arranged))

        val result = useCase(BookSortOption.CUSTOM).first()

        assertEquals(
            listOf("Second imported", "Third imported", "First imported"),
            result.map { it.title },
        )
    }

    @Test
    fun `custom sort breaks order ties by id`() = runTest {
        val tied = listOf(
            book(9, "Later", customOrder = 5),
            book(4, "Earlier", customOrder = 5),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(tied))

        val result = useCase(BookSortOption.CUSTOM).first()

        assertEquals(listOf("Earlier", "Later"), result.map { it.title })
    }

    @Test
    fun `category filter keeps only that category's books`() = runTest {
        val shelved = listOf(
            book(1, "Vol 2", categoryId = 7),
            book(2, "Vol 1", categoryId = 7),
            book(3, "Unshelved"),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(shelved))

        val result = useCase(BookSortOption.TITLE_NATURAL, categoryId = 7).first()

        assertEquals(listOf("Vol 1", "Vol 2"), result.map { it.title })
    }

    @Test
    fun `null category filter returns everything`() = runTest {
        val shelved = listOf(
            book(1, "A", categoryId = 7),
            book(2, "B"),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(shelved))

        val result = useCase(BookSortOption.TITLE_NATURAL, categoryId = null).first()

        assertEquals(2, result.size)
    }

    @Test
    fun `snapshot picks the furthest-along open downloaded book as the hero`() = runTest {
        val mixed = listOf(
            book(1, "A").copy(progress = 0.2f, filePath = "/a"),
            book(2, "B").copy(progress = 0.8f, filePath = "/b"),
            book(3, "C").copy(progress = 1f, filePath = "/c"), // finished — excluded
            book(4, "D").copy(progress = 0.5f), // not downloaded — excluded
            book(5, "E"), // untouched — excluded
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(mixed))

        val snapshot = useCase.snapshot(BookSortOption.TITLE_NATURAL).first()

        assertEquals(2L, snapshot.continueReading?.id)
    }

    @Test
    fun `snapshot's hero and count ignore the active category filter`() = runTest {
        val shelved = listOf(
            book(1, "Shelved").copy(categoryId = 7),
            book(2, "Reading").copy(progress = 0.4f, filePath = "/b"),
        )
        val useCase = ObserveLibraryUseCase(repositoryOf(shelved))

        val snapshot = useCase.snapshot(BookSortOption.TITLE_NATURAL, categoryId = 7).first()

        assertEquals(listOf("Shelved"), snapshot.books.map { it.title })
        assertEquals(2L, snapshot.continueReading?.id)
        assertEquals(2, snapshot.totalCount)
    }
}
