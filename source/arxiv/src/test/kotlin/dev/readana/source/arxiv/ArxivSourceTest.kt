package dev.readana.source.arxiv

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

class ArxivSourceTest {
    @Test
    fun `papers map to results with a one-line title`() = runTest {
        val source = source { respondXml(fixture("/arxiv-query.xml")) }

        val page = source.search(SearchQuery("attention")).orFail()

        assertEquals(2, page.results.size)
        val paper = page.results.first()
        // The feed wraps titles across lines; a list of books wants one line.
        assertEquals("Attention Is All You Need", paper.title)
        assertEquals("1706.03762v7", paper.recordId)
        assertEquals(listOf("Ashish Vaswani", "Noam Shazeer"), paper.authors)
        assertEquals(2017, paper.year)
        assertEquals(BookFormat.PDF, paper.format)
        assertEquals("http://arxiv.org/abs/1706.03762v7", paper.detailUrl)
        assertTrue(paper.description.orEmpty().startsWith("The dominant sequence transduction"))
    }

    @Test
    fun `a paper without a PDF link still gets one, because arXiv always serves it`() = runTest {
        val source = source { respondXml(fixture("/arxiv-query.xml")) }

        val page = source.search(SearchQuery("attention")).orFail()

        assertEquals("http://arxiv.org/pdf/1706.03762v7", page.results[0].extras["pdfUrl"])
        assertEquals("http://arxiv.org/pdf/2001.00001v1", page.results[1].extras["pdfUrl"])
    }

    @Test
    fun `paging asks for the next window and knows when it has run out`() = runTest {
        var url = ""
        val source = source { request ->
            url = request.url.toString()
            respondXml(fixture("/arxiv-query.xml"))
        }

        val page = source.search(SearchQuery("attention", page = 2)).orFail()

        assertTrue("start=20" in url, url)
        assertTrue("max_results=20" in url, url)
        assertTrue(page.hasMore, "22 of 137 seen")
    }

    @Test
    fun `configured categories narrow the query`() = runTest {
        var url = ""
        val source = source(config = { mapOf("categories" to "cs.AI, cs.LG") }) { request ->
            url = request.url.toString()
            respondXml(fixture("/arxiv-query.xml"))
        }

        source.search(SearchQuery("attention")).orFail()

        val decoded = java.net.URLDecoder.decode(url, Charsets.UTF_8.name())
        assertTrue("all:attention AND (cat:cs.AI OR cat:cs.LG)" in decoded, decoded)
    }

    @Test
    fun `a search that rules out PDFs never leaves the device`() = runTest {
        var called = false
        val source = source {
            called = true
            respondXml(fixture("/arxiv-query.xml"))
        }

        val page = source.search(SearchQuery("attention", formats = setOf(BookFormat.EPUB))).orFail()

        assertTrue(page.results.isEmpty())
        assertFalse(page.hasMore)
        assertFalse(called, "arXiv has nothing but PDFs, so there was nothing to ask")
    }

    @Test
    fun `resolving a download reuses the link found during the search`() = runTest {
        val source = source { respondXml(fixture("/arxiv-query.xml")) }
        val paper = source.search(SearchQuery("attention")).orFail().results.first()

        val request = source.resolveDownload(paper).orFail()

        assertEquals("http://arxiv.org/pdf/1706.03762v7", request.url)
        assertEquals(BookFormat.PDF, request.format)
        assertEquals("Attention Is All You Need - Ashish Vaswani, Noam Shazeer.pdf", request.suggestedFileName)
    }

    @Test
    fun `an unreachable service is a network error`() = runTest {
        val source = source { throw IOException("offline") }

        val outcome = assertIs<AppResult.Failure>(source.search(SearchQuery("attention")))

        assertIs<AppError.Network>(outcome.error)
    }

    @Test
    fun `a server error is reported with its status`() = runTest {
        val source = source { respondError(HttpStatusCode.ServiceUnavailable) }

        val outcome = assertIs<AppResult.Failure>(source.search(SearchQuery("attention")))

        assertEquals(AppError.Http(HttpStatusCode.ServiceUnavailable.value), outcome.error)
    }

    private fun source(config: suspend () -> Map<String, String> = { emptyMap() }, handler: MockRequestHandler) =
        ArxivSource(
            client = HttpClient(MockEngine(handler)),
            baseUrl = "https://arxiv.test",
            config = config
        )

    private fun fixture(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

private fun <T> AppResult<T>.orFail(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> fail("expected success but failed with ${error.message}")
}

private fun MockRequestHandleScope.respondXml(body: String) =
    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/atom+xml"))
