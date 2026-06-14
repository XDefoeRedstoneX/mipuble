package com.mipuble.data.remote

import com.mipuble.domain.model.RemoteBook
import java.io.File

/**
 * A backend that hosts EPUBs remotely. The repository talks to this interface,
 * so the real Google Drive source and the offline demo source are
 * interchangeable (and either can be faked in tests).
 */
interface RemoteLibrarySource {

    /** Whether the source can be used right now (configured, signed in, reachable). */
    suspend fun isAvailable(): Boolean

    /** Lists books in the library folder, metadata only — no file bytes transferred. */
    suspend fun listBooks(): List<RemoteBook>

    /** Cover image bytes for a remote book, if available. */
    suspend fun fetchCover(remoteId: String): ByteArray?

    /**
     * Streams the book's bytes into [target], reporting bytes read and the total
     * (null when the server doesn't send a length). Throws on failure (the
     * repository turns this into a Result).
     */
    suspend fun download(remoteId: String, target: File, onProgress: (read: Long, total: Long?) -> Unit)

    /**
     * Uploads [file] into the library folder under [displayName], reporting
     * progress in 0f..1f, and returns its remote handle.
     */
    suspend fun uploadBook(
        file: File,
        displayName: String,
        onProgress: (Float) -> Unit,
    ): RemoteBook

    /** Moves the remote book to the backend's trash (recoverable, not erased). */
    suspend fun trashBook(remoteId: String)
}
