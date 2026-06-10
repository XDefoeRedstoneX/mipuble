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
)
