package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.errorOrNull
import dev.readana.core.common.result.getOrNull
import dev.readana.core.testing.FakeBrowserVerifier
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.VerifyOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The gate in front of Anna's Archive: without a recent browser check it answers a search with a
 * redirect to the same URL plus `check=1`, and serves that as a JavaScript challenge. A member is
 * waved past it; everyone else needs the challenge answered in a browser engine.
 */
class AnnasGateTest {
    private val searchHtml = checkNotNull(javaClass.getResource("/annas-search-table.html")).readText()
    private val challengeHtml = checkNotNull(javaClass.getResource("/ddos-guard-challenge.html")).readText()
    private val mirrors = listOf("https://one.test")

    @Test
    fun `a member signs in with the key and is waved past the check`() = runTest {
        val requests = mutableListOf<String>()
        var postedBody: String? = null
        var signedIn = false
        val verifier = FakeBrowserVerifier()

        val source = source(AnnasConfig(mirrors, secretKey = "s3cr3t"), verifier) { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.url.encodedPath == "/account/" -> {
                    postedBody = request.body.readText()
                    signedIn = true
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(
                            HttpHeaders.Location to listOf("/account/"),
                            HttpHeaders.SetCookie to listOf("aa_account_id2=member.jwt; Path=/; HttpOnly")
                        )
                    )
                }

                signedIn -> respond(searchHtml, HttpStatusCode.OK, htmlHeaders)

                else -> gate(request)
            }
        }

        val page = assertNotNull(source.search(SearchQuery("pride")).getOrNull())

        assertEquals(2, page.results.size)
        assertEquals("key=s3cr3t", postedBody)
        assertEquals(
            listOf("GET /search", "GET /search", "POST /account/", "GET /search"),
            requests,
            "expected: blocked search, its check redirect, the login, then the search again"
        )
        assertTrue(verifier.verified.isEmpty(), "a member should never need the browser check")
    }

    @Test
    fun `a signed-in member is not signed in again on the next search`() = runTest {
        var logins = 0
        var signedIn = false
        val source = source(AnnasConfig(mirrors, secretKey = "s3cr3t")) { request ->
            when {
                request.url.encodedPath == "/account/" -> {
                    logins++
                    signedIn = true
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(
                            HttpHeaders.Location to listOf("/account/"),
                            HttpHeaders.SetCookie to listOf("aa_account_id2=member.jwt; Path=/")
                        )
                    )
                }

                signedIn -> respond(searchHtml, HttpStatusCode.OK, htmlHeaders)

                else -> gate(request)
            }
        }

        source.search(SearchQuery("pride"))
        source.search(SearchQuery("prejudice"))

        assertEquals(1, logins, "the session cookie is good for both searches")
    }

    @Test
    fun `without a key the challenge is answered in a browser and the search retried`() = runTest {
        var cleared = false
        val verifier = FakeBrowserVerifier(onVerify = { cleared = true })
        val source = source(AnnasConfig(mirrors), verifier) { request ->
            if (cleared) respond(searchHtml, HttpStatusCode.OK, htmlHeaders) else gate(request)
        }

        val page = assertNotNull(source.search(SearchQuery("pride")).getOrNull())

        assertEquals(2, page.results.size)
        // The site redirects to its own check URL, so the original one is what to hand over.
        assertEquals(1, verifier.verified.size)
        assertContains(verifier.verified.single(), "/search")
    }

    @Test
    fun `with no browser to verify with, the URL is handed back for the user to open`() = runTest {
        val verifier = FakeBrowserVerifier(outcome = VerifyOutcome.Unavailable)
        val source = source(AnnasConfig(mirrors), verifier) { gate(it) }

        val error = assertIs<AppError.BrowserCheck>(source.search(SearchQuery("pride")).errorOrNull())

        assertContains(error.url, "/search")
    }

    @Test
    fun `a check that keeps coming back is not retried forever`() = runTest {
        var attempts = 0
        val verifier = FakeBrowserVerifier()
        val source = source(AnnasConfig(mirrors), verifier) { request ->
            // The site's own check redirect is a second hop of the same attempt, not a new one.
            if (request.url.parameters["check"] == null) attempts++
            gate(request)
        }

        assertIs<AppError.BrowserCheck>(source.search(SearchQuery("pride")).errorOrNull())

        assertEquals(2, attempts, "the search itself, then one retry after verifying. No more.")
        assertEquals(1, verifier.verified.size)
    }

    @Test
    fun `a challenge served as a normal page is still a challenge`() = runTest {
        var cleared = false
        val verifier = FakeBrowserVerifier(onVerify = { cleared = true })
        val source = source(AnnasConfig(mirrors), verifier) {
            if (cleared) {
                respond(searchHtml, HttpStatusCode.OK, htmlHeaders)
            } else {
                // Some front-ends answer 200 with the challenge body rather than 403.
                respond(challengeHtml, HttpStatusCode.OK, htmlHeaders)
            }
        }

        val page = assertNotNull(source.search(SearchQuery("pride")).getOrNull())

        assertEquals(2, page.results.size)
        assertEquals(1, verifier.verified.size)
    }

    /** What the site does to a request with no recent check: bounce it, then challenge it. */
    private fun MockRequestHandleScope.gate(request: HttpRequestData): HttpResponseData =
        if (request.url.parameters["check"] == "1") {
            respond(challengeHtml, HttpStatusCode.Forbidden, htmlHeaders)
        } else {
            respondRedirect("${request.url}&check=1")
        }

    private fun source(
        config: AnnasConfig,
        verifier: FakeBrowserVerifier = FakeBrowserVerifier(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) = AnnasArchiveSource(
        client = HttpClient(MockEngine(handler)),
        config = { config },
        verifier = verifier,
        reachability = HostReachability.Always
    )

    /** The login body, so a test can prove the key is what gets posted. */
    private fun OutgoingContent.readText(): String = when (this) {
        is TextContent -> text
        is OutgoingContent.ByteArrayContent -> String(bytes())
        else -> ""
    }

    private val htmlHeaders = headersOf(HttpHeaders.ContentType, "text/html")
}
