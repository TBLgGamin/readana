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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class OpenAiCompatibleClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `streams text deltas and reports usage from the final chunk`() = runTest {
        val client = client(AiProviderKind.OpenAi) { respondSse(fixture("openai-stream.txt")) }

        val events = client.stream(request()).toList()

        assertEquals(
            listOf("Ishmael ", "is the narrator."),
            events.filterIsInstance<LlmEvent.Delta>().map { it.text }
        )
        assertEquals(LlmEvent.Done(1200, 42, 1024), events.last())
        assertEquals(1, events.count { it is LlmEvent.Done })
    }

    @Test
    fun `sends one system message, a bearer key and usage options`() = runTest {
        val client = client(AiProviderKind.OpenAi) { respondSse(fixture("openai-stream.txt")) }

        client.stream(request()).toList()

        val sent = requests.single()
        assertEquals("https://api.openai.com/v1/chat/completions", sent.url.toString())
        assertEquals("Bearer secret", sent.headers["Authorization"])
        val body = sent.bodyText()
        assertTrue(body.contains("\"role\":\"system\",\"content\":\"rules\\n\\nthe book so far\""), body)
        assertTrue(body.contains("\"include_usage\":true"), body)
    }

    @Test
    fun `openrouter identifies the app`() = runTest {
        val client = client(AiProviderKind.OpenRouter) { respondSse(fixture("openai-stream.txt")) }

        client.stream(request()).toList()

        val sent = requests.single()
        assertEquals("Readana", sent.headers["X-Title"])
        assertTrue(sent.headers["HTTP-Referer"]?.isNotBlank() == true)
    }

    @Test
    fun `a rejected key becomes an unauthorized failure`() = runTest {
        val client = client(AiProviderKind.DeepSeek) {
            respond(fixture("error-401.json"), HttpStatusCode.Unauthorized, jsonHeaders)
        }

        val failure = assertFailsWith<LlmException> { client.stream(request()).toList() }

        assertIs<AppError.Unauthorized>(failure.error)
    }

    @Test
    fun `a malformed chunk becomes a parse failure`() = runTest {
        val client = client(AiProviderKind.OpenAi) { respondSse("data: not json\n\n") }

        val failure = assertFailsWith<LlmException> { client.stream(request()).toList() }

        assertIs<AppError.Parse>(failure.error)
    }

    @Test
    fun `model ids drop the gemini prefix`() = runTest {
        val client = client(AiProviderKind.Gemini) {
            respond(fixture("openai-models.json"), headers = jsonHeaders)
        }

        val models = assertIs<AppResult.Success<List<LlmModel>>>(client.listModels()).value

        assertEquals(listOf("gemini-3.6-flash", "gpt-5.6-luna"), models.map { it.id })
        assertEquals(1_048_576, models.first().contextLength)
    }

    private fun request() = LlmRequest(
        system = listOf(PromptBlock("rules"), PromptBlock("the book so far", cacheable = true)),
        messages = listOf(ChatMessage(ChatRole.User, "Who is Ishmael?"))
    )

    private fun client(
        kind: AiProviderKind,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): LlmClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return OpenAiCompatibleClient(
            httpClient = HttpClient(engine),
            endpoint = LlmEndpoint(
                kind = kind,
                baseUrl = kind.defaultBaseUrl ?: "http://localhost:11434/v1",
                apiKey = "secret",
                model = kind.defaultModel ?: "local-model"
            )
        )
    }
}
