package dev.readana.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.database.AiThreadDao
import dev.readana.core.data.database.BookDao
import dev.readana.core.data.database.BookSettingsDao
import dev.readana.core.data.database.BookmarkDao
import dev.readana.core.data.database.CollectionDao
import dev.readana.core.data.database.DownloadDao
import dev.readana.core.data.database.HighlightDao
import dev.readana.core.data.database.InkStrokeDao
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.data.database.ReadingSessionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): ReadanaDatabase =
        Room.databaseBuilder(context, ReadanaDatabase::class.java, ReadanaDatabase.NAME).build()

    @Provides
    fun providesBookDao(database: ReadanaDatabase): BookDao = database.bookDao()

    @Provides
    fun providesBookmarkDao(database: ReadanaDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun providesHighlightDao(database: ReadanaDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun providesInkStrokeDao(database: ReadanaDatabase): InkStrokeDao = database.inkStrokeDao()

    @Provides
    fun providesDownloadDao(database: ReadanaDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun providesAiThreadDao(database: ReadanaDatabase): AiThreadDao = database.aiThreadDao()

    @Provides
    fun providesBookSettingsDao(database: ReadanaDatabase): BookSettingsDao = database.bookSettingsDao()

    @Provides
    fun providesReadingSessionDao(database: ReadanaDatabase): ReadingSessionDao = database.readingSessionDao()

    @Provides
    fun providesCollectionDao(database: ReadanaDatabase): CollectionDao = database.collectionDao()
}
