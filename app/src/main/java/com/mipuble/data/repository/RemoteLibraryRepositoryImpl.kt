package com.mipuble.data.repository

import android.app.PendingIntent
import android.content.Context
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.BookEntity
import com.mipuble.data.remote.AuthResult
import com.mipuble.data.remote.DriveAuthProvider
import com.mipuble.data.remote.NeedConsentException
import com.mipuble.data.remote.RemoteLibrarySource
import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.repository.RemoteLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class RemoteLibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: RemoteLibrarySource,
    private val authProvider: DriveAuthProvider,
    private val bookDao: BookDao,
) : RemoteLibraryRepository {

    private val _downloads = MutableStateFlow<Map<Long, DownloadStatus>>(emptyMap())
    override val downloads: Flow<Map<Long, DownloadStatus>> = _downloads

    override suspend fun isAvailable(): Boolean = source.isAvailable()

    override suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = authProvider.authenticate()
            when (auth) {
                is AuthResult.ConsentRequired -> throw NeedConsentException(auth.intent)
                is AuthResult.Error -> error("Authentication failed")
                is AuthResult.Success -> { /* proceed */ }
            }

            val known = bookDao.remoteIds().toSet()
            val newBooks = source.listBooks().filter { it.remoteId !in known }

            for (remote in newBooks) {
                val id = bookDao.insert(
                    BookEntity(
                        title = remote.title,
                        author = remote.author.ifBlank { "Unknown" },
                        addedAt = System.currentTimeMillis(),
                        filePath = null, // metadata only — bytes stay remote
                        remoteId = remote.remoteId,
                        remoteSizeBytes = remote.sizeBytes,
                    ),
                )
                // Sort new arrivals to the end of the hand-arranged order.
                bookDao.updateCustomOrder(id, id)

                source.fetchCover(remote.remoteId)?.let { bytes ->
                    val coverFile = File(coversDir(), "$id.cover")
                    coverFile.writeBytes(bytes)
                    bookDao.updateCover(id, coverFile.absolutePath)
                }
            }
            newBooks.size
        }
    }

    override suspend fun download(bookId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId)
        val remoteId = book?.remoteId
        when {
            book == null -> Result.failure(IllegalStateException("No such book"))
            remoteId == null -> Result.failure(IllegalStateException("Book isn't remote"))
            book.filePath != null -> Result.success(Unit) // already downloaded
            else -> {
                val target = File(booksDir(), "${remoteId.toFileName()}.epub")
                setStatus(bookId, DownloadStatus.Downloading(0f))
                runCatching {
                    source.download(remoteId, target) { fraction ->
                        setStatus(bookId, DownloadStatus.Downloading(fraction))
                    }
                    bookDao.updateFilePath(bookId, target.absolutePath)
                }.onSuccess {
                    clearStatus(bookId)
                }.onFailure { e ->
                    target.delete()
                    setStatus(bookId, DownloadStatus.Failed(e.message ?: "Download failed"))
                }
            }
        }
    }

    override suspend fun evict(bookId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId)
        when {
            book == null -> Result.failure(IllegalStateException("No such book"))
            book.remoteId == null -> Result.failure(IllegalStateException("Local-only book can't be evicted"))
            else -> runCatching {
                book.filePath?.let { File(it).delete() }
                bookDao.updateFilePath(bookId, null)
            }
        }
    }

    private fun setStatus(bookId: Long, status: DownloadStatus) {
        _downloads.update { it + (bookId to status) }
    }

    private fun clearStatus(bookId: Long) {
        _downloads.update { it - bookId }
    }

    private fun booksDir() = File(context.filesDir, "books").apply { mkdirs() }
    private fun coversDir() = File(context.filesDir, "covers").apply { mkdirs() }

    private fun String.toFileName(): String = replace(Regex("[^A-Za-z0-9_-]"), "_")
}
