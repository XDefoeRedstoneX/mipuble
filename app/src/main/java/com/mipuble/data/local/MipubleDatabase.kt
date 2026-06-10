package com.mipuble.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BookEntity::class], version = 1, exportSchema = false)
abstract class MipubleDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
