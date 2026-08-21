package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.errorOrNull
import dev.readana.core.common.result.getOrNull
import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
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

class AnnasArchiveSourceTest {
    private val searchHtml = checkNotNull(javaClass.getResource("/annas-search-table.html")).readText()
    private val parkedHtml = checkNotNull(javaClass.getResource("/parked-domain.html")).readText()
    private val mirrors = listOf("https://one.test", "https://two.test", "https://three.test")

    @Test
    fun `fails over from dead and parked mirrors and remembers the working one`() = runTest {
        val hosts = mutableListOf<String>()
        val resolver = MirrorResolver()
        val source = source(resolver) { request ->
            hosts += request.url.host
            when (request.url.host) {
                "one.test" -> throw IOException("connection refused")
                "two.test" -> respond(parkedHtml, HttpStatusCode.OK, htmlHeaders)
                else -> respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val page = assertNotNull(source.search(SearchQuery("pride")).getOrNull())
        assertEquals(2, page.results.size)
        assertEquals(listOf("one.test", "two.test", "three.test"), hosts)
        assertEquals("https://three.test", resolver.preferredMirror)

        source.search(SearchQuery("again"))
        assertEquals("three.test", hosts.last())
        assertEquals(4, hosts.size)
    }

    @Test
    fun `search sends the expected query parameters`() = runTest {
        var url = ""
        val source = source { request ->
            url = request.url.toString()
            respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
        }

        source.search(
            SearchQuery("pride", formats = setOf(BookFormat.EPUB), language = "en", page = 2)
        )

        assertTrue(url.startsWith("https://one.test/search?"))
        assertTrue("q=pride" in url)
        assertTrue("display=table" in url, "the table layout is the one we parse: $url")
        assertTrue("ext=epub" in url)
        assertTrue("lang=en" in url)
        assertTrue("page=2" in url)
    }

    @Test
    fun `a search for both formats asks for both`() = runTest {
        var url = ""
        val source = source { request ->
            url = request.url.toString()
            respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
        }

        // The default query wants everything the app can open; the filter is repeatable.
        source.search(SearchQuery("pride"))

        assertTrue("ext=epub" in url, url)
        assertTrue("ext=pdf" in url, url)
    }

    @Test
    fun `a rate-limiting mirror is skipped rather than reported`() = runTest {
        val hosts = mutableListOf<String>()
        val source = source { request ->
            hosts += request.url.host
            // Anna's Archive throttles per IP and answers 403; the next mirror may still serve us.
            if (request.url.host == "one.test") {
                respond("blocked", HttpStatusCode.Forbidden)
            } else {
                respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val page = assertNotNull(source.search(SearchQuery("pride")).getOrNull())

        assertEquals(2, page.results.size)
        assertEquals(listOf("one.test", "two.test"), hosts)
    }

    @Test
    fun `every mirror rate-limiting explains what to do about it`() = runTest {
        val source = source { respond("blocked", HttpStatusCode.TooManyRequests) }

        val error = assertIs<AppError.Http>(source.search(SearchQuery("pride")).errorOrNull())

        assertEquals(429, error.statusCode)
        assertTrue("rate-limiting" in error.message, "unhelpful message: ${error.message}")
    }

    @Test
    fun `a mirror that only redirects is skipped`() = runTest {
        val hosts = mutableListOf<String>()
        val source = source { request ->
            hosts += request.url.host
            if (request.url.host == "one.test") {
                // What .li serves: mentions the site, carries no results, just bounces the browser.
                respond(
                    """<html><head><title>annas-archive.li</title>""" +
                        """<meta http-equiv="refresh" content="0; URL=http://annas-archive.li/search?q=x"/>""" +
                        """</head><body>Anna's Archive</body></html>""",
                    HttpStatusCode.OK,
                    htmlHeaders
                )
            } else {
                respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val page = assertNotNull(source.search(SearchQuery("pride")).getOrNull())

        assertEquals(2, page.results.size, "the interstitial must not pass as an empty result page")
        assertEquals(listOf("one.test", "two.test"), hosts)
    }

    @Test
    fun `all mirrors failing yields a Network failure`() = runTest {
        val source = source { throw IOException("offline") }

        assertIs<AppError.Network>(source.search(SearchQuery("x")).errorOrNull())
    }

    @Test
    fun `without a secret key downloads are handed to the browser`() = runTest {
        val source = source(config = AnnasConfig(mirrors)) { error("no request expected") }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertTrue(request.requiresBrowser)
        assertEquals("https://one.test/md5/0123456789abcdef0123456789abcdef", request.url)
        assertEquals("Pride and Prejudice - Jane Austen.epub", request.suggestedFileName)
        assertNull(request.browserLogin, "there is no key to sign in with")
    }

    @Test
    fun `a spent quota falls back to the browser and offers a key login`() = runTest {
        val source = source(config = AnnasConfig(mirrors, secretKey = "s3cr3t")) {
            respond(
                """{"download_url":null,"account_fast_download_info":""" +
                    """{"downloads_left":0,"downloads_per_day":100}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertTrue(request.requiresBrowser)
        assertEquals("https://one.test/md5/0123456789abcdef0123456789abcdef", request.url)
        assertEquals(0, request.quota?.remaining)
        assertEquals(100, request.quota?.perDay)
        assertEquals(true, request.quota?.isExhausted)
        // Anna's has no password: posting the membership key to /account/ is the whole login.
        assertEquals("https://one.test/account/", request.browserLogin?.url)
        assertEquals("key=s3cr3t", request.browserLogin?.formBody)
    }

    @Test
    fun `with a secret key the fast download api is used`() = runTest {
        var url = ""
        val source = source(config = AnnasConfig(mirrors, secretKey = "s3cr3t")) { request ->
            url = request.url.toString()
            respond(
                """{"download_url":"https://fast.test/dl/abc.epub",""" +
                    """"account_fast_download_info":{"downloads_left":9}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertEquals(
            "https://one.test/dyn/api/fast_download.json?md5=0123456789abcdef0123456789abcdef&key=s3cr3t",
            url
        )
        assertEquals("https://fast.test/dl/abc.epub", request.url)
        assertEquals(false, request.requiresBrowser)
        assertEquals(9, request.quota?.remaining)
    }

    @Test
    fun `an invalid key is reported as Unauthorized`() = runTest {
        val source = source(config = AnnasConfig(mirrors, secretKey = "bad")) {
            respond("""{"download_url":null,"error":"Invalid secret key"}""", HttpStatusCode.OK)
        }

        assertIs<AppError.Unauthorized>(source.resolveDownload(result()).errorOrNull())
    }

    @Test
    fun `each fast-download refusal is reported as what it is, on one mirror only`() = runTest {
        // The statuses the API's own documentation lists, and what the user can do about each.
        val expected = mapOf<HttpStatusCode, (AppError) -> Unit>(
            HttpStatusCode.BadRequest to { assertIs<AppError.Parse>(it) },
            HttpStatusCode.Unauthorized to { assertIs<AppError.Unauthorized>(it) },
            HttpStatusCode.Forbidden to { assertIs<AppError.Unauthorized>(it) },
            HttpStatusCode.NotFound to { assertIs<AppError.NotFound>(it) }
        )

        expected.forEach { (status, assertion) ->
            val hosts = mutableListOf<String>()
            val source = source(config = AnnasConfig(mirrors, secretKey = "s3cr3t")) { request ->
                hosts += request.url.host
                respond("""{"download_url":null,"error":"${status.description}"}""", status)
            }

            val error = checkNotNull(source.resolveDownload(result()).errorOrNull()) {
                "$status should not have produced a download"
            }
            assertion(error)
            // None of these change with the mirror, so asking the others is pure noise.
            assertEquals(listOf("one.test"), hosts, "$status should not have been retried elsewhere")
        }
    }

    @Test
    fun `a spent daily allowance is reported by status too`() = runTest {
        val source = source(config = AnnasConfig(mirrors, secretKey = "s3cr3t")) {
            respond(
                """{"download_url":null,"error":"No downloads left"}""",
                HttpStatusCode.TooManyRequests
            )
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertTrue(request.requiresBrowser)
    }

    @Test
    fun `the mirror that answered is remembered and tried first next time`() = runTest {
        val remembered = mutableListOf<String>()
        val hosts = mutableListOf<String>()
        val source = source(
            config = AnnasConfig(mirrors, preferredMirror = "https://three.test"),
            onWorkingMirror = { remembered += it }
        ) { request ->
            hosts += request.url.host
            respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
        }

        source.search(SearchQuery("pride"))

        assertEquals(listOf("three.test"), hosts, "the remembered mirror was not tried first")
        assertTrue(remembered.isEmpty(), "nothing changed, so nothing needed writing")
    }

    private fun result() = SearchResult(
        sourceId = AnnasArchiveSource.ID,
        recordId = "0123456789abcdef0123456789abcdef",
        title = "Pride and Prejudice",
        authors = listOf("Jane Austen"),
        format = BookFormat.EPUB
    )

    private fun source(
        resolver: MirrorResolver = MirrorResolver(),
        config: AnnasConfig = AnnasConfig(mirrors),
        onWorkingMirror: suspend (String) -> Unit = {},
        handler: MockRequestHandler
    ) = AnnasArchiveSource(
        client = HttpClient(MockEngine(handler)),
        config = { config },
        mirrors = resolver,
        onWorkingMirror = onWorkingMirror
    )

    private val htmlHeaders = headersOf(HttpHeaders.ContentType, "text/html")
}
