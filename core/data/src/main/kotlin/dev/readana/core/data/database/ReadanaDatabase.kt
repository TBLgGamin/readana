package dev.readana.core.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true,
    // Version 2 only adds the bookmarks table, so Room can write the migration itself.
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class ReadanaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val NAME = "readana.db"
    }
}
