package dev.readana.core.data

import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory settings for tests that only care about a couple of values. */
class FakeSettingsRepository(
    reader: ReaderSettings = ReaderSettings(),
    sources: SourceSettings = SourceSettings(),
    libraryFolder: String? = null
) : SettingsRepository {
    private val reader = MutableStateFlow(reader)
    private val sources = MutableStateFlow(sources)
    private val libraryFolder = MutableStateFlow(libraryFolder)

    override val readerSettings: Flow<ReaderSettings> = this.reader

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        reader.update(transform)
    }

    override val sourceSettings: Flow<SourceSettings> = this.sources

    /** Latest value, for tests asserting on what was written. */
    val sourceSettingsNow: SourceSettings get() = sources.value

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) {
        sources.update(transform)
    }

    override val libraryFolderUri: Flow<String?> = this.libraryFolder

    override suspend fun setLibraryFolderUri(uri: String?) {
        libraryFolder.value = uri
    }
}
