package dev.readana.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.readana.core.data.datastore.SettingsKeys
import dev.readana.core.data.datastore.toConversionSettings
import dev.readana.core.data.datastore.toReaderSettings
import dev.readana.core.data.datastore.toSourceSettings
import dev.readana.core.data.datastore.write
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val readerSettings: Flow<ReaderSettings>

    suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings)

    val sourceSettings: Flow<SourceSettings>

    suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings)

    val conversionSettings: Flow<ConversionSettings>

    suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings)

    /** Persisted SAF tree URI of the user-chosen library folder, or null for app-private storage. */
    val libraryFolderUri: Flow<String?>

    suspend fun setLibraryFolderUri(uri: String?)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    SettingsRepository {
    override val readerSettings: Flow<ReaderSettings> =
        dataStore.data.map { it.toReaderSettings() }.distinctUntilChanged()

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        dataStore.edit { preferences ->
            preferences.write(transform(preferences.toReaderSettings()))
        }
    }

    override val sourceSettings: Flow<SourceSettings> =
        dataStore.data.map { it.toSourceSettings() }.distinctUntilChanged()

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) {
        dataStore.edit { preferences ->
            preferences.write(transform(preferences.toSourceSettings()))
        }
    }

    override val conversionSettings: Flow<ConversionSettings> =
        dataStore.data.map { it.toConversionSettings() }.distinctUntilChanged()

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) {
        dataStore.edit { preferences ->
            preferences.write(transform(preferences.toConversionSettings()))
        }
    }

    override val libraryFolderUri: Flow<String?> =
        dataStore.data.map { it[SettingsKeys.LIBRARY_FOLDER_URI] }.distinctUntilChanged()

    override suspend fun setLibraryFolderUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri ==
                null
            ) {
                preferences.remove(SettingsKeys.LIBRARY_FOLDER_URI)
            } else {
                preferences[SettingsKeys.LIBRARY_FOLDER_URI] =
                    uri
            }
        }
    }
}
