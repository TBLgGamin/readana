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
    fun `finds a picture and where it came from`() = runTest {
        val search = search { respond(RESULT, HttpStatusCode.OK, jsonHeaders) }

        val found = search.find("HMS Victory")

        assertEquals("https://upload.wikimedia.org/victory.jpg", found?.imageUrl)
        assertEquals("https://en.wikipedia.org/wiki/HMS_Victory", found?.pageUrl)
        assertEquals("HMS Victory", found?.title)
    }

    @Test
    fun `identifies itself, as Wikimedia requires`() = runTest {
        search { respond(RESULT, HttpStatusCode.OK, jsonHeaders) }.find("HMS Victory")

        val sent = requests.single()
        assertTrue(sent.headers[HttpHeaders.UserAgent].orEmpty().startsWith("Readana/"))
        assertEquals("HMS Victory", sent.url.parameters["gsrsearch"])
    }

    @Test
    fun `a result with no picture is not offered`() = runTest {
        val search = search { respond(NO_IMAGE, HttpStatusCode.OK, jsonHeaders) }

        assertNull(search.find("something obscure"))
    }

    @Test
    fun `a blank subject is not looked up at all`() = runTest {
        val search = search { respond(RESULT, HttpStatusCode.OK, jsonHeaders) }

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
