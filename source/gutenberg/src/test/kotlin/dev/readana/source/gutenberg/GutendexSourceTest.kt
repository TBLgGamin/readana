package dev.readana.source.gutenberg

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.errorOrNull
import dev.readana.core.common.result.getOrNull
import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GutendexSourceTest {
    private val fixture = checkNotNull(javaClass.getResource("/gutendex-search.json")).readText()

    @Test
    fun `maps gutendex payload to search results`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val source = source { request ->
            requests += request
            respond(fixture, HttpStatusCode.OK, jsonHeaders)
        }

        val page = assertNotNull(source.search(SearchQuery("pride", language = "en")).getOrNull())

        assertEquals("pride", requests.single().url.parameters["search"])
        assertEquals("en", requests.single().url.parameters["languages"])
        assertTrue(page.hasMore)
        val result = page.results.single()
        assertEquals("1342", result.recordId)
        assertEquals("Pride and Prejudice", result.title)
        assertEquals(listOf("Jane Austen"), result.authors)
        assertEquals(BookFormat.EPUB, result.format)
        assertEquals(
            "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
            result.coverUrl
        )
        assertEquals(
            "https://www.gutenberg.org/ebooks/1342.epub3.images",
            result.extras["downloadUrl"]
        )
    }

    @Test
    fun `records without a wanted format are dropped`() = runTest {
        val source = source { respond(fixture, HttpStatusCode.OK, jsonHeaders) }

        val page =
            assertNotNull(
                source.search(SearchQuery("pride", formats = setOf(BookFormat.PDF))).getOrNull()
            )

        assertTrue(page.results.isEmpty())
    }

    @Test
    fun `page past the end is an empty page`() = runTest {
        val source =
            source {
                respond("""{"detail":"Invalid page."}""", HttpStatusCode.NotFound, jsonHeaders)
            }

        val page = assertNotNull(source.search(SearchQuery("pride", page = 99)).getOrNull())

        assertTrue(page.results.isEmpty())
        assertEquals(false, page.hasMore)
    }

    @Test
    fun `server errors surface as Http failures`() = runTest {
        val source = source { respond("boom", HttpStatusCode.BadGateway) }

        val error = source.search(SearchQuery("x")).errorOrNull()

        assertEquals(AppError.Http(502), error)
    }

    @Test
    fun `transport errors surface as Network failures`() = runTest {
        val source = source { throw IOException("offline") }

        assertIs<AppError.Network>(source.search(SearchQuery("x")).errorOrNull())
    }

    @Test
    fun `malformed payloads surface as Parse failures`() = runTest {
        val source = source { respond("<html>not json</html>", HttpStatusCode.OK) }

        assertIs<AppError.Parse>(source.search(SearchQuery("x")).errorOrNull())
    }

    @Test
    fun `resolveDownload reuses the url captured during search`() = runTest {
        val source = source { respond(fixture, HttpStatusCode.OK, jsonHeaders) }
        val result = assertNotNull(source.search(SearchQuery("pride")).getOrNull()).results.single()

        val request = assertIs<AppResult.Success<*>>(source.resolveDownload(result)).value
            as dev.readana.source.api.DownloadRequest

        assertEquals("https://www.gutenberg.org/ebooks/1342.epub3.images", request.url)
        assertEquals("Pride and Prejudice - Jane Austen.epub", request.suggestedFileName)
        assertEquals(false, request.requiresBrowser)
    }

    private fun source(handler: MockRequestHandler) =
        GutendexSource(HttpClient(MockEngine(handler)), baseUrl = "https://gutendex.test")

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
}
