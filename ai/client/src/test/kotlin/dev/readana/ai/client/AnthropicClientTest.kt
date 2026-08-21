package dev.readana.ai.client

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.model.AiProviderKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class AnthropicClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `streams text deltas and reports usage`() = runTest {
        val client = client { respondSse(fixture("anthropic-stream.txt")) }

        val events = client.stream(request()).toList()

        assertEquals(
            listOf("Ishmael ", "is the narrator."),
            events.filterIsInstance<LlmEvent.Delta>().map { it.text }
        )
        assertEquals(LlmEvent.Done(1200, 42, 1024, stopReason = "end_turn"), events.last())
    }

    @Test
    fun `sends the key, the version and a cacheable system block`() = runTest {
        val client = client { respondSse(fixture("anthropic-stream.txt")) }

        client.stream(request()).toList()

        val sent = requests.single()
        assertEquals("secret", sent.headers["x-api-key"])
        assertEquals("2023-06-01", sent.headers["anthropic-version"])
        val body = sent.bodyText()
        assertTrue(body.contains("\"cache_control\":{\"type\":\"ephemeral\"}"), body)
        assertTrue(body.contains("\"stream\":true"), body)
        assertTrue(body.contains("\"model\":\"claude-sonnet-5\""), body)
    }

    @Test
    fun `a rejected key becomes an unauthorized failure`() = runTest {
        val client = client {
            respond(fixture("error-401.json"), HttpStatusCode.Unauthorized, jsonHeaders)
        }

        val failure = assertFailsWith<LlmException> { client.stream(request()).toList() }

        val error = assertIs<AppError.Unauthorized>(failure.error)
        assertEquals("invalid x-api-key", error.message)
    }

    @Test
    fun `a dropped connection becomes a network failure`() = runTest {
        val client = client { throw IOException("socket closed") }

        val failure = assertFailsWith<LlmException> { client.stream(request()).toList() }

        assertIs<AppError.Network>(failure.error)
    }

    @Test
    fun `lists models`() = runTest {
        val client = client { respond(fixture("anthropic-models.json"), headers = jsonHeaders) }

        val models = client.listModels()

        assertEquals(
            listOf("claude-sonnet-5", "claude-haiku-4-5"),
            assertIs<AppResult.Success<List<LlmModel>>>(models).value.map { it.id }
        )
    }

    @Test
    fun `ping measures a round trip`() = runTest {
        val client = client { respond("""{"content":[]}""", headers = jsonHeaders) }

        assertIs<AppResult.Success<Long>>(client.ping())
        assertTrue(requests.single().bodyText().contains("\"stream\":false"))
    }

    private fun request() = LlmRequest(
        system = listOf(PromptBlock("rules"), PromptBlock("the book so far", cacheable = true)),
        messages = listOf(ChatMessage(ChatRole.User, "Who is Ishmael?"))
    )

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): LlmClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return AnthropicClient(
            httpClient = HttpClient(engine),
            endpoint = LlmEndpoint(
                kind = AiProviderKind.Anthropic,
                baseUrl = "https://api.anthropic.com",
                apiKey = "secret",
                model = "claude-sonnet-5"
            )
        )
    }
}
