package dev.readana.source.openlibrary

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

class OpenLibrarySourceTest {
    @Test
    fun `only publicly downloadable scans are offered`() = runTest {
        val source = source { respondJson(fixture("/openlibrary-search.json")) }

        val page = source.search(SearchQuery("dahl")).orFail()

        // Borrowable and print-disabled scans end in a DRM licence this app cannot open, and a
        // work with no scan at all has no file; none of the three are worth listing.
        assertEquals(listOf("Fantastic Mr Fox"), page.results.map { it.title })
        val fox = page.results.single()
        assertEquals("OL45804W", fox.recordId)
        assertEquals(listOf("Roald Dahl"), fox.authors)
        assertEquals(1970, fox.year)
        assertEquals("eng", fox.language)
        assertEquals("Puffin", fox.publisher)
        assertEquals("https://covers.test/b/id/6498519-M.jpg", fox.coverUrl)
        assertEquals("https://openlibrary.test/works/OL45804W", fox.detailUrl)
        assertEquals("fantasticmrfox0000dahl", fox.extras["archiveId"])
    }

    @Test
    fun `there is more to fetch while the query has matches left`() = runTest {
        val source = source { respondJson(fixture("/openlibrary-search.json")) }

        val page = source.search(SearchQuery("dahl")).orFail()

        // Four of forty-two seen. Counting what survived filtering instead would stop paging as
        // soon as one page happened to be all lending-only.
        assertTrue(page.hasMore)
    }

    @Test
    fun `the search asks for one page of only the fields it uses`() = runTest {
        var url = ""
        val source = source { request ->
            url = request.url.toString()
            respondJson(fixture("/openlibrary-search.json"))
        }

        source.search(SearchQuery("dahl", page = 3)).orFail()

        assertTrue("q=dahl" in url, url)
        assertTrue("page=3" in url, url)
        assertTrue("ebook_access" in url, "the access field must be requested or filtering breaks")
    }

    @Test
    fun `resolving prefers the EPUB derivative over the scanned PDF`() = runTest {
        val source = source { request ->
            if ("metadata" in request.url.encodedPath) {
                respondJson(fixture("/archive-metadata.json"))
            } else {
                respondJson(fixture("/openlibrary-search.json"))
            }
        }

        val request = source.resolveDownload(result()).orFail()

        assertEquals(BookFormat.EPUB, request.format)
        assertEquals(
            "https://archive.test/download/fantasticmrfox0000dahl/fantasticmrfox0000dahl.epub",
            request.url
        )
        assertEquals(412_000L, request.expectedSizeBytes)
        assertEquals("Fantastic Mr Fox - Roald Dahl.epub", request.suggestedFileName)
    }

    @Test
    fun `an item with only a PDF still resolves`() = runTest {
        val source = source { respondJson(fixture("/archive-metadata-pdf-only.json")) }

        val request = source.resolveDownload(result()).orFail()

        assertEquals(BookFormat.PDF, request.format)
        assertEquals(
            "https://archive.test/download/fantasticmrfox0000dahl/scan_text.pdf",
            request.url
        )
    }

    @Test
    fun `an item holding nothing readable is reported as not found`() = runTest {
        val source = source { respondJson("""{"files":[{"name":"scan_djvu.txt","format":"DjVuTXT"}]}""") }

        val outcome = assertIs<AppResult.Failure>(source.resolveDownload(result()))

        assertIs<AppError.NotFound>(outcome.error)
    }

    @Test
    fun `a result with no archive scan cannot be downloaded`() = runTest {
        val source = source { respondJson("{}") }

        val outcome = assertIs<AppResult.Failure>(source.resolveDownload(result(extras = emptyMap())))

        assertIs<AppError.NotFound>(outcome.error)
    }

    @Test
    fun `an unreachable service is a network error`() = runTest {
        val source = source { throw IOException("offline") }

        val outcome = assertIs<AppResult.Failure>(source.search(SearchQuery("dahl")))

        assertIs<AppError.Network>(outcome.error)
    }

    @Test
    fun `a server error is reported with its status`() = runTest {
        val source = source { respondError(HttpStatusCode.BadGateway) }

        val outcome = assertIs<AppResult.Failure>(source.search(SearchQuery("dahl")))

        assertEquals(AppError.Http(HttpStatusCode.BadGateway.value), outcome.error)
    }

    @Test
    fun `a malformed payload is a parse error`() = runTest {
        val source = source { respondJson("not json") }

        val outcome = assertIs<AppResult.Failure>(source.search(SearchQuery("dahl")))

        assertIs<AppError.Parse>(outcome.error)
    }

    private fun result(extras: Map<String, String> = mapOf("archiveId" to "fantasticmrfox0000dahl")) = SearchResult(
        sourceId = SourceId("openlibrary"),
        recordId = "OL45804W",
        title = "Fantastic Mr Fox",
        authors = listOf("Roald Dahl"),
        format = BookFormat.PDF,
        extras = extras
    )

    private fun source(handler: MockRequestHandler) = OpenLibrarySource(
        client = HttpClient(MockEngine(handler)),
        baseUrl = "https://openlibrary.test",
        archiveUrl = "https://archive.test",
        coversUrl = "https://covers.test"
    )

    private fun fixture(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

private fun <T> AppResult<T>.orFail(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> fail("expected success but failed with ${error.message}")
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
