package dev.readana.ai.client

import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.map
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `POST {base}/chat/completions` — the dialect OpenAI defined and OpenAI, Gemini, OpenRouter,
 * DeepSeek, Ollama, LM Studio and most gateways serve. The differences between them are the base
 * URL, a few extra headers and the model names, all of which live in [LlmEndpoint].
 *
 * There is no prompt-cache control here: providers that cache do it automatically on identical
 * prefixes, which is exactly what a stored thread context produces.
 */
internal class OpenAiCompatibleClient(private val httpClient: HttpClient, private val endpoint: LlmEndpoint) :
    LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmEvent> {
        // Usage arrives in a final chunk of its own, after the one carrying the finish reason.
        val usage = UsageTally()
        return httpClient.llmEventFlow(
            url = endpoint.baseUrl.appendPath(COMPLETIONS_PATH),
            configure = { prepare(request.toBody(stream = true)) },
            onEvent = { event -> handle(event, usage) },
            onEnd = { emit(usage.toDone()) }
        )
    }

    override suspend fun listModels(): AppResult<List<LlmModel>> =
        readBody { httpClient.get(endpoint.baseUrl.appendPath(MODELS_PATH)) { authHeaders() } }
            .flatMapDecode<OpenAiModelsResponse>()
            .map { response -> response.data.map { it.toModel() } }

    override suspend fun ping(): AppResult<Long> {
        val started = System.nanoTime()
        val request = LlmRequest(
            system = listOf(PromptBlock(PING_SYSTEM)),
            messages = listOf(ChatMessage(ChatRole.User, PING_PROMPT)),
            maxOutputTokens = PING_MAX_TOKENS
        )
        return readBody {
            httpClient.post(endpoint.baseUrl.appendPath(COMPLETIONS_PATH)) {
                prepare(request.toBody(stream = false))
            }
        }.map { millisSince(started) }
    }

    private fun HttpRequestBuilder.prepare(body: OpenAiCompletionsRequest) {
        authHeaders()
        contentType(ContentType.Application.Json)
        setBody(LlmJson.encodeToString(body))
    }

    private fun HttpRequestBuilder.authHeaders() {
        endpoint.apiKey?.takeIf { it.isNotBlank() }?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
        endpoint.extraHeaders.forEach { (name, value) -> header(name, value) }
    }

    /**
     * The dialect has one system message, so the blocks are joined. Their order is stable across a
     * thread, which is what lets providers reuse a cached prefix.
     */
    private fun LlmRequest.toBody(stream: Boolean) = OpenAiCompletionsRequest(
        model = endpoint.model,
        maxTokens = maxOutputTokens,
        stream = stream,
        streamOptions = if (stream) OpenAiStreamOptions() else null,
        messages = buildList {
            val instructions = system.joinToString(BLOCK_SEPARATOR) { it.text }
            if (instructions.isNotBlank()) {
                add(OpenAiMessage(ROLE_SYSTEM, MessageContent.openAi(instructions, emptyList())))
            }
            messages.forEachIndexed { index, message ->
                val role = if (message.role == ChatRole.User) ROLE_USER else ROLE_ASSISTANT
                // Pictures ride on the turn being answered, which is always the last one.
                val attached = if (index == messages.lastIndex) images else emptyList()
                add(OpenAiMessage(role, MessageContent.openAi(message.text, attached)))
            }
        }
    )

    private suspend fun FlowCollector<LlmEvent>.handle(event: SseEvent, usage: UsageTally) {
        val chunk = LlmJson.decodeFromString<OpenAiChunk>(event.data)
        val text = chunk.choices.firstOrNull()?.delta?.content
        if (!text.isNullOrEmpty()) emit(LlmEvent.Delta(text))
        usage.read(chunk.usage)
        chunk.choices.firstOrNull()?.finishReason?.let { usage.stopReason = it }
    }

    /** Only the last chunk carries usage, and only when the provider supports `stream_options`. */
    private class UsageTally {
        private var input: Int? = null
        private var output: Int? = null
        private var cached: Int? = null
        var stopReason: String? = null

        fun read(usage: OpenAiUsage?) {
            usage ?: return
            usage.promptTokens?.let { input = it }
            usage.completionTokens?.let { output = it }
            usage.promptTokensDetails?.cachedTokens?.let { cached = it }
        }

        fun toDone() = LlmEvent.Done(input, output, cached, stopReason)
    }

    private fun OpenAiModelEntry.toModel(): LlmModel = LlmModel(
        // Gemini's compatibility layer returns ids as "models/gemini-…".
        id = id.removePrefix(GEMINI_ID_PREFIX),
        displayName = name,
        contextLength = contextLength
    )

    private companion object {
        const val COMPLETIONS_PATH = "chat/completions"
        const val MODELS_PATH = "models"
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val BLOCK_SEPARATOR = "\n\n"
        const val GEMINI_ID_PREFIX = "models/"
        const val PING_SYSTEM = "Reply with the single word OK."
        const val PING_PROMPT = "ping"
        const val PING_MAX_TOKENS = 4
    }
}

@Serializable
private data class OpenAiCompletionsRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean,
    @SerialName("stream_options") val streamOptions: OpenAiStreamOptions? = null
)

@Serializable
private data class OpenAiStreamOptions(@SerialName("include_usage") val includeUsage: Boolean = true)

@Serializable
private data class OpenAiMessage(val role: String, val content: JsonElement)

@Serializable
private data class OpenAiChunk(val choices: List<OpenAiChoice> = emptyList(), val usage: OpenAiUsage? = null)

@Serializable
private data class OpenAiChoice(
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
private data class OpenAiDelta(val content: String? = null)

@Serializable
private data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: OpenAiPromptTokensDetails? = null
)

@Serializable
private data class OpenAiPromptTokensDetails(@SerialName("cached_tokens") val cachedTokens: Int? = null)

@Serializable
private data class OpenAiModelsResponse(val data: List<OpenAiModelEntry> = emptyList())

@Serializable
private data class OpenAiModelEntry(
    val id: String,
    val name: String? = null,
    @SerialName("context_length") val contextLength: Int? = null
)
