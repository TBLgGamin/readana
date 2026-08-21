package dev.readana.source.annas

import dev.readana.core.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnasSearchParserTest {
    private val table = checkNotNull(javaClass.getResource("/annas-search-table.html")).readText()
    private val cards = checkNotNull(javaClass.getResource("/annas-search-list.html")).readText()
    private val parked = checkNotNull(javaClass.getResource("/parked-domain.html")).readText()

    @Test
    fun `reads every column of the table layout`() {
        val page = AnnasSearchParser.parse(table, "https://mirror.test", AnnasArchiveSource.ID)

        assertEquals(2, page.results.size)
        val pride = page.results[0]
        assertEquals("0123456789abcdef0123456789abcdef", pride.recordId)
        assertEquals("Pride and Prejudice", pride.title)
        assertEquals(listOf("Jane Austen"), pride.authors)
        assertEquals("Penguin Classics", pride.publisher)
        assertEquals(2003, pride.year)
        assertEquals("en", pride.language)
        assertEquals(BookFormat.EPUB, pride.format)
        assertEquals((0.8 * 1024 * 1024).toLong(), pride.sizeBytes)
        assertEquals("https://mirror.test/covers/pride.jpg", pride.coverUrl)
        assertEquals("https://mirror.test/md5/0123456789abcdef0123456789abcdef", pride.detailUrl)
        assertEquals("lgli/lgrs/zlib", pride.extras[AnnasSearchParser.EXTRA_COLLECTIONS])
        assertEquals("epub", pride.extras[AnnasSearchParser.EXTRA_EXTENSION])

        val achterhuis = page.results[1]
        assertEquals("Het Achterhuis", achterhuis.title)
        assertEquals(BookFormat.PDF, achterhuis.format)
        assertEquals("nl", achterhuis.language)
        assertEquals(1947, achterhuis.year)
        assertNull(achterhuis.coverUrl)
    }

    @Test
    fun `reads the card layout, including records the site ships commented out`() {
        val page = AnnasSearchParser.parse(cards, "https://mirror.test", AnnasArchiveSource.ID)

        assertEquals(2, page.results.size)
        val pride = page.results[0]
        assertEquals("0123456789abcdef0123456789abcdef", pride.recordId)
        assertEquals("Pride and Prejudice", pride.title)
        assertEquals(listOf("Jane Austen"), pride.authors)
        assertEquals("Penguin Classics", pride.publisher)
        assertEquals("en", pride.language)
        assertEquals(BookFormat.EPUB, pride.format)
        assertEquals((0.8 * 1024 * 1024).toLong(), pride.sizeBytes)
        assertEquals(2003, pride.year)
        assertEquals("https://mirror.test/covers/pride.jpg", pride.coverUrl)

        // Only reachable through the comment node the page un-comments in JavaScript.
        val achterhuis = page.results[1]
        assertEquals("fedcba9876543210fedcba9876543210", achterhuis.recordId)
        assertEquals("Het Achterhuis", achterhuis.title)
        assertEquals(BookFormat.PDF, achterhuis.format)
        assertEquals("nl", achterhuis.language)
        assertEquals((12.5 * 1024 * 1024).toLong(), achterhuis.sizeBytes)
    }

    @Test
    fun `drops formats the app cannot open but still counts them`() {
        listOf(table, cards).forEach { html ->
            val page = AnnasSearchParser.parse(html, "https://mirror.test", AnnasArchiveSource.ID)

            assertTrue(page.results.none { it.title == "Kindle Only Edition" })
            // The record was there; dropping it must not read as "the page was short".
            assertEquals(3, page.recordCount)
        }
    }

    @Test
    fun `recognises real pages and rejects parked domains`() {
        assertTrue(AnnasSearchParser.looksLikeAnnasArchive(table))
        assertTrue(AnnasSearchParser.looksLikeAnnasArchive(cards))
        assertFalse(AnnasSearchParser.looksLikeAnnasArchive(parked))
    }
}
