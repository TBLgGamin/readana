package dev.readana.core.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        DownloadEntity::class
    ],
    version = 4,
    exportSchema = true,
    // Each version only adds a table, so Room can write the migrations itself.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class ReadanaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun highlightDao(): HighlightDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "readana.db"
    }
}
