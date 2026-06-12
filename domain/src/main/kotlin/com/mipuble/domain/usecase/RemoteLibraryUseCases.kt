package com.mipuble.domain.usecase

import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.repository.RemoteLibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDownloadsUseCase @Inject constructor(
    private val repository: RemoteLibraryRepository,
) {
    operator fun invoke(): Flow<Map<Long, DownloadStatus>> = repository.downloads
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
