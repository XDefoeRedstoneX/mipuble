package com.mipuble.domain.repository

import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.model.UploadProgress
import com.mipuble.domain.model.UploadSummary
import kotlinx.coroutines.flow.Flow

/**
 * The storage-efficiency feature: a library whose books live remotely and are
 * pulled down only on demand. Syncing brings *metadata only* (title, author,
 * cover) into the local library, so a multi-gigabyte collection costs almost
 * nothing on the device until a book is actually opened.
 */
interface RemoteLibraryRepository {

    /** Live per-book download progress, keyed by local book id. */
    val downloads: Flow<Map<Long, DownloadStatus>>

    /** Progress of an in-flight upload batch; null when idle. */
    val uploads: Flow<UploadProgress?>

    /** Whether a remote source is configured/available (e.g. signed in). */
    suspend fun isAvailable(): Boolean

    /** Pulls remote metadata into the local library. Returns how many were new. */
    suspend fun sync(): Result<Int>

    /** Downloads a remote book's bytes on demand, reporting progress via [downloads]. */
    suspend fun download(bookId: Long): Result<Unit>

    /** Deletes a downloaded book's local file, keeping its metadata. */
    suspend fun evict(bookId: Long): Result<Unit>

    /**
     * Uploads local EPUBs (content-Uri strings) into the Drive folder, keeping
     * a readable local copy. Books already present (by content or name) are
     * skipped. Returns the added/skipped tally.
     */
    suspend fun uploadBooks(uriStrings: List<String>): Result<UploadSummary>

    /**
     * Recursively finds every EPUB under a picked folder (SAF tree Uri) and
     * uploads them, skipping duplicates. Returns the added/skipped tally.
     */
    suspend fun uploadFolder(treeUriString: String): Result<UploadSummary>

    /**
     * Makes the local library mirror the Drive folder. When [uploadLocalFirst]
     * is true, books that exist only on the device are uploaded before the
     * local rows are reconciled, so nothing is lost. Progress/category/order
     * are preserved by Drive file id. Returns the resulting book count.
     */
    suspend fun resetToDrive(uploadLocalFirst: Boolean): Result<Int>

    /**
     * Removes a book from the library (deletes its local file + cover). When
     * [alsoFromDrive] is true and the book is remote, its Drive copy is moved
     * to trash (recoverable).
     */
    suspend fun deleteBook(bookId: Long, alsoFromDrive: Boolean): Result<Unit>
}
