package dev.readana.core.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, HighlightEntity::class],
    version = 3,
    exportSchema = true,
    // Each version only adds a table, so Room can write the migrations itself.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
)
abstract class ReadanaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun highlightDao(): HighlightDao

    companion object {
        const val NAME = "readana.db"
    }
}
