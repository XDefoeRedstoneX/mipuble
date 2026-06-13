package com.mipuble.domain.usecase

import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.model.UploadProgress
import com.mipuble.domain.model.UploadSummary
import com.mipuble.domain.repository.RemoteLibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDownloadsUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    operator fun invoke(): Flow<Map<Long, DownloadStatus>> = repository.downloads
}

class ObserveUploadsUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    operator fun invoke(): Flow<UploadProgress?> = repository.uploads
}

class UploadBooksToDriveUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(uriStrings: List<String>): Result<UploadSummary> =
        repository.uploadBooks(uriStrings)
}

class UploadFolderToDriveUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(treeUriString: String): Result<UploadSummary> =
        repository.uploadFolder(treeUriString)
}

class ResetLibraryToDriveUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(uploadLocalFirst: Boolean): Result<Int> =
        repository.resetToDrive(uploadLocalFirst)
}

class DeleteBookUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(bookId: Long, alsoFromDrive: Boolean): Result<Unit> =
        repository.deleteBook(bookId, alsoFromDrive)
}

class CheckRemoteAvailabilityUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(): Boolean = repository.isAvailable()
}

class SyncRemoteLibraryUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(): Result<Int> = repository.sync()
}

class DownloadBookUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(bookId: Long): Result<Unit> = repository.download(bookId)
}

class EvictBookUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    suspend operator fun invoke(bookId: Long): Result<Unit> = repository.evict(bookId)
}
