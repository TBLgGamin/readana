package dev.readana.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.BookMetadataExtractor
import dev.readana.core.data.importing.DefaultBookImportManager
import dev.readana.core.data.importing.DefaultBookImporter
import dev.readana.core.data.importing.ReadiumMetadataExtractor
import dev.readana.core.data.repository.BookmarksRepository
import dev.readana.core.data.repository.DataStoreSettingsRepository
import dev.readana.core.data.repository.HighlightsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.RoomBookmarksRepository
import dev.readana.core.data.repository.RoomHighlightsRepository
import dev.readana.core.data.repository.RoomLibraryRepository
import dev.readana.core.data.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindsLibraryRepository(impl: RoomLibraryRepository): LibraryRepository

    @Binds
    abstract fun bindsSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindsBookmarksRepository(impl: RoomBookmarksRepository): BookmarksRepository

    @Binds
    abstract fun bindsHighlightsRepository(impl: RoomHighlightsRepository): HighlightsRepository

    @Binds
    abstract fun bindsBookMetadataExtractor(impl: ReadiumMetadataExtractor): BookMetadataExtractor

    @Binds
    abstract fun bindsBookImporter(impl: DefaultBookImporter): BookImporter

    @Binds
    abstract fun bindsBookImportManager(impl: DefaultBookImportManager): BookImportManager
}
