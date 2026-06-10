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
)
