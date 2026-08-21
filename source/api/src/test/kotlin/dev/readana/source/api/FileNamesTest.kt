package dev.readana.source.api

import dev.readana.core.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileNamesTest {
    @Test
    fun `strips characters that are illegal on Android and Windows file systems`() {
        val name = FileNames.suggest("What: is/this?", listOf("A*B"), BookFormat.EPUB)

        assertEquals("What isthis - AB.epub", name)
    }

    @Test
    fun `caps overly long names and never ends the stem with a dot`() {
        val name = FileNames.suggest("x".repeat(300) + ".", emptyList(), BookFormat.PDF)

        assertTrue(name.length <= 125)
        assertTrue(name.endsWith("x.pdf"))
    }

    @Test
    fun `falls back to a generic stem for blank titles`() {
        assertEquals("book.epub", FileNames.suggest("   ", emptyList(), BookFormat.EPUB))
    }
}
