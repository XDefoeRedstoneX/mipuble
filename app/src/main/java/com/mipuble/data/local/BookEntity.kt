package com.mipuble.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mipuble.domain.model.Book

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    val progress: Float = 0f,
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    @ColumnInfo(name = "cover_path") val coverPath: String? = null,
    @ColumnInfo(name = "last_chapter_index") val lastChapterIndex: Int = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "custom_order") val customOrder: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "remote_size_bytes") val remoteSizeBytes: Long? = null,
)

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    addedAtEpochMillis = addedAt,
    progress = progress,
    filePath = filePath,
    coverPath = coverPath,
    lastChapterIndex = lastChapterIndex,
    categoryId = categoryId,
    customOrder = customOrder,
    remoteId = remoteId,
    remoteSizeBytes = remoteSizeBytes,
)
