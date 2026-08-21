package dev.readana.source.trending

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.errorOrNull
import dev.readana.core.common.result.getOrNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OpenLibraryTrendingTest {
    private val payload = """
        {"works":[
          {"title":"Atomic Habits","author_name":["James Clear"],"cover_i":12539702},
          {"title":"The Psychology of Money","author_name":["Morgan Housel"],"cover_i":10389354},
          {"title":"No Cover Here","author_name":["Anon"]},
          {"author_name":["Missing Title"],"cover_i":1}
        ]}
    """.trimIndent()

    @Test
    fun `reads titles, authors and cover art`() = runTest {
        val books = assertNotNull(source { respond(payload, HttpStatusCode.OK, jsonHeaders) }.trending().getOrNull())

        assertEquals(3, books.size, "the entry without a title is dropped")
        val first = books.first()
        assertEquals("Atomic Habits", first.title)
        assertEquals(listOf("James Clear"), first.authors)
        assertEquals("https://covers.openlibrary.org/b/id/12539702-M.jpg", first.coverUrl)
        assertNull(books[2].coverUrl, "a work without cover art still shows, just without a picture")
    }

    @Test
    fun `builds a query that finds the same book on a book source`() = runTest {
        val books = assertNotNull(source { respond(payload, HttpStatusCode.OK, jsonHeaders) }.trending().getOrNull())

        assertEquals("Atomic Habits James Clear", books.first().searchQuery)
    }

    @Test
    fun `asks for the requested number of books`() = runTest {
        var url = ""
        val client = HttpClient(
            MockEngine { request ->
                url = request.url.toString()
                respond(payload, HttpStatusCode.OK, jsonHeaders)
            }
        )

        OpenLibraryTrending(client).trending(limit = 12)

        assertTrue("limit=12" in url, "unexpected url: $url")
        assertTrue(url.startsWith("https://openlibrary.org/trending/weekly.json"), url)
    }

    @Test
    fun `an http error is reported, not thrown`() = runTest {
        val error = source { respondError(HttpStatusCode.ServiceUnavailable) }.trending().errorOrNull()

        assertEquals(503, assertIs<AppError.Http>(error).statusCode)
    }

    @Test
    fun `being offline is reported as a network failure`() = runTest {
        val error = source { throw IOException("offline") }.trending().errorOrNull()

        assertIs<AppError.Network>(error)
    }

    @Test
    fun `unexpected json does not crash the shelf`() = runTest {
        val error = source { respond("not json at all", HttpStatusCode.OK, jsonHeaders) }.trending().errorOrNull()

        assertIs<AppError.Network>(error)
    }

    private fun source(handler: io.ktor.client.engine.mock.MockRequestHandler) =
        OpenLibraryTrending(HttpClient(MockEngine(handler)))

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
}
