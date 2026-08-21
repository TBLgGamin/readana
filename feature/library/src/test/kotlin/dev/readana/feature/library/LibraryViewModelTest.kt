package dev.readana.feature.library

import app.cash.turbine.test
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.conversion.ConversionEvent
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.CapturedDownload
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.downloads.RetryOutcome
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportEvent
import dev.readana.core.data.importing.ImportResult
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.BrowserDownload
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val importManager = FakeBookImportManager()
    private val importer = FakeBookImporter()
    private val downloadManager = FakeBookDownloadManager()
    private val conversionManager = FakeConversionManager()

    private fun viewModel() = LibraryViewModel(repository, importManager, importer, downloadManager, conversionManager)

    @Test
    fun `shows a loading state until the first books arrive`() {
        assertTrue(LibraryUiState().isLoading)
    }

    @Test
    fun `reflects the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val empty = awaitItem()
            assertTrue(empty.libraryIsEmpty)
            assertTrue(empty.books.isEmpty())

            repository.upsert(book("1"))
            assertEquals(listOf("1"), awaitItem().books.map { it.id })

            repository.remove("1")
            assertTrue(awaitItem().libraryIsEmpty)
        }
    }

    @Test
    fun `orders by most recently read by default`() = runTest {
        repository.upsert(book("old", title = "Aardvark", addedAt = 1_000))
        repository.upsert(book("recent", title = "Zebra", addedAt = 500, lastOpenedAt = 9_000))
        val viewModel = viewModel()

        assertEquals(listOf("recent", "old"), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `sorts by title and by author on request`() = runTest {
        repository.upsert(book("1", title = "Zebra", author = "Ann Author"))
        repository.upsert(book("2", title = "aardvark", author = "Zoe Writer"))
        val viewModel = viewModel()

        viewModel.onSortChange(LibrarySort.Title)
        assertEquals(listOf("2", "1"), viewModel.uiState.value.books.map { it.id })

        viewModel.onSortChange(LibrarySort.Author)
        assertEquals(listOf("1", "2"), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `searches title and author, ignoring case`() = runTest {
        repository.upsert(book("1", title = "Moby Dick", author = "Herman Melville"))
        repository.upsert(book("2", title = "Frankenstein", author = "Mary Shelley"))
        val viewModel = viewModel()

        viewModel.onQueryChange("moby")
        assertEquals(listOf("1"), viewModel.uiState.value.books.map { it.id })

        viewModel.onQueryChange("SHELLEY")
        assertEquals(listOf("2"), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `tells an empty library apart from an empty search`() = runTest {
        repository.upsert(book("1", title = "Moby Dick"))
        val viewModel = viewModel()

        viewModel.onQueryChange("nothing matches this")

        val state = viewModel.uiState.value
        assertTrue(state.books.isEmpty())
        assertEquals(false, state.libraryIsEmpty)
    }

    @Test
    fun `hands picked files to the import manager`() = runTest {
        val viewModel = viewModel()

        viewModel.importFiles(listOf("content://a", "content://b"))

        assertEquals(listOf("content://a", "content://b"), importManager.enqueued)
    }

    @Test
    fun `surfaces import outcomes as messages until consumed`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()

            importManager.emit(ImportEvent.Succeeded(book("1", title = "Moby Dick")))
            assertEquals("Moby Dick", assertIs<LibraryMessage.Imported>(awaitItem().message).title)

            viewModel.consumeMessage()
            assertNull(awaitItem().message)

            importManager.emit(ImportEvent.Failed(AppError.Parse("broken")))
            assertIs<LibraryMessage.ImportFailed>(awaitItem().message)
        }
    }

    @Test
    fun `mirrors how many imports are running`() = runTest {
        val viewModel = viewModel()

        importManager.setActive(2)

        assertEquals(2, viewModel.uiState.value.activeImports)
    }

    @Test
    fun `deletes a book only after confirmation`() = runTest {
        val target = book("1", title = "Moby Dick")
        repository.upsert(target)
        val viewModel = viewModel()

        viewModel.setDetails(target)
        viewModel.setPendingDelete(target)
        assertEquals(target, viewModel.uiState.value.pendingDelete)
        assertTrue(importer.deleted.isEmpty())

        viewModel.confirmDelete()

        val state = viewModel.uiState.value
        assertEquals(listOf("1"), importer.deleted)
        assertNull(state.pendingDelete)
        assertNull(state.detailsBook)
        assertEquals("Moby Dick", assertIs<LibraryMessage.Deleted>(state.message).title)
    }

    @Test
    fun `reports a failed delete`() = runTest {
        val target = book("1")
        repository.upsert(target)
        importer.failure = AppError.Unexpected("disk on fire")
        val viewModel = viewModel()

        viewModel.setPendingDelete(target)
        viewModel.confirmDelete()

        assertIs<LibraryMessage.DeleteFailed>(viewModel.uiState.value.message)
    }

    @Test
    fun `opens and dismisses the details sheet`() = runTest {
        val target = book("1")
        val viewModel = viewModel()

        viewModel.setDetails(target)
        assertEquals(target, viewModel.uiState.value.detailsBook)

        viewModel.setDetails(null)
        assertNull(viewModel.uiState.value.detailsBook)
    }

    @Test
    fun `shows the download queue`() = runTest {
        val viewModel = viewModel()

        downloadManager.setJobs(listOf(downloadJob("job-1", DownloadStatus.Downloading)))
        runCurrent()

        assertEquals(listOf("job-1"), viewModel.uiState.value.downloads.map { it.id })
    }

    @Test
    fun `cancelling and dismissing reach the manager`() = runTest {
        val viewModel = viewModel()

        viewModel.cancelDownload("job-1")
        viewModel.dismissDownload("job-2")
        runCurrent()

        assertEquals(listOf("job-1"), downloadManager.cancelled)
        assertEquals(listOf("job-2"), downloadManager.dismissed)
    }

    @Test
    fun `a retry that needs the browser becomes a navigation effect`() = runTest {
        downloadManager.retryOutcome = RetryOutcome.NeedsBrowser(
            BrowserDownload(
                sourceId = "annas-archive",
                recordId = "md5abc",
                title = "Pride and Prejudice",
                authors = listOf("Jane Austen"),
                format = BookFormat.EPUB,
                url = "https://one.test/md5/md5abc",
                loginUrl = "https://one.test/account/",
                loginFormBody = "key=s3cr3t"
            )
        )
        val viewModel = viewModel()

        viewModel.retryDownload("job-1")
        runCurrent()

        val effect = assertIs<LibraryEffect.OpenBrowser>(viewModel.uiState.value.effect)
        assertEquals("https://one.test/md5/md5abc", effect.download.url)
        assertEquals(listOf("job-1"), downloadManager.retried)

        viewModel.consumeEffect()
        assertNull(viewModel.uiState.value.effect)
    }

    @Test
    fun `a plain retry leaves the screen alone`() = runTest {
        val viewModel = viewModel()

        viewModel.retryDownload("job-1")
        runCurrent()

        assertNull(viewModel.uiState.value.effect)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `shows conversion progress with the book's title`() = runTest {
        repository.upsert(book("1", title = "Moby Dick"))
        val viewModel = viewModel()

        conversionManager.setActive(
            mapOf("1" to ConversionProgress(ConversionPhase.Extracting, currentPage = 4, totalPages = 20))
        )
        runCurrent()

        val conversion = viewModel.uiState.value.conversions.single()
        assertEquals("Moby Dick", conversion.title)
        assertEquals(4, conversion.currentPage)
        assertEquals(20, conversion.totalPages)
    }

    @Test
    fun `converting a book reaches the manager and closes the sheet`() = runTest {
        val target = book("1")
        repository.upsert(target)
        val viewModel = viewModel()
        viewModel.setDetails(target)

        viewModel.convert("1")

        assertEquals(listOf("1"), conversionManager.enqueued)
        assertNull(viewModel.uiState.value.detailsBook)
    }

    @Test
    fun `conversion outcomes become messages`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            conversionManager.emit(ConversionEvent.Succeeded(book("1", title = "Moby Dick"), report = null))
            assertEquals("Moby Dick", assertIs<LibraryMessage.Converted>(awaitItem().message).title)

            viewModel.consumeMessage()
            assertNull(awaitItem().message)

            conversionManager.emit(ConversionEvent.Failed("1", AppError.Parse("broken")))
            assertIs<LibraryMessage.ConversionFailed>(awaitItem().message)
        }
    }

    private fun downloadJob(id: String, status: DownloadStatus) = DownloadJob(
        id = id,
        sourceId = "annas-archive",
        recordId = "md5abc",
        title = "Pride and Prejudice",
        format = BookFormat.EPUB,
        status = status
    )

    private fun book(
        id: String,
        title: String = "Book $id",
        author: String = "Author",
        addedAt: Long = 0,
        lastOpenedAt: Long? = null
    ) = Book(
        id = id,
        title = title,
        authors = listOf(author),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "content://test/$id",
        coverUri = null,
        sizeBytes = null,
        addedAt = Instant.ofEpochMilli(addedAt),
        lastOpenedAt = lastOpenedAt?.let(Instant::ofEpochMilli),
        progress = null,
        origin = null
    )
}

private class FakeLibraryRepository : LibraryRepository {
    private val state = MutableStateFlow<Map<String, Book>>(emptyMap())

    override val books: Flow<List<Book>> = state.map { it.values.toList() }

    override fun observeBook(id: String): Flow<Book?> = state.map { it[id] }

    override suspend fun getBook(id: String): Book? = state.value[id]

    override suspend fun findDuplicate(title: String, sizeBytes: Long): Book? =
        state.value.values.firstOrNull { it.title == title && it.sizeBytes == sizeBytes }

    override suspend fun findBySource(sourceId: String, recordId: String): Book? = state.value.values.firstOrNull {
        it.origin?.sourceId == sourceId && it.origin?.recordId == recordId
    }

    override suspend fun upsert(book: Book) = state.update { it + (book.id to book) }

    override suspend fun remove(id: String) = state.update { it - id }

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) =
        state.update { books ->
            val book = books[id] ?: return@update books
            books + (id to book.copy(progress = progress, lastOpenedAt = openedAt))
        }
}

private class FakeBookImportManager : BookImportManager {
    val enqueued = mutableListOf<String>()
    private val active = MutableStateFlow(0)
    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 8)

    override val activeImports: StateFlow<Int> = active

    override val events: SharedFlow<ImportEvent> = _events

    override fun enqueue(uris: List<String>) {
        enqueued += uris
    }

    suspend fun emit(event: ImportEvent) = _events.emit(event)

    fun setActive(count: Int) {
        active.value = count
    }
}

private class FakeBookImporter : BookImporter {
    val deleted = mutableListOf<String>()
    var failure: AppError? = null

    override suspend fun import(uri: String, origin: BookOrigin?): AppResult<ImportResult> =
        failure?.asFailure() ?: error("not used in these tests")

    override suspend fun delete(bookId: String): AppResult<Unit> {
        failure?.let { return it.asFailure() }
        deleted += bookId
        return Unit.asSuccess()
    }
}

private class FakeConversionManager : ConversionManager {
    val enqueued = mutableListOf<String>()
    private val _active = MutableStateFlow<Map<String, ConversionProgress>>(emptyMap())
    private val _events = MutableSharedFlow<ConversionEvent>(extraBufferCapacity = 8)

    override val active: StateFlow<Map<String, ConversionProgress>> = _active

    override val events: SharedFlow<ConversionEvent> = _events

    fun setActive(value: Map<String, ConversionProgress>) {
        _active.value = value
    }

    suspend fun emit(event: ConversionEvent) = _events.emit(event)

    override fun enqueue(bookId: String) {
        enqueued += bookId
    }

    override suspend fun convertAndWait(bookId: String): AppResult<Book> = AppError.Unexpected("not used").asFailure()
}

private class FakeBookDownloadManager : BookDownloadManager {
    val retried = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    val dismissed = mutableListOf<String>()
    var retryOutcome: RetryOutcome = RetryOutcome.Requeued
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
        return retryOutcome
    }

    override suspend fun cancel(jobId: String) {
        cancelled += jobId
    }

    override suspend fun dismiss(jobId: String) {
        dismissed += jobId
    }
}
