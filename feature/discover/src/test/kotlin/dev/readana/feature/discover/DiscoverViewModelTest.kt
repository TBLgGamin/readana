package dev.readana.feature.discover

import dev.readana.core.common.result.AppError
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import dev.readana.core.testing.FakeBookSource
import dev.readana.core.testing.MainDispatcherRule
import dev.readana.source.api.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class DiscoverViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gutenberg = FakeBookSource(
        id = SourceId("gutenberg"),
        displayName = "Gutenberg",
        results = listOf(
            FakeBookSource.result("1342", "Pride and Prejudice", "Jane Austen", sourceId = SourceId("gutenberg"))
        )
    )
    private val broken =
        FakeBookSource(id = SourceId("broken"), displayName = "Broken", error = AppError.Http(503))
    private val settings = FakeSettingsRepository()

    private fun viewModel() = DiscoverViewModel(BookSourceRegistry(setOf(gutenberg, broken)), settings)

    @Test
    fun `search merges results and reports per-source errors`() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("pride")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasSearched)
        assertEquals(false, state.isSearching)
        assertEquals(listOf("Broken", "Gutenberg"), state.sourceNames)
        assertEquals(listOf("Pride and Prejudice"), state.results.map { it.title })
        assertEquals(listOf("Broken"), state.sourceErrors.map { it.sourceName })
        assertEquals("pride", gutenberg.queries.single().text)
    }

    @Test
    fun `disabled sources are skipped`() = runTest {
        settings.update { it.withSourceEnabled("broken", enabled = false) }
        val viewModel = viewModel()

        viewModel.onQueryChange("pride")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Gutenberg"), state.sourceNames)
        assertTrue(state.sourceErrors.isEmpty())
        assertEquals(1, state.results.size)
    }

    @Test
    fun `blank queries are ignored`() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("   ")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.hasSearched)
        assertTrue(gutenberg.queries.isEmpty())
    }

    @Test
    fun `download resolves through the owning source and emits a message`() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("pride")
        viewModel.search()
        advanceUntilIdle()

        viewModel.download(viewModel.uiState.value.results.single())
        advanceUntilIdle()

        val message = assertIs<DiscoverMessage.ReadyToDownload>(viewModel.uiState.value.message)
        assertEquals("https://example.test/1342.epub", message.url)

        viewModel.consumeMessage()
        assertEquals(null, viewModel.uiState.value.message)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val reader = MutableStateFlow(ReaderSettings())
    private val sources = MutableStateFlow(SourceSettings())
    private val folder = MutableStateFlow<String?>(null)

    fun update(transform: (SourceSettings) -> SourceSettings) = sources.update(transform)

    override val readerSettings: Flow<ReaderSettings> = reader

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = reader.update(transform)

    override val sourceSettings: Flow<SourceSettings> = sources

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) = sources.update(transform)

    override val libraryFolderUri: Flow<String?> = folder

    override suspend fun setLibraryFolderUri(uri: String?) {
        folder.value = uri
    }
}
