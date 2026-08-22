package dev.readana.feature.downloads

import app.cash.turbine.test
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.data.conversion.ConversionEvent
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.CapturedDownload
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.downloads.RetryOutcome
import dev.readana.core.data.messages.DefaultMessageBus
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import dev.readana.core.model.ReadingProgress
import dev.readana.core.testing.MainDispatcherRule
import dev.readana.engine.pdf2epub.ConversionPhase
import dev.readana.engine.pdf2epub.ConversionProgress
import dev.readana.source.api.SearchResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class DownloadsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val downloadManager = FakeBookDownloadManager()
    private val conversionManager = FakeConversionManager()
    private val library = FakeLibraryRepository()
    private val messageBus = DefaultMessageBus()

    @Test
    fun `orders the queue by what is happening now`() = runTest {
        downloadManager.setJobs(
            listOf(
                job("gone", DownloadStatus.Cancelled),
                job("failed", DownloadStatus.Failed),
                job("running", DownloadStatus.Downloading),
                job("waiting", DownloadStatus.Queued)
            )
        )
        val viewModel = viewModel()
        runCurrent()

        assertEquals(
            listOf("running", "waiting", "failed", "gone"),
            viewModel.uiState.value.downloadIds()
        )
    }

    @Test
    fun `counts each slice of the queue`() = runTest {
        downloadManager.setJobs(
            listOf(
                job("running", DownloadStatus.Downloading),
                job("held", DownloadStatus.Paused),
                job("failed", DownloadStatus.Failed),
                job("gone", DownloadStatus.Cancelled)
            )
        )
        val viewModel = viewModel()
        runCurrent()

        val tally = viewModel.uiState.value.tally
        assertEquals(4, tally.all)
        assertEquals(2, tally.active)
        assertEquals(2, tally.failed)
    }

    @Test
    fun `filtering narrows the list without losing the counts`() = runTest {
        downloadManager.setJobs(
            listOf(job("running", DownloadStatus.Downloading), job("failed", DownloadStatus.Failed))
        )
        val viewModel = viewModel()
        runCurrent()

        viewModel.onFilterChange(DownloadsFilter.Failed)

        val state = viewModel.uiState.value
        assertEquals(listOf("failed"), state.downloadIds())
        assertEquals(2, state.tally.all)
        assertEquals(DownloadsFilter.Failed, state.filter)
    }

    @Test
    fun `a filter matching nothing is not an empty queue`() = runTest {
        downloadManager.setJobs(listOf(job("running", DownloadStatus.Downloading)))
        val viewModel = viewModel()
        runCurrent()

        viewModel.onFilterChange(DownloadsFilter.Failed)

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertTrue(!state.queueIsEmpty)
    }

    @Test
    fun `summarises only what is in flight`() = runTest {
        downloadManager.setJobs(
            listOf(
                job("a", DownloadStatus.Downloading, bytes = 1_000, total = 4_000),
                job("b", DownloadStatus.Downloading, bytes = 2_000, total = 6_000),
                job("stopped", DownloadStatus.Failed, bytes = 9_000, total = 9_000)
            )
        )
        val viewModel = viewModel()
        runCurrent()

        val summary = checkNotNull(viewModel.uiState.value.summary)
        assertEquals(2, summary.activeCount)
        assertEquals(3_000, summary.bytesDownloaded)
        assertEquals(10_000, summary.totalBytes)
    }

    @Test
    fun `one job of unknown size leaves the whole total unknown`() = runTest {
        downloadManager.setJobs(
            listOf(
                job("a", DownloadStatus.Downloading, bytes = 1_000, total = 4_000),
                job("b", DownloadStatus.Downloading, bytes = 500, total = null)
            )
        )
        val viewModel = viewModel()
        runCurrent()

        val summary = checkNotNull(viewModel.uiState.value.summary)
        assertNull(summary.totalBytes)
    }

    @Test
    fun `a queue with nothing running has nothing to summarise`() = runTest {
        downloadManager.setJobs(listOf(job("failed", DownloadStatus.Failed)))
        val viewModel = viewModel()
        runCurrent()

        assertNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `a converting book shares the queue with the downloads`() = runTest {
        library.setBooks(listOf(book("book-1", "Moby-Dick")))
        downloadManager.setJobs(listOf(job("running", DownloadStatus.Downloading)))
        conversionManager.setActive(
            mapOf("book-1" to ConversionProgress(ConversionPhase.Extracting, currentPage = 4, totalPages = 20))
        )
        val viewModel = viewModel()
        runCurrent()

        val conversion = viewModel.uiState.value.items
            .filterIsInstance<QueueItem.Conversion>()
            .single()
            .conversion
        assertEquals("Moby-Dick", conversion.title)
        assertEquals(4, conversion.currentPage)
        // It leads, and it counts towards what is in flight.
        assertIs<QueueItem.Conversion>(viewModel.uiState.value.items.first())
        assertEquals(2, viewModel.uiState.value.tally.active)
    }

    @Test
    fun `a conversion started by a download rides inside that download's card`() = runTest {
        library.setBooks(listOf(book("book-1", "Moby-Dick")))
        downloadManager.setJobs(
            listOf(job("running", DownloadStatus.Converting).copy(bookId = "book-1"))
        )
        conversionManager.setActive(
            mapOf("book-1" to ConversionProgress(ConversionPhase.Reflowing, currentPage = 4, totalPages = 20))
        )
        val viewModel = viewModel()
        runCurrent()

        // One row, not two: the conversion is a later step of the same book.
        val download = assertIs<QueueItem.Download>(viewModel.uiState.value.items.single())
        assertEquals("book-1", download.conversion?.bookId)
        assertEquals(1, viewModel.uiState.value.tally.all)
    }

    @Test
    fun `a conversion is left out of the slices it cannot belong to`() = runTest {
        library.setBooks(listOf(book("book-1", "Moby-Dick")))
        downloadManager.setJobs(listOf(job("failed", DownloadStatus.Failed)))
        conversionManager.setActive(
            mapOf("book-1" to ConversionProgress(ConversionPhase.Extracting, currentPage = 4, totalPages = 20))
        )
        val viewModel = viewModel()
        runCurrent()

        viewModel.onFilterChange(DownloadsFilter.Failed)

        assertTrue(viewModel.uiState.value.items.none { it is QueueItem.Conversion })
    }

    @Test
    fun `pausing and resuming reach the manager`() = runTest {
        val viewModel = viewModel()

        viewModel.pause("job-1")
        viewModel.resume("job-2")
        viewModel.cancel("job-3")
        viewModel.dismiss("job-4")
        runCurrent()

        assertEquals(listOf("job-1"), downloadManager.paused)
        assertEquals(listOf("job-2"), downloadManager.resumed)
        assertEquals(listOf("job-3"), downloadManager.cancelled)
        assertEquals(listOf("job-4"), downloadManager.dismissed)
    }

    @Test
    fun `a retry that needs the browser becomes a navigation effect`() = runTest {
        downloadManager.retryOutcome = RetryOutcome.NeedsBrowser(browserDownload())
        val viewModel = viewModel()

        viewModel.retry("job-1")
        runCurrent()

        val effect = assertIs<DownloadsEffect.OpenBrowser>(viewModel.uiState.value.effect)
        assertEquals("https://one.test/md5/md5abc", effect.download.url)

        viewModel.consumeEffect()
        assertNull(viewModel.uiState.value.effect)
    }

    @Test
    fun `retrying everything stops at the job that wants the browser`() = runTest {
        downloadManager.setJobs(
            listOf(
                job("first", DownloadStatus.Failed),
                job("second", DownloadStatus.Failed),
                job("third", DownloadStatus.Cancelled)
            )
        )
        downloadManager.retryOutcomes = mapOf("second" to RetryOutcome.NeedsBrowser(browserDownload()))
        val viewModel = viewModel()
        runCurrent()

        viewModel.retryAll()
        runCurrent()

        assertEquals(listOf("first", "second"), downloadManager.retried)
        assertIs<DownloadsEffect.OpenBrowser>(viewModel.uiState.value.effect)
    }

    @Test
    fun `a failed retry is reported`() = runTest {
        downloadManager.retryOutcome = RetryOutcome.Failed(AppError.Unexpected("no route"))
        val viewModel = viewModel()
        val sent = record(messageBus)

        viewModel.retry("job-1")
        runCurrent()

        assertEquals(
            R.string.feature_downloads_message_retry_failed,
            assertIs<UiMessage.Text>(sent.single()).id
        )
    }

    @Test
    fun `clearing the list leaves the queue itself alone`() = runTest {
        downloadManager.setJobs(
            listOf(
                job("running", DownloadStatus.Downloading),
                job("failed", DownloadStatus.Failed),
                job("gone", DownloadStatus.Cancelled)
            )
        )
        val viewModel = viewModel()
        runCurrent()

        val sent = record(messageBus)
        viewModel.clearFinished()
        runCurrent()

        assertEquals(listOf("running"), viewModel.uiState.value.downloadIds())
        assertEquals(
            UiMessage.Quantity(R.plurals.feature_downloads_message_cleared, 2, listOf(2)),
            sent.single()
        )
    }

    private fun viewModel() = DownloadsViewModel(downloadManager, messageBus, conversionManager, library)

    private fun DownloadsUiState.downloadIds() = items.filterIsInstance<QueueItem.Download>().map { it.job.id }

    private fun browserDownload() = BrowserDownload(
        sourceId = "annas-archive",
        recordId = "md5abc",
        title = "Pride and Prejudice",
        authors = listOf("Jane Austen"),
        format = BookFormat.EPUB,
        url = "https://one.test/md5/md5abc",
        loginUrl = null,
        loginFormBody = null
    )

    private fun book(id: String, title: String) = Book(
        id = id,
        title = title,
        authors = listOf("Herman Melville"),
        language = null,
        format = BookFormat.EPUB,
        fileUri = "file:///books/" + id + ".epub",
        coverUri = null,
        sizeBytes = 1,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )

    private var createdAt = 0L

    private fun job(
        id: String,
        status: DownloadStatus,
        bytes: Long = 0,
        total: Long? = null,
        errorKind: DownloadErrorKind? = null
    ) = DownloadJob(
        id = id,
        sourceId = "annas-archive",
        recordId = id,
        title = "Pride and Prejudice",
        authors = listOf("Jane Austen"),
        format = BookFormat.EPUB,
        status = status,
        bytesDownloaded = bytes,
        totalBytes = total,
        errorKind = errorKind,
        // Ascending, so "oldest first inside a group" is testable without a clock.
        createdAt = Instant.ofEpochMilli(createdAt++)
    )
}

private class FakeBookDownloadManager : BookDownloadManager {
    val retried = mutableListOf<String>()
    val paused = mutableListOf<String>()
    val resumed = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    val dismissed = mutableListOf<String>()
    var retryOutcome: RetryOutcome = RetryOutcome.Requeued
    var retryOutcomes: Map<String, RetryOutcome> = emptyMap()
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())

    override val jobs: Flow<List<DownloadJob>> = _jobs

    fun setJobs(value: List<DownloadJob>) {
        _jobs.value = value
    }

    override suspend fun enqueue(result: SearchResult): EnqueueOutcome =
        EnqueueOutcome.Failed(AppError.Unexpected("not used"))

    override suspend fun enqueueCaptured(capture: CapturedDownload): EnqueueOutcome =
        EnqueueOutcome.Failed(AppError.Unexpected("not used"))

    override suspend fun retry(jobId: String): RetryOutcome {
        retried += jobId
        return retryOutcomes[jobId] ?: retryOutcome
    }

    override suspend fun pause(jobId: String) {
        paused += jobId
    }

    override suspend fun resume(jobId: String) {
        resumed += jobId
    }

    override suspend fun cancel(jobId: String) {
        cancelled += jobId
    }

    override suspend fun dismiss(jobId: String) {
        dismissed += jobId
    }

    override suspend fun dismissFinished() {
        _jobs.value = _jobs.value.filter { it.isPending }
    }
}

private class FakeConversionManager : ConversionManager {
    private val _active = MutableStateFlow<Map<String, ConversionProgress>>(emptyMap())
    override val active: StateFlow<Map<String, ConversionProgress>> = _active

    override val events: SharedFlow<ConversionEvent> = MutableSharedFlow()

    fun setActive(value: Map<String, ConversionProgress>) {
        _active.value = value
    }

    override fun enqueue(bookId: String) = Unit

    override suspend fun convertAndWait(bookId: String): AppResult<Book> = AppError.Unexpected("not used").asFailure()
}

private class FakeLibraryRepository : LibraryRepository {
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    override val books: Flow<List<Book>> = _books

    fun setBooks(value: List<Book>) {
        _books.value = value
    }

    override fun observeBook(id: String): Flow<Book?> = _books.map { saved -> saved.firstOrNull { it.id == id } }

    override suspend fun getBook(id: String): Book? = _books.value.firstOrNull { it.id == id }

    override suspend fun findDuplicate(title: String, sizeBytes: Long): Book? = null

    override suspend fun findBySource(sourceId: String, recordId: String): Book? = null

    override suspend fun upsert(book: Book) = Unit

    override suspend fun remove(id: String) = Unit

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) = Unit

    override suspend fun setFinished(id: String, finished: Boolean) = Unit
}

/**
 * The real bus, recording what went through it.
 *
 * It has no dependencies of its own, so there is nothing to fake: collecting from it is exactly
 * what the app shell does.
 */
private fun TestScope.record(bus: MessageBus): List<UiMessage> {
    val seen = mutableListOf<UiMessage>()
    // Unconfined so the collector is subscribed the moment this returns: the bus buffers rather
    // than replays, and a message sent before anyone is listening is simply gone.
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        bus.messages.collect { seen += it }
    }
    return seen
}
