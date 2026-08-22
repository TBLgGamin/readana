package dev.readana.core.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        DownloadEntity::class,
        AiThreadEntity::class,
        AiMessageEntity::class,
        BookSettingsEntity::class,
        ReadingSessionEntity::class,
        CollectionEntity::class,
        CollectionBookEntity::class
    ],
    version = 11,
    exportSchema = true,
    // Every version only adds tables or nullable columns, so Room can write the migrations itself.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11)
    ]
)
abstract class ReadanaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun highlightDao(): HighlightDao

    abstract fun downloadDao(): DownloadDao

    abstract fun aiThreadDao(): AiThreadDao

    abstract fun bookSettingsDao(): BookSettingsDao

    abstract fun readingSessionDao(): ReadingSessionDao

    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "readana.db"
    }
}
