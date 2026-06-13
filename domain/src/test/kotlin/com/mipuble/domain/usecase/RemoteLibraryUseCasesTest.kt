package com.mipuble.domain.usecase

import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.repository.RemoteLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLibraryUseCasesTest {

    private class FakeRemoteRepository : RemoteLibraryRepository {
        val downloaded = mutableListOf<Long>()
        val evicted = mutableListOf<Long>()
        var uploadedBatch: List<String>? = null
        var resetUploadedFirst: Boolean? = null
        var syncResult: Result<Int> = Result.success(0)

        override val downloads: Flow<Map<Long, DownloadStatus>> =
            flowOf(mapOf(1L to DownloadStatus.Downloading(0.5f)))
        override val uploads: Flow<com.mipuble.domain.model.UploadProgress?> = flowOf(null)

        override suspend fun isAvailable(): Boolean = true
        override suspend fun sync(): Result<Int> = syncResult
        override suspend fun download(bookId: Long): Result<Unit> {
            downloaded += bookId
            return Result.success(Unit)
        }

        override suspend fun evict(bookId: Long): Result<Unit> {
            evicted += bookId
            return Result.success(Unit)
        }

        override suspend fun uploadBooks(uriStrings: List<String>): Result<Int> {
            uploadedBatch = uriStrings
            return Result.success(uriStrings.size)
        }

        override suspend fun resetToDrive(uploadLocalFirst: Boolean): Result<Int> {
            resetUploadedFirst = uploadLocalFirst
            return Result.success(0)
        }

        var deleted: Pair<Long, Boolean>? = null
        override suspend fun deleteBook(bookId: Long, alsoFromDrive: Boolean): Result<Unit> {
            deleted = bookId to alsoFromDrive
            return Result.success(Unit)
        }
    }

    @Test
    fun `sync passes the new-book count through`() = runTest {
        val repository = FakeRemoteRepository().apply { syncResult = Result.success(3) }

        val result = SyncRemoteLibraryUseCase(repository)()

        assertEquals(3, result.getOrNull())
    }

    @Test
    fun `download delegates to the repository`() = runTest {
        val repository = FakeRemoteRepository()

        DownloadBookUseCase(repository)(42L)

        assertEquals(listOf(42L), repository.downloaded)
    }

    @Test
    fun `evict delegates to the repository`() = runTest {
        val repository = FakeRemoteRepository()

        EvictBookUseCase(repository)(7L)

        assertEquals(listOf(7L), repository.evicted)
    }

    @Test
    fun `upload passes the uri batch through and counts them`() = runTest {
        val repository = FakeRemoteRepository()

        val result = UploadBooksToDriveUseCase(repository)(listOf("a", "b"))

        assertEquals(2, result.getOrNull())
        assertEquals(listOf("a", "b"), repository.uploadedBatch)
    }

    @Test
    fun `reset forwards the upload-first flag`() = runTest {
        val repository = FakeRemoteRepository()

        ResetLibraryToDriveUseCase(repository)(uploadLocalFirst = true)

        assertEquals(true, repository.resetUploadedFirst)
    }

    @Test
    fun `delete forwards the book id and drive flag`() = runTest {
        val repository = FakeRemoteRepository()

        DeleteBookUseCase(repository)(bookId = 11L, alsoFromDrive = true)

        assertEquals(11L to true, repository.deleted)
    }

    @Test
    fun `downloads flow surfaces per-book progress`() = runTest {
        val repository = FakeRemoteRepository()

        val first = ObserveDownloadsUseCase(repository)().let { flow ->
            var captured: Map<Long, DownloadStatus> = emptyMap()
            flow.collect { captured = it }
            captured
        }

        assertTrue(first[1L] is DownloadStatus.Downloading)
    }
}
