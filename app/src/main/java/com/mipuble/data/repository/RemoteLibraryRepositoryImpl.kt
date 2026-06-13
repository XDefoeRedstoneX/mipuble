package com.mipuble.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.mipuble.data.epub.EpubImporter
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.BookEntity
import com.mipuble.data.remote.AuthResult
import com.mipuble.data.remote.DriveAuthProvider
import com.mipuble.data.remote.NeedConsentException
import com.mipuble.data.remote.RemoteLibrarySource
import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.model.UploadProgress
import com.mipuble.domain.model.UploadSummary
import com.mipuble.domain.repository.RemoteLibraryRepository
import com.mipuble.domain.title.TitleNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
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
    private val importer: EpubImporter,
) : RemoteLibraryRepository {

    private val _downloads = MutableStateFlow<Map<Long, DownloadStatus>>(emptyMap())
    override val downloads: Flow<Map<Long, DownloadStatus>> = _downloads

    private val _uploads = MutableStateFlow<UploadProgress?>(null)
    override val uploads: Flow<UploadProgress?> = _uploads

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

    override suspend fun deleteBook(bookId: Long, alsoFromDrive: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val book = bookDao.getById(bookId)
                ?: return@withContext Result.failure(IllegalStateException("No such book"))
            runCatching {
                if (alsoFromDrive) book.remoteId?.let { source.trashBook(it) }
                book.filePath?.let { File(it).delete() }
                book.coverPath?.let { File(it).delete() }
                bookDao.deleteById(bookId)
            }
        }

    override suspend fun uploadBooks(uriStrings: List<String>): Result<UploadSummary> =
        withContext(Dispatchers.IO) {
            runCatching { uploadAll(uriStrings) }.also { _uploads.value = null }
        }

    override suspend fun uploadFolder(treeUriString: String): Result<UploadSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Surface a scanning state first — a big tree can take a moment.
                _uploads.value = UploadProgress(0, 0, "Scanning folder…", 0f, scanning = true)
                uploadAll(scanFolderForEpubs(treeUriString))
            }.also { _uploads.value = null }
        }

    /** Shared upload loop with duplicate-skipping; used by file- and folder-pick. */
    private suspend fun uploadAll(uriStrings: List<String>): UploadSummary {
        if (!source.isAvailable()) error("Not signed in to Drive")
        // Logical keys already in the Drive folder, to skip re-uploading them.
        val remoteKeys = source.listBooks()
            .mapNotNull { TitleNormalizer.normalize(it.title).dedupKey }
            .toSet()

        var added = 0
        var skipped = 0
        uriStrings.forEachIndexed { index, uriString ->
            val local = copyUriToStorage(uriString)
            val identity = importer.identify(local, fallbackName = displayName(uriString))
            _uploads.value = UploadProgress(index + 1, uriStrings.size, identity.displayTitle, 0f)

            val duplicate = importer.isDuplicate(identity) ||
                (identity.dedupKey != null && identity.dedupKey in remoteKeys)
            if (duplicate) {
                local.delete()
                skipped++
                return@forEachIndexed
            }

            // Upload under the cleaned-up name so the Drive folder stays tidy.
            val remote = source.uploadBook(local, identity.displayTitle) { fraction ->
                _uploads.value = UploadProgress(index + 1, uriStrings.size, identity.displayTitle, fraction)
            }
            importer.register(local, remoteId = remote.remoteId, remoteSize = remote.sizeBytes)
            added++
        }
        return UploadSummary(added = added, skipped = skipped)
    }

    /** Recursively collects content-Uri strings of every EPUB under a tree Uri. */
    private fun scanFolderForEpubs(treeUriString: String): List<String> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return emptyList()
        val found = mutableListOf<String>()
        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { entry ->
                when {
                    entry.isDirectory -> walk(entry)
                    entry.name?.endsWith(".epub", ignoreCase = true) == true ||
                        entry.type == "application/epub+zip" -> found += entry.uri.toString()
                }
            }
        }
        walk(root)
        return found
    }

    override suspend fun resetToDrive(uploadLocalFirst: Boolean): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!source.isAvailable()) error("Not signed in to Drive")

                // Upload device-only books first so the reset loses nothing.
                if (uploadLocalFirst) {
                    val localOnly = bookDao.getAll().filter { it.remoteId == null && it.filePath != null }
                    localOnly.forEachIndexed { index, book ->
                        _uploads.value = UploadProgress(index + 1, localOnly.size, book.title, 0f)
                        val file = File(book.filePath!!)
                        val remote = source.uploadBook(file, book.title) { fraction ->
                            _uploads.value = UploadProgress(index + 1, localOnly.size, book.title, fraction)
                        }
                        // Promote the existing row to a remote-backed one in place.
                        bookDao.setRemote(book.id, remote.remoteId, remote.sizeBytes)
                    }
                    _uploads.value = null
                }

                // Drop anything that isn't in the Drive folder; sync the rest.
                val folderIds = source.listBooks().map { it.remoteId }.toSet()
                bookDao.getAll()
                    .filter { it.remoteId == null || it.remoteId !in folderIds }
                    .forEach { bookDao.deleteById(it.id) }

                sync().getOrThrow()
                bookDao.getAll().size
            }.also { _uploads.value = null }
        }

    /** Copies a SAF content Uri's bytes into book storage and returns the file. */
    private fun copyUriToStorage(uriString: String): File {
        val uri = Uri.parse(uriString)
        val target = File(booksDir(), "${UUID.randomUUID()}.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("Could not open $uriString")
        return target
    }

    /** Best-effort human name for an upload, from the content provider. */
    private fun displayName(uriString: String): String {
        val uri = Uri.parse(uriString)
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return (name ?: uri.lastPathSegment ?: "book").substringAfterLast('/')
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
