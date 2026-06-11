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

    /** Lists books with metadata only — no file bytes are transferred. */
    suspend fun listBooks(): List<RemoteBook>

    /** Cover image bytes for a remote book, if available. */
    suspend fun fetchCover(remoteId: String): ByteArray?

    /**
     * Streams the book's bytes into [target], reporting progress as a fraction
     * in 0f..1f. Throws on failure (the repository turns this into a Result).
     */
    suspend fun download(remoteId: String, target: File, onProgress: (Float) -> Unit)
}
