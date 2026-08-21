package dev.readana.source.annas

import dev.readana.core.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnasSearchParserTest {
    private val html = checkNotNull(javaClass.getResource("/annas-search.html")).readText()
    private val parked = checkNotNull(javaClass.getResource("/parked-domain.html")).readText()

    @Test
    fun `extracts one result per md5 with metadata from the surrounding text`() {
        val results = AnnasSearchParser.parse(html, "https://mirror.test", AnnasArchiveSource.ID)

        assertEquals(2, results.size)
        val pride = results[0]
        assertEquals("0123456789abcdef0123456789abcdef", pride.recordId)
        assertEquals("Pride and Prejudice", pride.title)
        assertEquals(listOf("Jane Austen"), pride.authors)
        assertEquals("en", pride.language)
        assertEquals(BookFormat.EPUB, pride.format)
        assertEquals((0.8 * 1024 * 1024).toLong(), pride.sizeBytes)
        assertEquals(2003, pride.year)
        assertEquals("Penguin Classics", pride.publisher)
        assertEquals("https://covers.example/pride.jpg", pride.coverUrl)
        assertEquals("https://mirror.test/md5/0123456789abcdef0123456789abcdef", pride.detailUrl)

        val achterhuis = results[1]
        assertEquals(listOf("Anne Frank", "Otto Frank"), achterhuis.authors)
        assertEquals(BookFormat.PDF, achterhuis.format)
        assertEquals("nl", achterhuis.language)
        assertEquals(1947, achterhuis.year)
        assertEquals(null, achterhuis.coverUrl)
    }

    @Test
    fun `recognises real pages and rejects parked domains`() {
        assertTrue(AnnasSearchParser.looksLikeAnnasArchive(html))
        assertFalse(AnnasSearchParser.looksLikeAnnasArchive(parked))
    }
}
