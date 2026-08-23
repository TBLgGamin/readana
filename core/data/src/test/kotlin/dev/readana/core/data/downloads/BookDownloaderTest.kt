package dev.readana.core.data.downloads

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.FakeSettingsRepository
import dev.readana.core.data.conversion.FakeConversionManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportResult
import dev.readana.core.data.repository.testRegistry
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadStatus
import dev.readana.source.api.BookSource
import dev.readana.source.api.DownloadQuota
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.SearchPage
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDownloaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloads = FakeDownloadsRepository()
    private val importer = RecordingImporter()
    private val conversions = FakeConversionManager()
    private val settings = FakeSettingsRepository()
    private lateinit var partsDir: File

    @Before
    fun setUp() {
        partsDir = File(context.cacheDir, "parts-${Instant.now().toEpochMilli()}").apply { mkdirs() }
    }

    @Test
    fun `the imported file is named for what its bytes actually are`() = runTest {
        // The importer works the format out from the file name, and a ".part" file told it
        // nothing — a download would run to completion and then be refused as "not an EPUB".
        downloads.upsert(downloadEntity())
        val downloader = downloader { respond(EPUB_BYTES, HttpStatusCode.OK) }

        downloader.run("job-1", isRetryAttempt = false) { _, _ -> }

        assertEquals("job-1.epub", importer.importedName)
    }

    @Test
    fun `a record that claimed EPUB but downloaded a PDF is imported as a PDF`() = runTest {
        downloads.upsert(downloadEntity())
        val downloader = downloader { respond("%PDF-1.7 and the rest", HttpStatusCode.OK) }

        val outcome = downloader.run("job-1", isRetryAttempt = false) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Success, outcome)
        assertEquals("job-1.pdf", importer.importedName)
        assertEquals(BookFormat.PDF, importer.origin?.originalFormat)
    }

    @Test
    fun `a page served in place of a book is refused instead of imported`() = runTest {
        downloads.upsert(downloadEntity())
        // What a partner server sends when it would rather not: an error page, with a 200 on it.
        val downloader = downloader { respond("<!doctype html><html>Try again later", HttpStatusCode.OK) }

        val outcome = downloader.run("job-1", isRetryAttempt = false) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Failure, outcome)
        assertNull(importer.importedText, "nothing that shape should reach the library")
        assertEquals(DownloadStatus.Failed, downloads.getJob("job-1")?.status)
    }

    @Test
    fun `streams the file and hands it to the importer with its origin`() = runTest {
        downloads.upsert(downloadEntity())
        val downloader = downloader { respond(EPUB_BYTES, HttpStatusCode.OK) }

        val outcome = downloader.run("job-1", isRetryAttempt = false) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Success, outcome)
        assertEquals(EPUB_BYTES, importer.importedText)
        assertEquals(BookOrigin("annas-archive", "0123456789abcdef0123456789abcdef", BookFormat.EPUB), importer.origin)
        // The row goes with it: the book is in the library, so the queue has nothing left to say.
        assertNull(downloads.getJob("job-1"))
        assertTrue(partsDir.listFiles().orEmpty().isEmpty(), "the partial file is cleaned up")
    }

    @Test
    fun `resumes a partial file with a Range request`() = runTest {
        downloads.upsert(downloadEntity())
        // Nine bytes of it already on disk, signature included.
        File(partsDir, "job-1.part").writeText("PKepub ")
        var range: String? = null
        val downloader = downloader { request ->
            range = request.headers[HttpHeaders.Range]
            respond(
                "bytes",
                HttpStatusCode.PartialContent,
                headersOf(HttpHeaders.ContentRange, "bytes 9-13/14")
            )
        }

        val outcome = downloader.run("job-1", isRetryAttempt = true) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Success, outcome)
        assertEquals("bytes=9-", range)
        assertEquals(EPUB_BYTES, importer.importedText)
    }

    @Test
    fun `restarts from scratch when the server ignores the range`() = runTest {
        downloads.upsert(downloadEntity())
        File(partsDir, "job-1.part").writeText("stale")
        val downloader = downloader { respond(EPUB_BYTES, HttpStatusCode.OK) }

        downloader.run("job-1", isRetryAttempt = true) { _, _ -> }

        assertEquals(EPUB_BYTES, importer.importedText)
    }

    @Test
    fun `a server error is retryable and keeps the partial file`() = runTest {
        downloads.upsert(downloadEntity())
        File(partsDir, "job-1.part").writeText("half")
        val downloader = downloader { respondError(HttpStatusCode.BadGateway) }

        val outcome = downloader.run("job-1", isRetryAttempt = false) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Retry, outcome)
        assertTrue(File(partsDir, "job-1.part").exists())
        assertNull(importer.importedText)
    }

    @Test
    fun `an expired link is resolved again before giving up`() = runTest {
        downloads.upsert(downloadEntity(resolvedUrl = "https://fast.test/expired.epub"))
        val urls = mutableListOf<String>()
        val downloader =
            downloader(
                source = FakeSource(
                    DownloadRequest("https://fast.test/fresh.epub", BookFormat.EPUB, "b.epub").asSuccess()
                )
            ) { request ->
                urls += request.url.toString()
                if (request.url.toString().endsWith("expired.epub")) {
                    respondError(HttpStatusCode.Forbidden)
                } else {
                    respond(EPUB_BYTES, HttpStatusCode.OK)
                }
            }

        val outcome = downloader.run("job-1", isRetryAttempt = false) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Success, outcome)
        assertEquals(listOf("https://fast.test/expired.epub", "https://fast.test/fresh.epub"), urls)
    }

    @Test
    fun `a spent quota fails without burning retries`() = runTest {
        downloads.upsert(downloadEntity())
        val exhausted = DownloadRequest(
            url = "https://one.test/md5/abc",
            format = BookFormat.EPUB,
            suggestedFileName = "b.epub",
            requiresBrowser = true,
            quota = DownloadQuota(remaining = 0, perDay = 100)
        )
        val downloader = downloader(source = FakeSource(exhausted.asSuccess())) {
            respondError(HttpStatusCode.Forbidden)
        }

        val outcome = downloader.run("job-1", isRetryAttempt = true) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Failure, outcome)
        val job = downloads.getJob("job-1")
        assertEquals(DownloadStatus.Failed, job?.status)
        assertEquals(DownloadErrorKind.QuotaExhausted, job?.errorKind)
        assertEquals("https://one.test/md5/abc", job?.browserUrl, "the queue can offer the browser")
    }

    @Test
    fun `a browser-captured job is never resolved again`() = runTest {
        downloads.upsert(
            downloadEntity(
                resolvedUrl = "https://slow.test/file.epub",
                headersJson = """{"Cookie":"aa_account_id2=xyz"}"""
            )
        )
        var cookie: String? = null
        val source = FakeSource(AppError.Unexpected("must not be called").asFailure())
        val downloader = downloader(source = source) { request ->
            cookie = request.headers[HttpHeaders.Cookie]
            respond(EPUB_BYTES, HttpStatusCode.OK)
        }

        val outcome = downloader.run("job-1", isRetryAttempt = true) { _, _ -> }

        assertEquals(BookDownloader.Outcome.Success, outcome)
        assertEquals("aa_account_id2=xyz", cookie)
        assertEquals(0, source.resolveCalls)
    }

    @Test
    fun `a cancelled job does nothing`() = runTest {
        downloads.upsert(downloadEntity(status = DownloadStatus.Cancelled))
        val downloader = downloader { error("no request expected") }

        assertEquals(BookDownloader.Outcome.Failure, downloader.run("job-1", false) { _, _ -> })
        assertNull(importer.importedText)
    }

    @Test
    fun `sweeping removes partial files with no row`() = runTest {
        File(partsDir, "job-1.part").writeText("keep")
        File(partsDir, "ghost.part").writeText("drop")

        downloader { error("no request expected") }.sweepOrphans(setOf("job-1"))

        assertTrue(File(partsDir, "job-1.part").exists())
        assertFalse(File(partsDir, "ghost.part").exists())
    }

    private fun downloader(
        source: FakeSource =
            FakeSource(DownloadRequest("https://fast.test/dl/abc.epub", BookFormat.EPUB, "b.epub").asSuccess()),
        handler: MockRequestHandler
    ): BookDownloader {
        val client = HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(HttpTimeout)
        }
        return BookDownloader(
            client = client,
            downloadsRepository = downloads,
            sources = testRegistry(setOf(source), settings),
            importer = importer,
            conversionManager = conversions,
            settingsRepository = settings,
            partsDir = partsDir,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    private class FakeSource(private val resolution: AppResult<DownloadRequest>) : BookSource {
        var resolveCalls = 0

        override val id = SourceId("annas-archive")
        override val displayName = "Anna's Archive"

        override suspend fun search(query: SearchQuery): AppResult<SearchPage> =
            SearchPage(emptyList(), 1, false).asSuccess()

        override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
            resolveCalls++
            return resolution
        }
    }

    private companion object {
        /** A zip signature, because that is what the downloader now checks an EPUB by. */
        const val EPUB_BYTES = "PKepub bytes"
    }

    private class RecordingImporter : BookImporter {
        var importedText: String? = null
        var importedName: String? = null
        var origin: BookOrigin? = null

        override suspend fun import(uri: String, origin: BookOrigin?): AppResult<ImportResult> {
            val file = File(checkNotNull(Uri.parse(uri).path))
            importedText = file.readText()
            importedName = file.name
            this.origin = origin
            return ImportResult.Imported(book()).asSuccess()
        }

        override suspend fun delete(bookId: String): AppResult<Unit> = Unit.asSuccess()

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
            origin = origin
        )
    }
}
