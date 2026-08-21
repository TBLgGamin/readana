package dev.readana.core.data.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.FakeSettingsRepository
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportResult
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.RoomLibraryRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.DownloadStatus
import dev.readana.source.api.BookSource
import dev.readana.source.api.BrowserLogin
import dev.readana.source.api.DownloadQuota
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.SearchPage
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultBookDownloadManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloads = FakeDownloadsRepository()
    private val settings = FakeSettingsRepository()
    private val scheduler = RecordingScheduler()
    private lateinit var database: ReadanaDatabase
    private lateinit var library: RoomLibraryRepository
    private lateinit var partsDir: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, ReadanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = RoomLibraryRepository(database.bookDao(), UnconfinedTestDispatcher())
        partsDir = File(context.cacheDir, "manager-parts").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enqueueing schedules the job and reports the remaining quota`() = runTest {
        val manager = manager(source = FakeSource(fastDownload(remaining = 7).asSuccess()))

        val outcome = manager.enqueue(searchResult())

        val enqueued = assertIs<EnqueueOutcome.Enqueued>(outcome)
        assertEquals(7, enqueued.fastDownloadsLeft)
        assertEquals(DownloadStatus.Queued, enqueued.job.status)
        assertEquals(listOf("download:annas-archive:md5abc"), scheduler.scheduled.map { it.second })
        assertEquals(7, settings.sourceSettingsNow.annasDownloadsLeft)
    }

    @Test
    fun `a book already in the library is not downloaded again`() = runTest {
        library.upsert(book(origin = BookOrigin("annas-archive", "md5abc", BookFormat.EPUB)))
        val manager = manager(source = FakeSource(fastDownload().asSuccess()))

        val outcome = manager.enqueue(searchResult())

        assertEquals("book-1", assertIs<EnqueueOutcome.AlreadyInLibrary>(outcome).book.id)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `a record already in the queue is not queued twice`() = runTest {
        downloads.upsert(downloadEntity(recordId = "md5abc", status = DownloadStatus.Downloading))
        val manager = manager(source = FakeSource(fastDownload().asSuccess()))

        val outcome = manager.enqueue(searchResult())

        assertEquals("job-1", assertIs<EnqueueOutcome.AlreadyQueued>(outcome).job.id)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `a spent quota asks for the browser instead of creating a dead row`() = runTest {
        val browserRequest = DownloadRequest(
            url = "https://one.test/md5/md5abc",
            format = BookFormat.EPUB,
            suggestedFileName = "b.epub",
            requiresBrowser = true,
            quota = DownloadQuota(remaining = 0, perDay = 100),
            browserLogin = BrowserLogin("https://one.test/account/", "key=s3cr3t")
        )
        val manager = manager(source = FakeSource(browserRequest.asSuccess()))

        val outcome = manager.enqueue(searchResult())

        val browser = assertIs<EnqueueOutcome.RequiresBrowser>(outcome)
        assertTrue(browser.quotaExhausted)
        assertEquals("https://one.test/account/", browser.login?.url)
        assertTrue(downloads.state.value.isEmpty(), "no row for something that never started")
        assertEquals(0, settings.sourceSettingsNow.annasDownloadsLeft)
    }

    @Test
    fun `a failing source reports the error`() = runTest {
        val manager = manager(source = FakeSource(AppError.Unauthorized("bad key").asFailure()))

        val outcome = manager.enqueue(searchResult())

        assertIs<AppError.Unauthorized>(assertIs<EnqueueOutcome.Failed>(outcome).error)
        assertTrue(downloads.state.value.isEmpty())
    }

    @Test
    fun `a captured download keeps its cookies and skips resolution`() = runTest {
        val manager = manager(source = FakeSource(AppError.Unexpected("must not resolve").asFailure()))

        val outcome = manager.enqueueCaptured(
            CapturedDownload(
                sourceId = "annas-archive",
                recordId = "md5abc",
                title = "Pride and Prejudice",
                authors = listOf("Jane Austen"),
                format = BookFormat.EPUB,
                url = "https://slow.test/file.epub",
                headers = mapOf("Cookie" to "aa_account_id2=xyz")
            )
        )

        val job = assertIs<EnqueueOutcome.Enqueued>(outcome).job
        val entity = downloads.state.value.getValue(job.id)
        assertEquals("https://slow.test/file.epub", entity.resolvedUrl)
        assertEquals(mapOf("Cookie" to "aa_account_id2=xyz"), entity.headersJson.decodeStringMap())
        assertEquals(listOf("download:annas-archive:md5abc"), scheduler.scheduled.map { it.second })
    }

    @Test
    fun `retrying re-queues and re-schedules`() = runTest {
        downloads.upsert(downloadEntity(recordId = "md5abc", status = DownloadStatus.Failed))
        val manager = manager()

        assertEquals(RetryOutcome.Requeued, manager.retry("job-1"))

        assertEquals(DownloadStatus.Queued, downloads.getJob("job-1")?.status)
        assertEquals(listOf("download:annas-archive:md5abc"), scheduler.scheduled.map { it.second })
    }

    @Test
    fun `retrying a quota failure asks the source again and takes a refreshed link`() = runTest {
        downloads.upsert(
            downloadEntity(recordId = "md5abc", status = DownloadStatus.Failed)
                .copy(errorKind = "QuotaExhausted")
        )
        val manager = manager(source = FakeSource(fastDownload(remaining = 3).asSuccess()))

        assertEquals(RetryOutcome.Requeued, manager.retry("job-1"))

        assertEquals(DownloadStatus.Queued, downloads.getJob("job-1")?.status)
        assertEquals(3, settings.sourceSettingsNow.annasDownloadsLeft)
    }

    @Test
    fun `retrying a still-spent quota hands the job to the browser`() = runTest {
        downloads.upsert(
            downloadEntity(recordId = "md5abc", status = DownloadStatus.Failed)
                .copy(errorKind = "QuotaExhausted")
        )
        val stillSpent = DownloadRequest(
            url = "https://one.test/md5/md5abc",
            format = BookFormat.EPUB,
            suggestedFileName = "b.epub",
            requiresBrowser = true,
            quota = DownloadQuota(remaining = 0, perDay = 100),
            browserLogin = BrowserLogin("https://one.test/account/", "key=s3cr3t")
        )
        val manager = manager(source = FakeSource(stillSpent.asSuccess()))

        val outcome = assertIs<RetryOutcome.NeedsBrowser>(manager.retry("job-1"))

        assertEquals("https://one.test/md5/md5abc", outcome.download.url)
        assertEquals("key=s3cr3t", outcome.download.loginFormBody)
        assertTrue(downloads.state.value.isEmpty(), "the browser owns it now")
    }

    @Test
    fun `cancelling stops the work and drops the partial file`() = runTest {
        downloads.upsert(downloadEntity(recordId = "md5abc", status = DownloadStatus.Downloading))
        File(partsDir, "job-1.part").writeText("half")
        val manager = manager()

        manager.cancel("job-1")

        assertEquals(DownloadStatus.Cancelled, downloads.getJob("job-1")?.status)
        assertEquals(listOf("download:annas-archive:md5abc"), scheduler.cancelled)
        assertFalse(File(partsDir, "job-1.part").exists())
    }

    @Test
    fun `dismissing forgets the row`() = runTest {
        downloads.upsert(downloadEntity(recordId = "md5abc", status = DownloadStatus.Failed))
        val manager = manager()

        manager.dismiss("job-1")

        assertTrue(manager.jobs.first().isEmpty())
    }

    private fun manager(source: FakeSource = FakeSource(fastDownload().asSuccess())): DefaultBookDownloadManager {
        val registry = BookSourceRegistry(setOf(source))
        val downloader = BookDownloader(
            client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
            downloadsRepository = downloads,
            sources = registry,
            importer = NoopImporter(),
            partsDir = partsDir,
            ioDispatcher = UnconfinedTestDispatcher()
        )
        return DefaultBookDownloadManager(
            downloadsRepository = downloads,
            libraryRepository = library,
            sources = registry,
            settingsRepository = settings,
            scheduler = scheduler,
            downloader = downloader,
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    private fun fastDownload(remaining: Int? = null) = DownloadRequest(
        url = "https://fast.test/dl/abc.epub",
        format = BookFormat.EPUB,
        suggestedFileName = "Pride and Prejudice.epub",
        quota = remaining?.let { DownloadQuota(remaining = it, perDay = 100) }
    )

    private fun searchResult() = SearchResult(
        sourceId = SourceId("annas-archive"),
        recordId = "md5abc",
        title = "Pride and Prejudice",
        authors = listOf("Jane Austen"),
        format = BookFormat.EPUB
    )

    private fun book(origin: BookOrigin?) = Book(
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
        origin = origin
    )

    private class RecordingScheduler : DownloadScheduler {
        val scheduled = mutableListOf<Pair<String, String>>()
        val cancelled = mutableListOf<String>()

        override fun schedule(jobId: String, uniqueName: String) {
            scheduled += jobId to uniqueName
        }

        override fun cancel(uniqueName: String) {
            cancelled += uniqueName
        }
    }

    private class FakeSource(private val resolution: AppResult<DownloadRequest>) : BookSource {
        override val id = SourceId("annas-archive")
        override val displayName = "Anna's Archive"

        override suspend fun search(query: SearchQuery): AppResult<SearchPage> =
            SearchPage(emptyList(), 1, false).asSuccess()

        override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> = resolution
    }

    private class NoopImporter : BookImporter {
        override suspend fun import(uri: String, origin: BookOrigin?): AppResult<ImportResult> =
            AppError.Unexpected("not used").asFailure()

        override suspend fun delete(bookId: String): AppResult<Unit> = Unit.asSuccess()
    }
}
