package dev.readana.feature.discover

import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultGroupingTest {
    @Test
    fun `copies of one book become one entry, however the catalogue wrote the names`() {
        val groups = ResultGrouping.group(
            listOf(
                result("a", "Pride and Prejudice", listOf("Jane Austen")),
                result("b", "Pride and prejudice.", listOf("Austen, Jane")),
                result("c", "The Pride and Prejudice", listOf("Jane Austen")),
                result("d", "Emma", listOf("Jane Austen"))
            )
        )

        assertEquals(2, groups.size)
        val pride = groups.single { it.best.title.contains("Pride", ignoreCase = true) }
        assertEquals(3, pride.editions.size)
        assertTrue(pride.hasChoices)
        assertFalse(groups.single { it.best.title == "Emma" }.hasChoices)
    }

    @Test
    fun `different books that share a title stay apart`() {
        val groups = ResultGrouping.group(
            listOf(
                result("a", "Ulysses", listOf("James Joyce")),
                result("b", "Ulysses", listOf("Alfred Tennyson"))
            )
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun `the copy shown is one the app can open, with a cover, and the fuller file`() {
        val groups = ResultGrouping.group(
            listOf(
                result("pdf", "Dune", listOf("Frank Herbert"), format = BookFormat.PDF, size = 90),
                result("small", "Dune", listOf("Frank Herbert"), cover = null, size = 10),
                result("best", "Dune", listOf("Frank Herbert"), size = 40),
                result("plain", "Dune", listOf("Frank Herbert"), cover = null, size = 80)
            )
        )

        val dune = groups.single()
        assertEquals("best", dune.best.recordId)
        // Everything stays reachable, and the PDF sorts last rather than being dropped.
        assertEquals(listOf("best", "plain", "small", "pdf"), dune.editions.map { it.recordId })
    }

    @Test
    fun `the languages on offer are listed once each`() {
        val groups = ResultGrouping.group(
            listOf(
                result("a", "Dune", listOf("Frank Herbert"), language = "en"),
                result("b", "Dune", listOf("Frank Herbert"), language = "nl"),
                result("c", "Dune", listOf("Frank Herbert"), language = "en"),
                result("d", "Dune", listOf("Frank Herbert"), language = null)
            )
        )

        assertEquals(setOf("en", "nl"), groups.single().languages.toSet())
    }

    private fun result(
        id: String,
        title: String,
        authors: List<String>,
        format: BookFormat? = BookFormat.EPUB,
        language: String? = "en",
        size: Long? = 1,
        cover: String? = "https://covers.test/$id.jpg"
    ) = SearchResult(
        sourceId = SourceId("annas-archive"),
        recordId = id,
        title = title,
        authors = authors,
        language = language,
        format = format,
        sizeBytes = size,
        coverUrl = cover
    )
}
