package com.mipuble.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class], version = 2, exportSchema = false)
abstract class MipubleDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        /** Adds the file/cover/reading-position columns introduced in Phase 2. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN file_path TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN cover_path TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN last_chapter_index INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
