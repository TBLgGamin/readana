package dev.readana.core.data

import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiSettings
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory settings for tests that only care about a couple of values. */
class FakeSettingsRepository(
    reader: ReaderSettings = ReaderSettings(),
    sources: SourceSettings = SourceSettings(),
    conversion: ConversionSettings = ConversionSettings(),
    ai: AiSettings = AiSettings(),
    libraryFolder: String? = null
) : SettingsRepository {
    private val reader = MutableStateFlow(reader)
    private val sources = MutableStateFlow(sources)
    private val conversion = MutableStateFlow(conversion)
    private val ai = MutableStateFlow(ai)
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

    override val conversionSettings: Flow<ConversionSettings> = this.conversion

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) {
        conversion.update(transform)
    }

    override val aiSettings: Flow<AiSettings> = this.ai

    /** Latest value, for tests asserting on what was written. */
    val aiSettingsNow: AiSettings get() = ai.value

    override suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings) {
        ai.update(transform)
    }

    override val libraryFolderUri: Flow<String?> = this.libraryFolder

    override suspend fun setLibraryFolderUri(uri: String?) {
        libraryFolder.value = uri
    }
}
