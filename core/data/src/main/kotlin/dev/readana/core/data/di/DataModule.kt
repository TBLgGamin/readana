package dev.readana.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.repository.DataStoreSettingsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.RoomLibraryRepository
import dev.readana.core.data.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindsLibraryRepository(impl: RoomLibraryRepository): LibraryRepository

    @Binds
    abstract fun bindsSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
