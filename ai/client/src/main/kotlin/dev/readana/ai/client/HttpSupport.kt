package dev.readana.ai.client

import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.common.result.flatMap
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.preparePost
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared by both dialects: unknown fields come and go, and nulls are simply left out. */
internal val LlmJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Maps a non-success response onto a typed error, reading the provider's message when there is one. */
internal suspend fun HttpResponse.llmErrorOrNull(): AppError? {
    if (status.isSuccess()) return null
    val body = suspendRunCatching { bodyAsText() }.getOrDefault("")
    val detail = body.providerErrorMessage()
    return when (status) {
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            AppError.Unauthorized(detail ?: "The provider rejected this API key.")

        HttpStatusCode.TooManyRequests ->
            AppError.Http(status.value, detail ?: "The provider is rate limiting this key.")

        HttpStatusCode.NotFound ->
            AppError.NotFound(detail ?: "The provider has no such endpoint or model.")

        else -> AppError.Http(status.value, detail ?: status.description)
    }
}

/** Both dialects wrap failures as `{"error": {"message": "…"}}`. */
private fun String.providerErrorMessage(): String? {
    if (isBlank()) return null
    val message = try {
        LlmJson.parseToJsonElement(this)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    return message?.takeIf { it.isNotBlank() } ?: trimmedDetail()
}

private fun String.trimmedDetail(): String? = trim().takeIf { it.isNotEmpty() }?.take(MAX_DETAIL_CHARS)

private const val MAX_DETAIL_CHARS = 300

/**
 * Runs a streaming POST and turns its server-sent events into [LlmEvent]s. Transport and parsing
 * problems surface as [LlmException] so a collector sees one typed error either way.
 */
internal fun HttpClient.llmEventFlow(
    url: String,
    configure: HttpRequestBuilder.() -> Unit,
    onEvent: suspend FlowCollector<LlmEvent>.(SseEvent) -> Unit,
    onEnd: suspend FlowCollector<LlmEvent>.() -> Unit
): Flow<LlmEvent> = flow {
    try {
        preparePost(url, configure).execute { response ->
            response.llmErrorOrNull()?.let { throw LlmException(it) }
            SseReader.read(response.bodyAsChannel()) { event -> onEvent(event) }
            onEnd()
        }
    } catch (io: IOException) {
        throw LlmException(AppError.Network(io.message ?: "Could not reach the provider.", io))
    } catch (serialization: SerializationException) {
        throw LlmException(AppError.Parse("Unexpected response from the provider.", serialization))
    }
}

/** Joins a base URL and a path without doubling or dropping the slash between them. */
internal fun String.appendPath(path: String): String = trimEnd('/') + "/" + path.trimStart('/')

/**
 * Runs a one-shot call and returns its body, turning transport problems and error statuses into
 * typed failures. Used by the non-streaming calls (model lists and connection tests).
 */
internal suspend fun readBody(call: suspend () -> HttpResponse): AppResult<String> = suspendRunCatching {
    val response = call()
    val error = response.llmErrorOrNull()
    if (error != null) error.asFailure() else response.bodyAsText().asSuccess()
}.getOrElse { failure ->
    AppError.Network(failure.message ?: "Could not reach the provider.", failure).asFailure()
}

/** Decodes a response body, reporting malformed payloads as [AppError.Parse]. */
internal fun <T> decodeBody(body: String, decode: (String) -> T): AppResult<T> = try {
    decode(body).asSuccess()
} catch (serialization: SerializationException) {
    AppError.Parse("Unexpected response from the provider.", serialization).asFailure()
} catch (argument: IllegalArgumentException) {
    AppError.Parse("Unexpected response from the provider.", argument).asFailure()
}

/** Decodes a successful body into [T], keeping any earlier failure. */
internal inline fun <reified T> AppResult<String>.flatMapDecode(): AppResult<T> =
    flatMap { body -> decodeBody(body) { LlmJson.decodeFromString<T>(it) } }

/** Milliseconds elapsed since a [System.nanoTime] reading. */
internal fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L
