package com.mipuble.domain.model

/** Live status of an on-demand download, keyed by book id in the repository. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(val fraction: Float) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

/** A book listed in the remote library, before any bytes are downloaded. */
data class RemoteBook(
    val remoteId: String,
    val title: String,
    val author: String,
    val sizeBytes: Long,
)

/** Progress of an in-flight upload batch (or a folder scan); null when idle. */
data class UploadProgress(
    val currentIndex: Int,
    val total: Int,
    val fileName: String,
    val fraction: Float,
    /** True while a picked folder is being scanned, before any upload starts. */
    val scanning: Boolean = false,
)
