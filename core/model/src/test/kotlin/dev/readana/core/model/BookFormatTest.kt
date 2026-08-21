package dev.readana.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookFormatTest {
    @Test
    fun `resolves extensions case-insensitively with or without a dot`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromExtension(".EPUB"))
        assertEquals(BookFormat.PDF, BookFormat.fromExtension("pdf"))
        assertNull(BookFormat.fromExtension("mobi"))
    }

    @Test
    fun `resolves mime types ignoring parameters`() {
        assertEquals(BookFormat.PDF, BookFormat.fromMimeType("application/pdf; charset=binary"))
        assertEquals(BookFormat.EPUB, BookFormat.fromMimeType("application/epub+zip"))
        assertNull(BookFormat.fromMimeType("text/plain"))
    }
}
