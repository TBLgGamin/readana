package dev.readana.core.data.importing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.FakeSettingsRepository
import dev.readana.core.data.database.ReadanaDatabase
import dev.readana.core.data.repository.RoomLibraryRepository
import dev.readana.core.data.storage.BookStorage
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultBookImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ReadanaDatabase
    private lateinit var library: RoomLibraryRepository
    private lateinit var storage: BookStorage
    private var extracted: AppResult<ExtractedMetadata> = metadata().asSuccess()

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        database = Room.inMemoryDatabaseBuilder(context, ReadanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = RoomLibraryRepository(database.bookDao(), dispatcher)
        storage = BookStorage(context, FakeSettingsRepository(), dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `imports a book with its metadata, file and cover`() = runTest {
        extracted = metadata(cover = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)).asSuccess()

        val outcome = importer().import(sourceFile("moby.epub"))

        val imported = assertIs<ImportResult.Imported>(assertIs<AppResult.Success<ImportResult>>(outcome).value)
        val book = imported.book
        assertEquals("Moby Dick", book.title)
        assertEquals(listOf("Herman Melville"), book.authors)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("en", book.language)
        assertTrue(File(assertNotNull(Uri.parse(book.fileUri).path)).exists())
        assertNotNull(book.coverUri)
        assertEquals(listOf(book.id), library.booksNow().map { it.id })
    }

    @Test
    fun `falls back to the file name when the book has no title`() = runTest {
        extracted = metadata(title = null).asSuccess()

        val outcome = importer().import(sourceFile("Some Pamphlet.pdf"))

        val imported = assertIs<ImportResult.Imported>(assertIs<AppResult.Success<ImportResult>>(outcome).value)
        assertEquals("Some Pamphlet", imported.book.title)
        assertEquals(BookFormat.PDF, imported.book.format)
    }

    @Test
    fun `reports a duplicate instead of copying the file twice`() = runTest {
        val source = sourceFile("moby.epub")
        importer().import(source)
        val booksAfterFirst = library.booksNow()

        val outcome = importer().import(source)

        val duplicate = assertIs<ImportResult.Duplicate>(assertIs<AppResult.Success<ImportResult>>(outcome).value)
        assertEquals(booksAfterFirst.single().id, duplicate.existing.id)
        assertEquals(1, library.booksNow().size)
    }

    @Test
    fun `records where a downloaded book came from`() = runTest {
        val origin = BookOrigin("annas-archive", "abc123", BookFormat.PDF)

        val outcome = importer().import(sourceFile("moby.epub"), origin)

        val imported = assertIs<ImportResult.Imported>(assertIs<AppResult.Success<ImportResult>>(outcome).value)
        assertEquals("annas-archive", imported.book.origin?.sourceId)
        assertEquals("abc123", imported.book.origin?.recordId)
        // The stored file is what it is, whatever the search result claimed.
        assertEquals(BookFormat.EPUB, imported.book.origin?.originalFormat)
        assertEquals(imported.book.id, library.findBySource("annas-archive", "abc123")?.id)
    }

    @Test
    fun `rejects files that are neither EPUB nor PDF`() = runTest {
        val outcome = importer().import(sourceFile("notes.txt"))

        assertIs<AppError.Unsupported>(assertIs<AppResult.Failure>(outcome).error)
        assertTrue(library.booksNow().isEmpty())
    }

    @Test
    fun `keeps the library untouched when the file cannot be parsed`() = runTest {
        extracted = AppError.Parse("broken").asFailure()

        val outcome = importer().import(sourceFile("broken.epub"))

        assertIs<AppError.Parse>(assertIs<AppResult.Failure>(outcome).error)
        assertTrue(library.booksNow().isEmpty())
    }

    @Test
    fun `delete removes the row, the file and the cover`() = runTest {
        extracted = metadata(cover = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)).asSuccess()
        val importer = importer()
        val imported = assertIs<ImportResult.Imported>(
            assertIs<AppResult.Success<ImportResult>>(importer.import(sourceFile("moby.epub"))).value
        )
        val file = File(assertNotNull(Uri.parse(imported.book.fileUri).path))
        val cover = File(assertNotNull(Uri.parse(assertNotNull(imported.book.coverUri)).path))

        val outcome = importer.delete(imported.book.id)

        assertIs<AppResult.Success<Unit>>(outcome)
        assertNull(library.getBook(imported.book.id))
        assertFalse(file.exists())
        assertFalse(cover.exists())
    }

    @Test
    fun `delete reports an unknown book`() = runTest {
        val outcome = importer().delete("nope")

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(outcome).error)
    }

    private fun importer() = DefaultBookImporter(
        context = context,
        metadataExtractor = { extracted },
        storage = storage,
        libraryRepository = library,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun metadata(title: String? = "Moby Dick", cover: Bitmap? = null) = ExtractedMetadata(
        title = title,
        authors = listOf("Herman Melville"),
        language = "en",
        cover = cover
    )

    private fun sourceFile(name: String): String {
        val file = File(context.cacheDir, name).apply { writeText("book bytes") }
        return Uri.fromFile(file).toString()
    }

    private suspend fun RoomLibraryRepository.booksNow(): List<Book> = books.first()
}
