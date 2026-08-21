package dev.readana.core.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WikipediaPictureSearchTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `an exact article title wins, without a search`() = runTest {
        val search = search { respond(BY_TITLE, HttpStatusCode.OK, jsonHeaders) }

        val found = search.find("HMS Victory")

        assertEquals("https://upload.wikimedia.org/victory.jpg", found?.imageUrl)
        assertEquals("https://en.wikipedia.org/wiki/HMS_Victory", found?.pageUrl)
        assertEquals("HMS Victory", found?.title)
        assertEquals(1, requests.size)
        assertEquals("HMS Victory", requests.single().url.parameters["titles"])
    }

    @Test
    fun `a subject with no article of its own falls back to the search index`() = runTest {
        val search = search { request ->
            if (request.url.parameters["titles"] != null) {
                respond(MISSING, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(RESULT, HttpStatusCode.OK, jsonHeaders)
            }
        }

        val found = search.find("HMS Victory")

        // Best-ranked match with a picture, not merely the first one the API happened to list.
        assertEquals("https://upload.wikimedia.org/victory.jpg", found?.imageUrl)
        assertEquals(2, requests.size)
    }

    @Test
    fun `a result that has nothing to do with the subject is discarded`() = runTest {
        val search = search { request ->
            if (request.url.parameters["titles"] != null) {
                respond(MISSING, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(UNRELATED, HttpStatusCode.OK, jsonHeaders)
            }
        }

        assertNull(search.find("Hestia"))
    }

    @Test
    fun `identifies itself, as Wikimedia requires`() = runTest {
        search { respond(BY_TITLE, HttpStatusCode.OK, jsonHeaders) }.find("HMS Victory")

        assertTrue(requests.single().headers[HttpHeaders.UserAgent].orEmpty().startsWith("Readana/"))
    }

    @Test
    fun `a result with no picture is not offered`() = runTest {
        val search = search { respond(NO_IMAGE, HttpStatusCode.OK, jsonHeaders) }

        assertNull(search.find("Obscure"))
    }

    @Test
    fun `a blank subject is not looked up at all`() = runTest {
        val search = search { respond(BY_TITLE, HttpStatusCode.OK, jsonHeaders) }

        assertNull(search.find("   "))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a lookup that fails is simply no picture`() = runTest {
        assertNull(search { throw IOException("offline") }.find("HMS Victory"))
        assertNull(search { respondError(HttpStatusCode.ServiceUnavailable) }.find("HMS Victory"))
        assertNull(search { respond("not json", HttpStatusCode.OK, jsonHeaders) }.find("HMS Victory"))
    }

    private fun search(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        WikipediaPictureSearch(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                }
            )
        )

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        const val BY_TITLE = """
            {"query":{"pages":[
              {"title":"HMS Victory","thumbnail":{"source":"https://upload.wikimedia.org/victory.jpg"}}
            ]}}
        """

        const val MISSING = """{"query":{"pages":[{"title":"HMS Victory","missing":true}]}}"""

        const val UNRELATED = """
            {"query":{"pages":[
              {"title":"Agni","index":1,"thumbnail":{"source":"https://upload.wikimedia.org/agni.jpg"}}
            ]}}
        """

        // Wikipedia answers out of order; "index" is the ranking, and the best match wins.
        val RESULT = """
            {"query":{"pages":[
              {"title":"HMS Victory (1737)","index":2,
               "thumbnail":{"source":"https://upload.wikimedia.org/wreck.jpg"}},
              {"title":"Society for Nautical Research","index":3},
              {"title":"HMS Victory","index":1,
               "thumbnail":{"source":"https://upload.wikimedia.org/victory.jpg"}}
            ]}}
        """.trimIndent()

        const val NO_IMAGE = """{"query":{"pages":[{"title":"Obscure","index":1}]}}"""
    }
}
