package dev.readana.source.annas

import dev.readana.core.common.result.getOrNull
import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchResult
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * What a download falls back to, and in which order: a member's fast link, then Library Genesis,
 * then the site's own free partner servers, then a browser the user drives.
 */
class AnnasDownloadLadderTest {
    private val libgenPage = checkNotNull(javaClass.getResource("/libgen-ads.html")).readText()
    private val libgenMissing = checkNotNull(javaClass.getResource("/libgen-ads-missing.html")).readText()
    private val slowLink = checkNotNull(javaClass.getResource("/slow-download-link.html")).readText()
    private val slowWait = checkNotNull(javaClass.getResource("/slow-download-wait.html")).readText()
    private val mirrors = listOf("https://one.test")

    @Test
    fun `a free download comes from Library Genesis, with no browser check in the way`() = runTest {
        val hosts = mutableListOf<String>()
        val source = source(AnnasConfig(mirrors)) { request ->
            hosts += request.url.host
            respond(libgenPage, HttpStatusCode.OK, htmlHeaders)
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertEquals(
            "https://libgen.li/get.php?md5=d6e1dc51a50726f00ec438af21952a45&key=G1TR8521LIONIEVY",
            request.url
        )
        assertFalse(request.requiresBrowser)
        // The file server checks where the link was picked up.
        assertEquals(
            "https://libgen.li/ads.php?md5=d6e1dc51a50726f00ec438af21952a45",
            request.headers[HttpHeaders.Referrer]
        )
        // The link page, then one byte to check the file server is actually up.
        assertEquals(listOf("libgen.li", "libgen.li"), hosts, "Anna's Archive was not needed at all")
    }

    @Test
    fun `a Library Genesis host that is down is skipped`() = runTest {
        val hosts = mutableListOf<String>()
        val source = source(AnnasConfig(mirrors)) { request ->
            hosts += request.url.host
            when (request.url.host) {
                "libgen.li" -> throw IOException("connection reset")
                "libgen.la" -> respondError(HttpStatusCode.BadGateway)
                else -> respond(libgenPage, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "libgen.vg")
        assertEquals(listOf("libgen.li", "libgen.la", "libgen.vg", "libgen.vg"), hosts)
    }

    @Test
    fun `a Library Genesis file server that is down falls through to a partner server`() = runTest {
        // The link page is fine; the file server behind it is not. Only asking for a byte finds out.
        val source = source(AnnasConfig(mirrors)) { request ->
            when {
                request.url.encodedPath == "/ads.php" ->
                    respond(libgenPage, HttpStatusCode.OK, htmlHeaders)

                request.url.encodedPath == "/get.php" ->
                    respondError(HttpStatusCode.ServiceUnavailable)

                else -> respond(slowLink, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "momot.example")
    }

    @Test
    fun `a record Library Genesis does not have falls through to a partner server`() = runTest {
        val asked = mutableListOf<String>()
        val source = source(AnnasConfig(mirrors)) { request ->
            asked += request.url.encodedPath
            when {
                request.url.host.startsWith("libgen") ->
                    respond(libgenMissing, HttpStatusCode.OK, htmlHeaders)

                else -> respond(slowLink, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "momot.example")
        assertFalse(request.requiresBrowser)
        // Only the first Libgen host is asked: they all share one catalogue.
        assertEquals(1, asked.count { it == "/ads.php" })
        assertContains(asked, "/slow_download/d6e1dc51a50726f00ec438af21952a45/0/4")
    }

    @Test
    fun `a short wait is sat through, a long one moves to the next server`() = runTest {
        val asked = mutableListOf<String>()
        val source = source(AnnasConfig(mirrors)) { request ->
            asked += request.url.encodedPath
            when {
                request.url.host.startsWith("libgen") ->
                    respond(libgenMissing, HttpStatusCode.OK, htmlHeaders)

                // Server 4 asks for a wait; after it, the page serves the link.
                asked.count { it.endsWith("/0/4") } == 1 ->
                    respond(slowWait, HttpStatusCode.OK, htmlHeaders)

                else -> respond(slowLink, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "momot.example")
        assertEquals(2, asked.count { it.endsWith("/0/4") }, "the page is reloaded once the wait is over")
    }

    @Test
    fun `when nothing serves the file, the browser is offered with the record page`() = runTest {
        val source = source(AnnasConfig(mirrors)) { request ->
            when {
                request.url.host.startsWith("libgen") ->
                    respond(libgenMissing, HttpStatusCode.OK, htmlHeaders)

                // Every partner server refuses; the site's own record page is the last resort.
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertTrue(request.requiresBrowser)
        assertEquals("https://one.test/md5/d6e1dc51a50726f00ec438af21952a45", request.url)
    }

    @Test
    fun `a member link on a blocked domain is swapped for another server`() = runTest {
        // What an ISP block looks like: the site hands out a perfectly good link on a domain this
        // connection cannot resolve. Asking again for the same file is free, so ask another server.
        val asked = mutableListOf<String?>()
        val source = AnnasArchiveSource(
            client = HttpClient(
                MockEngine { request ->
                    asked += request.url.parameters["domain_index"]
                    val server = request.url.parameters["domain_index"] ?: "0"
                    val host = if (server == "0") "blocked.test" else "reachable.test"
                    respond(
                        """{"download_url":"https://$host/d4/x/${'$'}md5.epub",""" +
                            """"account_fast_download_info":{"downloads_left":9,"downloads_per_day":25}}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                }
            ),
            config = { AnnasConfig(mirrors, secretKey = "s3cr3t") },
            reachability = { url -> !url.contains("blocked.test") }
        )

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "reachable.test")
        assertEquals(listOf(null, "1"), asked, "the blocked server is tried once, then the next")
        assertEquals(9, request.quota?.remaining)
    }

    @Test
    fun `when every member server is blocked the free routes still get a turn`() = runTest {
        val source = AnnasArchiveSource(
            client = HttpClient(
                MockEngine { request ->
                    when {
                        request.url.encodedPath.endsWith("fast_download.json") -> respond(
                            """{"download_url":"https://blocked.test/d4/x/y.epub",""" +
                                """"account_fast_download_info":{"downloads_left":9}}""",
                            HttpStatusCode.OK,
                            jsonHeaders
                        )

                        else -> respond(libgenPage, HttpStatusCode.OK, htmlHeaders)
                    }
                }
            ),
            config = { AnnasConfig(mirrors, secretKey = "s3cr3t") },
            reachability = { url -> !url.contains("blocked.test") }
        )

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "libgen.li")
        assertEquals(9, request.quota?.remaining, "the allowance is still worth reporting")
    }

    @Test
    fun `a spent membership allowance takes the free route instead of the browser`() = runTest {
        val source = source(AnnasConfig(mirrors, secretKey = "s3cr3t")) { request ->
            when {
                request.url.encodedPath.endsWith("fast_download.json") -> respond(
                    """{"download_url":null,"error":"No downloads left",""" +
                        """"account_fast_download_info":{"downloads_left":0,"downloads_per_day":100}}""",
                    HttpStatusCode.TooManyRequests,
                    jsonHeaders
                )

                else -> respond(libgenPage, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val request = assertNotNull(source.resolveDownload(result()).getOrNull())

        assertContains(request.url, "libgen.li")
        assertFalse(request.requiresBrowser, "a spent allowance is no reason to make the user do it")
        // Still worth reporting: the Settings screen shows what is left.
        assertEquals(0, request.quota?.remaining)
        assertEquals(100, request.quota?.perDay)
    }

    @Test
    fun `a record only Anna's Archive holds does not bother Library Genesis`() = runTest {
        val hosts = mutableListOf<String>()
        val source = source(AnnasConfig(mirrors)) { request ->
            hosts += request.url.host
            respond(slowLink, HttpStatusCode.OK, htmlHeaders)
        }

        val request = assertNotNull(
            source.resolveDownload(result(collections = "zlib")).getOrNull()
        )

        assertContains(request.url, "momot.example")
        assertTrue(hosts.none { it.startsWith("libgen") }, "asked: $hosts")
    }

    private fun result(collections: String? = "lgli/lgrs/zlib") = SearchResult(
        sourceId = AnnasArchiveSource.ID,
        recordId = "d6e1dc51a50726f00ec438af21952a45",
        title = "Against Intellectual Monopoly",
        authors = listOf("Michele Boldrin"),
        format = BookFormat.EPUB,
        extras = buildMap {
            put(AnnasSearchParser.EXTRA_MD5, "d6e1dc51a50726f00ec438af21952a45")
            collections?.let { put(AnnasSearchParser.EXTRA_COLLECTIONS, it) }
        }
    )

    private fun source(config: AnnasConfig, handler: MockRequestHandler) = AnnasArchiveSource(
        client = HttpClient(MockEngine(handler)),
        config = { config },
        reachability = HostReachability.Always
    )

    private val htmlHeaders = headersOf(HttpHeaders.ContentType, "text/html")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
}
