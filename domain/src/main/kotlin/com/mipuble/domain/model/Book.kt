package com.mipuble.domain.model

/**
 * A book as the rest of the app sees it. Deliberately knows nothing about
 * Room, files, or the network — those concerns live in the data layer.
 */
data class Book(
    val id: Long,
    val title: String,
    val author: String,
    val addedAtEpochMillis: Long,
    /** Reading progress in 0f..1f; 0f means unopened. */
    val progress: Float,
    /** Local EPUB path, or null when the book's bytes aren't on the device yet. */
    val filePath: String? = null,
    /** Local cover image path, or null to fall back to a generated placeholder. */
    val coverPath: String? = null,
    /** Spine index to resume at when reopening. */
    val lastChapterIndex: Int = 0,
    /** The user-defined category (shelf) this book belongs to, if any. */
    val categoryId: Long? = null,
    /** Position in the user's hand-arranged order; ties broken by id. */
    val customOrder: Long = 0,
    /** Identifier in the remote library (e.g. a Drive file id); null if local-only. */
    val remoteId: String? = null,
    /** Size of the remote file in bytes, for display before downloading. */
    val remoteSizeBytes: Long? = null,
) {
    /** Whether the book's bytes are on the device and it can be opened. */
    val isDownloaded: Boolean get() = filePath != null

    /** Whether the book exists in the remote library. */
    val isRemote: Boolean get() = remoteId != null

    /** A downloaded remote book can be evicted to reclaim space (metadata stays). */
    val canEvict: Boolean get() = filePath != null && remoteId != null
}
