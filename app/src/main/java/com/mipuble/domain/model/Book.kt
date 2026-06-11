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
) {
    /** Whether the book can actually be opened in the reader. */
    val isDownloaded: Boolean get() = filePath != null
}
