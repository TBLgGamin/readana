package dev.readana.source.opds

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.model.BookFormat
import dev.readana.source.api.CatalogEntry
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

class OpdsSourceTest {
    @Test
    fun `browsing an Atom feed maps books and drops what cannot be opened`() = runTest {
        val source = source { respondXml(fixture("/standard-ebooks.xml")) }

        val page = source.browse().orFail()

        assertEquals("Standard Ebooks", page.title)
        // Three entries in, one out: mobi has no BookFormat, and a borrow link is a DRM licence
        // this app cannot open, so offering either would be a download that always fails.
        val books = page.entries.filterIsInstance<CatalogEntry.Publication>()
        assertEquals(listOf("Pride and Prejudice"), books.map { it.result.title })
        val pride = books.single().result
        assertEquals(listOf("Jane Austen"), pride.authors)
        assertEquals(BookFormat.EPUB, pride.format)
        assertEquals(483_920L, pride.sizeBytes)
        assertEquals(1813, pride.year)
        assertEquals("en-GB", pride.language)
        assertEquals("Standard Ebooks", pride.publisher)
    }

    @Test
    fun `relative hrefs are resolved against the feed they came from`() = runTest {
        val source = source { respondXml(fixture("/standard-ebooks.xml")) }

        val page = source.browse().orFail()
        val pride = page.entries.filterIsInstance<CatalogEntry.Publication>().single().result

        assertEquals(
            "https://catalog.test/ebooks/jane-austen/pride-and-prejudice/downloads/pride.epub",
            pride.extras["href"]
        )
        assertEquals("https://catalog.test/images/pride-thumb.jpg", pride.coverUrl)
        assertEquals("https://catalog.test/feeds/opds/all?page=2", page.nextHref)
    }

    @Test
    fun `navigation entries become shelves rather than books`() = runTest {
        val source = source { respondXml(fixture("/navigation-feed.xml")) }

        val page = source.browse().orFail()
        val sections = page.entries.filterIsInstance<CatalogEntry.Section>()

        assertEquals(listOf("Fiction", "Recently added"), sections.map { it.title })
        assertEquals("https://catalog.test/fiction", sections.first().href)
        assertEquals("Novels and short stories", sections.first().subtitle)
        assertNull(page.nextHref)
    }

    @Test
    fun `an OPDS 2 feed reads the same as an Atom one`() = runTest {
        val source = source { respondJson(fixture("/opds2-feed.json")) }

        val page = source.browse().orFail()

        assertEquals("An OPDS 2 Catalog", page.title)
        val books = page.entries.filterIsInstance<CatalogEntry.Publication>()
        // The audiobook is dropped: no BookFormat can hold it.
        assertEquals(listOf("The Time Machine"), books.map { it.result.title })
        val wells = books.single().result
        assertEquals(listOf("H. G. Wells"), wells.authors)
        assertEquals("Heinemann", wells.publisher)
        assertEquals(1895, wells.year)
        assertEquals("https://catalog.test/covers/time.jpg", wells.coverUrl)
        assertEquals(
            listOf("New titles"),
            page.entries.filterIsInstance<CatalogEntry.Section>().map { it.title }
        )
        assertEquals("https://catalog.test/feed?page=2", page.nextHref)
    }

    @Test
    fun `the feed flavour is decided by content type, not by configuration`() = runTest {
        // A server that labels its OPDS 2 feed "application/json" is still an OPDS 2 feed.
        val source = source {
            respond(fixture("/opds2-feed.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        assertEquals("An OPDS 2 Catalog", source.browse().orFail().title)
    }

    @Test
    fun `searching fills the feed's own template and drops unused placeholders`() = runTest {
        val requested = mutableListOf<String>()
        val source = source { request ->
            requested += request.url.toString()
            respondXml(fixture("/standard-ebooks.xml"))
        }

        val page = source.search(SearchQuery("pride & prejudice")).orFail()

        assertEquals(1, page.results.size)
        assertTrue(page.hasMore, "the feed linked a next page")
        val searchUrl = requested.last()
        assertTrue("query=pride+%26+prejudice" in searchUrl, "search URL was $searchUrl")
        assertTrue("startIndex" !in searchUrl, "unfilled placeholder was sent: $searchUrl")
    }

    @Test
    fun `a feed with no search link says so rather than returning nothing`() = runTest {
        val source = source {
            respondXml(
                """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Plain</title></feed>"""
            )
        }

        val outcome = assertIs<AppResult.Failure>(source.search(SearchQuery("x")))

        assertIs<AppError.Unsupported>(outcome.error)
    }

    @Test
    fun `a feed that needs a sign-in reports that, not a generic failure`() = runTest {
        val source = source { respondError(HttpStatusCode.Unauthorized) }

        val outcome = assertIs<AppResult.Failure>(source.browse())

        assertIs<AppError.Unauthorized>(outcome.error)
    }

    @Test
    fun `credentials become a basic auth header on both the feed and the download`() = runTest {
        val seen = mutableListOf<String?>()
        val source = source(config = { mapOf("username" to "ada", "password" to "lovelace") }) { request ->
            seen += request.headers[HttpHeaders.Authorization]
            respondXml(fixture("/standard-ebooks.xml"))
        }

        val page = source.browse().orFail()
        val result = page.entries.filterIsInstance<CatalogEntry.Publication>().single().result
        val request = source.resolveDownload(result).orFail()

        // "ada:lovelace", base64-encoded.
        assertEquals(listOf<String?>("Basic YWRhOmxvdmVsYWNl"), seen.toList())
        assertEquals("Basic YWRhOmxvdmVsYWNl", request.headers[HttpHeaders.Authorization])
        assertEquals("Pride and Prejudice - Jane Austen.epub", request.suggestedFileName)
        assertEquals(BookFormat.EPUB, request.format)
    }

    @Test
    fun `resolving a download never touches the network`() = runTest {
        var calls = 0
        val source = source {
            calls++
            respondXml(fixture("/standard-ebooks.xml"))
        }
        val result = source.browse().orFail()
            .entries.filterIsInstance<CatalogEntry.Publication>().single().result

        assertIs<AppResult.Success<*>>(source.resolveDownload(result))

        assertEquals(1, calls, "resolving re-fetched the feed")
    }

    @Test
    fun `a feed that will not parse is a parse error`() = runTest {
        val source = source { respondJson("{ not json at all") }

        val outcome = assertIs<AppResult.Failure>(source.browse())

        assertIs<AppError.Parse>(outcome.error)
    }

    @Test
    fun `an unreachable catalog is a network error`() = runTest {
        val source = source { throw IOException("offline") }

        val outcome = assertIs<AppResult.Failure>(source.browse())

        assertIs<AppError.Network>(outcome.error)
    }

    private fun source(config: suspend () -> Map<String, String> = { emptyMap() }, handler: MockRequestHandler) =
        OpdsSource(
            client = HttpClient(MockEngine(handler)),
            catalogId = "catalog-test",
            catalogName = "Test Catalog",
            feedUrl = "https://catalog.test/feed",
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

private fun MockRequestHandleScope.respondJson(body: String) =
    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/opds+json"))
