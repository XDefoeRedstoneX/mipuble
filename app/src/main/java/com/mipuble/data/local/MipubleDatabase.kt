package com.mipuble.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, CategoryEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class MipubleDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        /** Adds the file/cover/reading-position columns introduced in Phase 2. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN file_path TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN cover_path TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN last_chapter_index INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds categories and custom ordering (Phase 4). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "color_argb INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE books ADD COLUMN category_id INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN custom_order INTEGER NOT NULL DEFAULT 0")
                // Seed each book's custom slot with its id so the existing
                // shelf order is stable until the user rearranges it.
                db.execSQL("UPDATE books SET custom_order = id")
            }
        }

        /** Adds remote-library fields for download-on-demand (Phase 5). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN remote_id TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN remote_size_bytes INTEGER")
            }
        }
    }
}
