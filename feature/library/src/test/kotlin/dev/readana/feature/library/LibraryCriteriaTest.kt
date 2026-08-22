package dev.readana.feature.library

import dev.readana.core.model.Book
import dev.readana.core.model.BookCollection
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookSeries
import dev.readana.core.model.ReadingProgress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryCriteriaTest {
    @Test
    fun `finds a book by title, author or series`() {
        val library = listOf(
            book("1", title = "Moby-Dick", author = "Herman Melville"),
            book("2", title = "Persuasion", author = "Jane Austen"),
            book("3", title = "The Fellowship", series = BookSeries("The Lord of the Rings", 1f))
        )

        assertEquals(listOf("1"), library.ids(LibraryCriteria(query = "moby")))
        assertEquals(listOf("2"), library.ids(LibraryCriteria(query = "austen")))
        assertEquals(listOf("3"), library.ids(LibraryCriteria(query = "lord of the rings")))
    }

    @Test
    fun `filters by where a book stands`() {
        val library = listOf(
            book("unread"),
            book("reading", progress = 0.4f),
            book("done", progress = 0.99f),
            book("called-done", progress = 0.2f, finished = true)
        )

        assertEquals(listOf("unread"), library.ids(LibraryCriteria(filter = LibraryFilter.Unread)))
        assertEquals(listOf("reading"), library.ids(LibraryCriteria(filter = LibraryFilter.Reading)))
        assertEquals(
            listOf("done", "called-done"),
            library.ids(LibraryCriteria(filter = LibraryFilter.Finished))
        )
    }

    @Test
    fun `the reader's verdict outranks the progress bar`() {
        val abandoned = book("1", progress = 0.2f, finished = true)

        assertTrue(abandoned.id in listOf(abandoned).ids(LibraryCriteria(filter = LibraryFilter.Finished)))
        assertFalse(abandoned.id in listOf(abandoned).ids(LibraryCriteria(filter = LibraryFilter.Reading)))
    }

    @Test
    fun `narrows to one shelf`() {
        val library = listOf(book("1"), book("2"), book("3"))
        val memberships = mapOf("shelf" to setOf("1", "3"))

        val shown = library.forLibrary(
            LibraryCriteria(shelf = Shelf.Collection("shelf", "Favourites")),
            memberships
        )

        assertEquals(listOf("1", "3"), shown.map { it.id })
    }

    @Test
    fun `narrows to one series`() {
        val library = listOf(
            book("1", series = BookSeries("Discworld", 1f)),
            book("2", series = BookSeries("Discworld", 2f)),
            book("3")
        )

        val shown = library.forLibrary(LibraryCriteria(shelf = Shelf.Series("Discworld")), emptyMap())

        assertEquals(listOf("1", "2"), shown.map { it.id })
    }

    @Test
    fun `offers collections first, then the series with more than one book in them`() {
        val library = listOf(
            book("1", series = BookSeries("Discworld", 1f)),
            book("2", series = BookSeries("Discworld", 2f)),
            // A series the reader owns one of is a coincidence, not a shelf.
            book("3", series = BookSeries("Earthsea", 1f))
        )
        val made = listOf(BookCollection("c", "Favourites", Instant.EPOCH))

        assertEquals(
            listOf(Shelf.Collection("c", "Favourites"), Shelf.Series("Discworld")),
            library.shelves(made)
        )
    }

    @Test
    fun `a library with no shelves offers none`() {
        assertEquals(emptyList(), listOf(book("1")).shelves(emptyList()))
    }

    @Test
    fun `offers to continue the book most recently open`() {
        val library = listOf(
            book("old", progress = 0.3f, lastOpened = Instant.ofEpochMilli(1_000)),
            book("new", progress = 0.6f, lastOpened = Instant.ofEpochMilli(9_000)),
            book("finished", progress = 0.99f, lastOpened = Instant.ofEpochMilli(99_000))
        )

        assertEquals("new", library.continueReading()?.id)
    }

    @Test
    fun `offers to continue nothing when nothing has been started`() {
        assertNull(listOf(book("1"), book("2")).continueReading())
    }

    @Test
    fun `a search or a filter is a question, so the shortcut gets out of the way`() {
        assertFalse(LibraryCriteria().isNarrowed)
        assertTrue(LibraryCriteria(query = "moby").isNarrowed)
        assertTrue(LibraryCriteria(filter = LibraryFilter.Unread).isNarrowed)
        assertTrue(LibraryCriteria(shelf = Shelf.Series("Discworld")).isNarrowed)
    }

    @Test
    fun `sorts by title and by author`() {
        val library = listOf(
            book("1", title = "Zuleika", author = "Max Beerbohm"),
            book("2", title = "Anna", author = "Leo Tolstoy")
        )

        assertEquals(listOf("2", "1"), library.ids(LibraryCriteria(sort = LibrarySort.Title)))
        assertEquals(listOf("2", "1"), library.ids(LibraryCriteria(sort = LibrarySort.Author)))
    }

    private fun List<Book>.ids(criteria: LibraryCriteria) = forLibrary(criteria, emptyMap()).map { it.id }

    private fun book(
        id: String,
        title: String = "Book $id",
        author: String = "An Author",
        progress: Float? = null,
        finished: Boolean = false,
        series: BookSeries? = null,
        lastOpened: Instant? = null
    ) = Book(
        id = id,
        title = title,
        authors = listOf(author),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/$id.epub",
        coverUri = null,
        sizeBytes = 1,
        addedAt = Instant.EPOCH,
        lastOpenedAt = lastOpened,
        progress = progress?.let { ReadingProgress("{}", it) },
        origin = null,
        finishedAt = if (finished) Instant.EPOCH else null,
        series = series
    )
}
