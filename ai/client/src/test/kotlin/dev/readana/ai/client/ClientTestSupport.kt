package dev.readana.ai.client

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

internal val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

internal fun MockRequestHandleScope.respondSse(body: String): HttpResponseData =
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))

/** Fixtures live in `src/test/resources`, as they do for the book sources. */
internal fun Any.fixture(name: String): String =
    javaClass.getResource("/$name")?.readText() ?: error("Missing fixture $name")

internal fun HttpRequestData.bodyText(): String = String((body as OutgoingContent.ByteArrayContent).bytes())
