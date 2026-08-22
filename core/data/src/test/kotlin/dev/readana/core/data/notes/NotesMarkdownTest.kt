package dev.readana.core.data.notes

import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.Highlight
import dev.readana.core.model.HighlightStyle
import dev.readana.core.model.HighlightTint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotesMarkdownTest {
    @Test
    fun `heads the export with the book`() {
        val markdown = notesMarkdown(book(), listOf(highlight()))

        assertTrue(markdown.startsWith("# Moby-Dick\n*Herman Melville*\n"))
    }

    @Test
    fun `quotes the passage and writes the note under it`() {
        val markdown = notesMarkdown(book(), listOf(highlight(note = "the famous opening")))

        assertTrue(markdown.contains("> Call me Ishmael."))
        assertTrue(markdown.contains("\nthe famous opening\n"))
    }

    @Test
    fun `quotes every line of a passage that spans several`() {
        val markdown = notesMarkdown(book(), listOf(highlight(text = "first line\nsecond line")))

        assertTrue(markdown.contains("> first line\n> second line"))
    }

    @Test
    fun `leaves out what a highlight does not have`() {
        val markdown = notesMarkdown(book(), listOf(highlight(note = "   ", progression = null)))

        assertFalse(markdown.contains("%`"))
        // A blank note is no note; it should not leave an empty paragraph behind.
        assertFalse(markdown.contains("\n   \n"))
    }

    @Test
    fun `rounds the position to a percentage`() {
        val markdown = notesMarkdown(book(), listOf(highlight(progression = 0.426)))

        assertTrue(markdown.contains("`43%`"))
    }

    @Test
    fun `a book nobody marked is still a valid document`() {
        assertEquals("# Moby-Dick\n*Herman Melville*\n", notesMarkdown(book(), emptyList()))
    }

    @Test
    fun `builds a file name a filesystem will accept`() {
        assertEquals("Moby-Dick - notes.md", notesFileName(book()))
        assertEquals("What Now - notes.md", notesFileName(book(title = "What/Now?")))
    }

    @Test
    fun `falls back when a title leaves nothing to name a file after`() {
        assertEquals("notes - notes.md", notesFileName(book(title = "///")))
    }

    private fun book(title: String = "Moby-Dick") = Book(
        id = "1",
        title = title,
        authors = listOf("Herman Melville"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/1.epub",
        coverUri = null,
        sizeBytes = 1_000,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )

    private fun highlight(text: String = "Call me Ishmael.", note: String? = null, progression: Double? = 0.1) =
        Highlight(
            id = 1,
            bookId = "1",
            locatorJson = "{}",
            text = text,
            tint = HighlightTint.Yellow,
            style = HighlightStyle.Highlight,
            note = note,
            progression = progression,
            createdAt = Instant.EPOCH
        )
}
