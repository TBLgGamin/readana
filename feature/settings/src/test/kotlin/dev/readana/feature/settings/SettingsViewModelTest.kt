package dev.readana.feature.settings

import app.cash.turbine.test
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiSettings
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import dev.readana.core.testing.FakeBookSource
import dev.readana.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val registry = BookSourceRegistry(setOf(FakeBookSource()))

    private fun viewModel() = SettingsViewModel(settings, registry)

    @Test
    fun `exposes reader settings, sources and the library folder`() = runTest {
        settings.setLibraryFolderUri("content://tree/primary%3ABooks")
        val viewModel = viewModel()

        viewModel.uiState.test {
            val loaded = assertIs<SettingsUiState.Loaded>(awaitItem())
            assertEquals("content://tree/primary%3ABooks", loaded.libraryFolderUri)
            assertEquals(listOf("Fake Source"), loaded.sourceToggles.map { it.name })
        }
    }

    @Test
    fun `stores and clears the library folder`() = runTest {
        val viewModel = viewModel()

        viewModel.setLibraryFolder("content://tree/sdcard")
        assertEquals("content://tree/sdcard", settings.libraryFolderNow())

        viewModel.setLibraryFolder(null)
        assertNull(settings.libraryFolderNow())
    }

    @Test
    fun `writes conversion settings`() = runTest {
        val viewModel = viewModel()

        viewModel.setAutoConvertPdf(false)
        viewModel.setRemoveHeadersAndFooters(false)
        viewModel.setHeadingSensitivity(HeadingSensitivity.Aggressive)
        viewModel.setExtractImages(false)

        val conversion = settings.conversionNow()
        assertEquals(false, conversion.autoConvertPdf)
        assertEquals(false, conversion.removeHeadersAndFooters)
        assertEquals(HeadingSensitivity.Aggressive, conversion.headingSensitivity)
        assertEquals(false, conversion.extractImages)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val ai = MutableStateFlow(AiSettings())
    private val reader = MutableStateFlow(ReaderSettings())
    private val sources = MutableStateFlow(SourceSettings())
    private val conversion = MutableStateFlow(ConversionSettings())
    private val libraryFolder = MutableStateFlow<String?>(null)

    override val readerSettings: Flow<ReaderSettings> = reader

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = reader.update(transform)

    override val sourceSettings: Flow<SourceSettings> = sources

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) = sources.update(transform)

    override val conversionSettings: Flow<ConversionSettings> = conversion

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) =
        conversion.update(transform)

    override val aiSettings: Flow<AiSettings> = ai

    override suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings) = ai.update(transform)

    override val libraryFolderUri: Flow<String?> = libraryFolder

    override suspend fun setLibraryFolderUri(uri: String?) {
        libraryFolder.value = uri
    }

    fun readerNow(): ReaderSettings = reader.value

    fun conversionNow(): ConversionSettings = conversion.value

    fun libraryFolderNow(): String? = libraryFolder.value
}
