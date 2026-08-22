package dev.readana.core.data.dictionary

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

class WiktionaryDictionaryTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `reads the English senses of a word`() = runTest {
        val dictionary = dictionary { respond(GAM, HttpStatusCode.OK, jsonHeaders) }

        val entry = dictionary.define("gam")

        assertEquals("gam", entry?.word)
        assertEquals(2, entry?.senses?.size)
        assertEquals("Noun", entry?.senses?.first()?.partOfSpeech)
        assertEquals("A herd of whales.", entry?.senses?.first()?.definition)
        assertEquals("a gam of sperm whales", entry?.senses?.first()?.example)
    }

    @Test
    fun `strips the markup definitions come wrapped in`() = runTest {
        val dictionary = dictionary { respond(GAM, HttpStatusCode.OK, jsonHeaders) }

        val definition = dictionary.define("gam")?.senses?.last()?.definition

        assertEquals("To meet socially, as whaling ships & their crews.", definition)
    }

    @Test
    fun `leaves the reader's punctuation out of the lookup`() = runTest {
        val dictionary = dictionary { respond(GAM, HttpStatusCode.OK, jsonHeaders) }

        dictionary.define("“Gam,”")

        assertTrue(requests.single().url.encodedPath.endsWith("/gam"), requests.single().url.toString())
    }

    @Test
    fun `does not spend a request on a phrase`() = runTest {
        val dictionary = dictionary { respond(GAM, HttpStatusCode.OK, jsonHeaders) }

        assertNull(dictionary.define("a gam of sperm whales"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a word with no entry is simply not defined`() = runTest {
        val dictionary = dictionary { respondError(HttpStatusCode.NotFound) }

        assertNull(dictionary.define("quommoxed"))
    }

    @Test
    fun `a word with no English section is not defined either`() = runTest {
        val dictionary = dictionary { respond(FRENCH_ONLY, HttpStatusCode.OK, jsonHeaders) }

        assertNull(dictionary.define("chaloupe"))
    }

    @Test
    fun `a failed request is a missing definition, not a crash`() = runTest {
        val dictionary = dictionary { throw IOException("no network") }

        assertNull(dictionary.define("gam"))
    }

    @Test
    fun `nonsense in the response body is a missing definition too`() = runTest {
        val dictionary = dictionary { respond("<html>not json</html>", HttpStatusCode.OK, jsonHeaders) }

        assertNull(dictionary.define("gam"))
    }

    @Test
    fun `identifies itself, because Wikimedia turns anonymous callers away`() = runTest {
        val dictionary = dictionary { respond(GAM, HttpStatusCode.OK, jsonHeaders) }

        dictionary.define("gam")

        assertTrue(requests.single().headers[HttpHeaders.UserAgent].orEmpty().startsWith("Readana/"))
    }

    private fun dictionary(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        WiktionaryDictionary(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                }
            )
        )

    private val jsonHeaders get() = headersOf(HttpHeaders.ContentType, "application/json")

    private companion object {
        val GAM = """
            {
              "en": [
                {
                  "partOfSpeech": "Noun",
                  "language": "English",
                  "definitions": [
                    {
                      "definition": "A herd of <a href=\"/wiki/whale\">whales</a>.",
                      "examples": ["a gam of sperm whales"]
                    }
                  ]
                },
                {
                  "partOfSpeech": "Verb",
                  "language": "English",
                  "definitions": [
                    { "definition": "To meet socially, as whaling ships &amp; their crews." }
                  ]
                }
              ]
            }
        """.trimIndent()

        val FRENCH_ONLY = """
            {
              "fr": [
                {
                  "partOfSpeech": "Nom",
                  "language": "French",
                  "definitions": [{ "definition": "Une petite embarcation." }]
                }
              ]
            }
        """.trimIndent()
    }
}
