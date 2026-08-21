package dev.readana.core.data.conversion

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.FakeSettingsRepository
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.data.repository.RoomLibraryRepository
import dev.readana.core.data.storage.BookStorage
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
import dev.readana.core.model.ReadingProgress
import dev.readana.engine.epub.EpubWriter
import dev.readana.engine.pdf2epub.ConversionPhase
import dev.readana.engine.pdf2epub.ConversionProgress
import dev.readana.engine.pdf2epub.PdfToEpubConverter
import dev.readana.engine.reflow.model.ContentExtractor
import dev.readana.engine.reflow.model.DocumentContent
import dev.readana.engine.reflow.model.OutlineNode
import dev.readana.engine.reflow.model.PageContent
import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import java.io.File
import java.io.InputStream
import java.time.Instant
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * The pipeline around the engine: which file is read, what the row becomes afterwards, and what
 * survives a failure. The engine itself is driven through a fake extractor, so no PDF is needed.
 */
@RunWith(AndroidJUnit4::class)
class DefaultBookConverterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings = FakeSettingsRepository()
    private lateinit var database: ReadanaDatabase
    private lateinit var library: RoomLibraryRepository
    private lateinit var storage: BookStorage
    private var extractor: ContentExtractor = FakeExtractor()

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        database = Room.inMemoryDatabaseBuilder(context, ReadanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = RoomLibraryRepository(database.bookDao(), dispatcher)
        storage = BookStorage(context, settings, dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `converting points the entry at the epub and keeps the original`() = runTest {
        val pdf = pdfFile("moby.pdf")
        library.upsert(book(fileUri = pdf, format = BookFormat.PDF))

        val outcome = converter().convert("book-1")

        val converted = assertIs<AppResult.Success<Book>>(outcome).value
        assertEquals(BookFormat.EPUB, converted.format)
        assertTrue(converted.fileUri.endsWith(".epub"), "reads as EPUB, was ${converted.fileUri}")
        assertEquals(pdf, converted.conversion?.originalFileUri)
        assertTrue(File(assertNotNull(Uri.parse(pdf).path)).exists(), "the original PDF is kept")
        assertEquals(converted.fileUri, library.getBook("book-1")?.fileUri)
    }

    @Test
    fun `the report is persisted and read back`() = runTest {
        library.upsert(book(fileUri = pdfFile("moby.pdf"), format = BookFormat.PDF))

        converter().convert("book-1")

        val report = assertNotNull(library.getBook("book-1")?.conversion?.report)
        assertEquals(2, report.pages)
        assertEquals(2, report.chapters, "the fake outline defines two chapters")
        assertTrue(report.outlineUsed)
        assertTrue(report.warnings.any { "Reading position" in it })
    }

    @Test
    fun `reading position is dropped because the pagination changed`() = runTest {
        library.upsert(
            book(fileUri = pdfFile("moby.pdf"), format = BookFormat.PDF)
                .copy(progress = ReadingProgress("{}", 0.5f))
        )

        converter().convert("book-1")

        assertNull(library.getBook("book-1")?.progress)
    }

    @Test
    fun `size stays at the original so duplicate detection still works`() = runTest {
        library.upsert(book(fileUri = pdfFile("moby.pdf"), format = BookFormat.PDF).copy(sizeBytes = 4242))

        converter().convert("book-1")

        assertEquals(4242, library.getBook("book-1")?.sizeBytes)
    }

    @Test
    fun `re-converting works from the kept original`() = runTest {
        val pdf = pdfFile("moby.pdf")
        library.upsert(book(fileUri = pdf, format = BookFormat.PDF))
        val first = assertIs<AppResult.Success<Book>>(converter().convert("book-1")).value

        val second = assertIs<AppResult.Success<Book>>(converter().convert("book-1")).value

        assertEquals(pdf, second.conversion?.originalFileUri)
        assertFalse(
            File(assertNotNull(Uri.parse(first.fileUri).path)).exists(),
            "the superseded EPUB is deleted"
        )
    }

    @Test
    fun `a book that is not a pdf is rejected`() = runTest {
        library.upsert(book(fileUri = "file:///books/x.epub", format = BookFormat.EPUB))

        val outcome = converter().convert("book-1")

        assertIs<AppError.Unsupported>(assertIs<AppResult.Failure>(outcome).error)
    }

    @Test
    fun `a failing conversion leaves the entry untouched`() = runTest {
        extractor = FakeExtractor(pages = emptyList())
        library.upsert(book(fileUri = pdfFile("empty.pdf"), format = BookFormat.PDF))

        val outcome = converter().convert("book-1")

        assertIs<AppResult.Failure>(outcome)
        val unchanged = assertNotNull(library.getBook("book-1"))
        assertEquals(BookFormat.PDF, unchanged.format)
        assertNull(unchanged.conversion)
    }

    @Test
    fun `progress is reported through every phase`() = runTest {
        library.upsert(book(fileUri = pdfFile("moby.pdf"), format = BookFormat.PDF))
        val phases = mutableListOf<ConversionPhase>()

        converter().convert("book-1") { progress: ConversionProgress -> phases += progress.phase }

        assertTrue(phases.containsAll(ConversionPhase.entries), "saw $phases")
    }

    @Test
    fun `heading sensitivity reaches the engine`() {
        val conservative = ConversionSettings(headingSensitivity = HeadingSensitivity.Conservative)
        val aggressive = ConversionSettings(headingSensitivity = HeadingSensitivity.Aggressive)

        assertTrue(
            conservative.toReflowOptions().headingSizeFactor >
                aggressive.toReflowOptions().headingSizeFactor
        )
        assertFalse(
            ConversionSettings(removeHeadersAndFooters = false)
                .toReflowOptions().removeRepeatedHeadersAndFooters
        )
    }

    private fun converter() = DefaultBookConverter(
        context = context,
        libraryRepository = library,
        storage = storage,
        settingsRepository = settings,
        converterProvider = Provider { PdfToEpubConverter(extractor, EpubWriter()) { 0L } },
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun pdfFile(name: String): String {
        val file = File(context.cacheDir, name).apply { writeText("pretend pdf bytes") }
        return Uri.fromFile(file).toString()
    }

    private fun book(fileUri: String, format: BookFormat) = Book(
        id = "book-1",
        title = "Moby Dick",
        authors = listOf("Herman Melville"),
        language = "en",
        format = format,
        fileUri = fileUri,
        coverUri = null,
        sizeBytes = 10,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )

    /** Stands in for PDFBox: two pages, each opening with a heading, plus a matching outline. */
    private class FakeExtractor(private val pages: List<PageContent> = defaultPages()) : ContentExtractor {
        override fun extract(input: InputStream, onPage: (pageIndex: Int, pageCount: Int) -> Unit): DocumentContent {
            input.readBytes()
            pages.forEachIndexed { index, _ -> onPage(index, pages.size) }
            return DocumentContent(
                pages = pages,
                outline = if (pages.size < 2) {
                    emptyList()
                } else {
                    listOf(OutlineNode("Chapter One", 0), OutlineNode("Chapter Two", 1))
                }
            )
        }

        companion object {
            fun defaultPages(): List<PageContent> = (0..1).map { index ->
                PageContent(
                    PageText(
                        index = index,
                        width = 612f,
                        height = 792f,
                        lines = listOf(
                            TextLine.of("Chapter ${index + 1}", x = 60f, baseline = 80f, width = 200f, fontSize = 18f),
                            TextLine.of(
                                "Body text for chapter ${index + 1} that runs on for a while.",
                                x = 60f,
                                baseline = 110f,
                                width = 400f,
                                fontSize = 10f
                            )
                        )
                    )
                )
            }
        }
    }
}
