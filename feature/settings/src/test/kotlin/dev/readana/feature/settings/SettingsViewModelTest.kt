package dev.readana.feature.settings

import app.cash.turbine.test
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
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
    fun `writes reader preferences through the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.setTheme(ReaderTheme.Sepia)
        viewModel.setKeepScreenOn(false)

        assertEquals(ReaderTheme.Sepia, settings.readerNow().theme)
        assertEquals(false, settings.readerNow().keepScreenOn)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val reader = MutableStateFlow(ReaderSettings())
    private val sources = MutableStateFlow(SourceSettings())
    private val libraryFolder = MutableStateFlow<String?>(null)

    override val readerSettings: Flow<ReaderSettings> = reader

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = reader.update(transform)

    override val sourceSettings: Flow<SourceSettings> = sources

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) = sources.update(transform)

    override val libraryFolderUri: Flow<String?> = libraryFolder

    override suspend fun setLibraryFolderUri(uri: String?) {
        libraryFolder.value = uri
    }

    fun readerNow(): ReaderSettings = reader.value

    fun libraryFolderNow(): String? = libraryFolder.value
}
