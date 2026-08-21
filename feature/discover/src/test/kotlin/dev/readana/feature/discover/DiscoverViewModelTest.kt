package dev.readana.feature.discover

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.CapturedDownload
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.downloads.RetryOutcome
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import dev.readana.core.testing.FakeBookSource
import dev.readana.core.testing.MainDispatcherRule
import dev.readana.source.api.BrowserLogin
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import dev.readana.source.trending.TrendingBook
import dev.readana.source.trending.TrendingBooks
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
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
    private val downloads = FakeBookDownloadManager()

    private val trending = FakeTrendingBooks()

    private fun viewModel() =
        DiscoverViewModel(BookSourceRegistry(setOf(gutenberg, broken)), settings, downloads, trending)

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
    fun `downloading queues the book and reports the remaining quota`() = runTest {
        downloads.outcome = { EnqueueOutcome.Enqueued(job(it.title), fastDownloadsLeft = 4) }
        val viewModel = searchedViewModel()

        viewModel.download(viewModel.uiState.value.results.single())
        advanceUntilIdle()

        val message = assertIs<DiscoverMessage.Queued>(viewModel.uiState.value.message)
        assertEquals("Pride and Prejudice", message.title)
        assertEquals(4, message.fastDownloadsLeft)
        assertEquals(listOf("1342"), downloads.enqueued.map { it.recordId })

        viewModel.consumeMessage()
        assertEquals(null, viewModel.uiState.value.message)
    }

    @Test
    fun `a book already in the library is reported instead of queued`() = runTest {
        downloads.outcome = { EnqueueOutcome.AlreadyInLibrary(book()) }
        val viewModel = searchedViewModel()

        viewModel.download(viewModel.uiState.value.results.single())
        advanceUntilIdle()

        val message = assertIs<DiscoverMessage.AlreadyInLibrary>(viewModel.uiState.value.message)
        assertEquals("Pride and Prejudice", message.title)
    }

    @Test
    fun `a source that needs a browser asks to navigate there`() = runTest {
        downloads.outcome = { result ->
            EnqueueOutcome.RequiresBrowser(
                result = result,
                url = "https://one.test/md5/abc",
                login = BrowserLogin("https://one.test/account/", "key=s3cr3t"),
                quotaExhausted = true
            )
        }
        val viewModel = searchedViewModel()

        viewModel.download(viewModel.uiState.value.results.single())
        advanceUntilIdle()

        val effect = assertIs<DiscoverEffect.OpenBrowser>(viewModel.uiState.value.effect)
        assertEquals("https://one.test/md5/abc", effect.download.url)
        assertEquals("https://one.test/account/", effect.download.loginUrl)
        assertEquals("key=s3cr3t", effect.download.loginFormBody)
        assertEquals("1342", effect.download.recordId)

        viewModel.consumeEffect()
        assertEquals(null, viewModel.uiState.value.effect)
    }

    @Test
    fun `a failing enqueue surfaces the error`() = runTest {
        downloads.outcome = { EnqueueOutcome.Failed(AppError.Unauthorized("bad key")) }
        val viewModel = searchedViewModel()

        viewModel.download(viewModel.uiState.value.results.single())
        advanceUntilIdle()

        assertIs<DiscoverMessage.Failed>(viewModel.uiState.value.message)
    }

    @Test
    fun `popular books fill the screen before anything is searched`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.hasSearched)
        assertEquals(listOf("Atomic Habits"), state.trending.map { it.title })
    }

    @Test
    fun `tapping a popular book searches the sources for it`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.searchFor(viewModel.uiState.value.trending.single())
        advanceUntilIdle()

        assertEquals("Atomic Habits James Clear", gutenberg.queries.single().text)
        assertTrue(viewModel.uiState.value.hasSearched)
    }

    @Test
    fun `a failing popular shelf leaves search working`() = runTest {
        trending.failure = AppError.Network("offline")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.trending.isEmpty())

        viewModel.onQueryChange("pride")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.results.size)
    }

    private fun TestScope.searchedViewModel(): DiscoverViewModel {
        val viewModel = viewModel()
        viewModel.onQueryChange("pride")
        viewModel.search()
        advanceUntilIdle()
        return viewModel
    }

    private fun job(title: String) = DownloadJob(
        id = "job-1",
        sourceId = "gutenberg",
        recordId = "1342",
        title = title,
        format = BookFormat.EPUB,
        status = DownloadStatus.Queued
    )

    private fun book() = Book(
        id = "book-1",
        title = "Pride and Prejudice",
        authors = listOf("Jane Austen"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/pride.epub",
        coverUri = null,
        sizeBytes = 10,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )
}

private class FakeTrendingBooks : TrendingBooks {
    var failure: AppError? = null

    override suspend fun trending(limit: Int): AppResult<List<TrendingBook>> = failure?.asFailure() ?: listOf(
        TrendingBook(
            title = "Atomic Habits",
            authors = listOf("James Clear"),
            coverUrl = "https://covers.example/atomic.jpg"
        )
    ).asSuccess()
}

private class FakeBookDownloadManager : BookDownloadManager {
    val enqueued = mutableListOf<SearchResult>()
    var outcome: (SearchResult) -> EnqueueOutcome = { EnqueueOutcome.Failed(AppError.Unexpected("unset")) }

    override val jobs: Flow<List<DownloadJob>> = MutableStateFlow(emptyList())

    override suspend fun enqueue(result: SearchResult): EnqueueOutcome {
        enqueued += result
        return outcome(result)
    }

    override suspend fun enqueueCaptured(capture: CapturedDownload): EnqueueOutcome =
        EnqueueOutcome.Failed(AppError.Unexpected("not used"))

    override suspend fun retry(jobId: String): RetryOutcome = RetryOutcome.Requeued

    override suspend fun cancel(jobId: String) = Unit

    override suspend fun dismiss(jobId: String) = Unit
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

    override val conversionSettings: Flow<ConversionSettings> = MutableStateFlow(ConversionSettings())

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) = Unit

    override val libraryFolderUri: Flow<String?> = folder

    override suspend fun setLibraryFolderUri(uri: String?) {
        folder.value = uri
    }
}
