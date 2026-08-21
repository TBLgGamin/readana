package dev.readana.ai.client

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.map
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anthropic's Messages API. Kept native rather than folded into the OpenAI-compatible client
 * because prompt caching (`cache_control`) is what keeps follow-up questions about the same book
 * cheap, and only this dialect exposes it.
 */
internal class AnthropicClient(private val httpClient: HttpClient, private val endpoint: LlmEndpoint) : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmEvent> {
        // Usage arrives spread over three event types, so it is collected as the stream runs.
        val usage = UsageTally()
        return httpClient.llmEventFlow(
            url = endpoint.baseUrl.appendPath(MESSAGES_PATH),
            configure = { prepare(request.toBody(stream = true)) },
            onEvent = { event -> handle(event, usage) },
            onEnd = { emit(usage.toDone()) }
        )
    }

    override suspend fun listModels(): AppResult<List<LlmModel>> =
        readBody { httpClient.get(endpoint.baseUrl.appendPath(MODELS_PATH)) { authHeaders() } }
            .flatMapDecode<ModelsResponse>()
            .map { response -> response.data.map { LlmModel(id = it.id, displayName = it.displayName) } }

    override suspend fun ping(): AppResult<Long> {
        val started = System.nanoTime()
        val request = LlmRequest(
            system = listOf(PromptBlock(PING_SYSTEM)),
            messages = listOf(ChatMessage(ChatRole.User, PING_PROMPT)),
            maxOutputTokens = PING_MAX_TOKENS
        )
        return readBody {
            httpClient.post(endpoint.baseUrl.appendPath(MESSAGES_PATH)) {
                prepare(request.toBody(stream = false))
            }
        }.map { millisSince(started) }
    }

    private fun HttpRequestBuilder.prepare(body: MessagesRequest) {
        authHeaders()
        contentType(ContentType.Application.Json)
        setBody(LlmJson.encodeToString(body))
    }

    private fun HttpRequestBuilder.authHeaders() {
        endpoint.apiKey?.takeIf { it.isNotBlank() }?.let { header(HEADER_KEY, it) }
        header(HEADER_VERSION, API_VERSION)
        endpoint.extraHeaders.forEach { (name, value) -> header(name, value) }
    }

    private fun LlmRequest.toBody(stream: Boolean) = MessagesRequest(
        model = endpoint.model,
        maxTokens = maxOutputTokens,
        stream = stream,
        system = system.map { block ->
            SystemBlock(
                text = block.text,
                cacheControl = if (block.cacheable) CacheControl() else null
            )
        },
        messages = messages.map { message ->
            RequestMessage(
                role = if (message.role == ChatRole.User) ROLE_USER else ROLE_ASSISTANT,
                content = message.text
            )
        }
    )

    private suspend fun FlowCollector<LlmEvent>.handle(event: SseEvent, usage: UsageTally) {
        when (event.name) {
            EVENT_CONTENT_DELTA -> {
                val text = LlmJson.decodeFromString<ContentBlockDelta>(event.data).delta?.text
                if (!text.isNullOrEmpty()) emit(LlmEvent.Delta(text))
            }

            EVENT_MESSAGE_START ->
                usage.read(LlmJson.decodeFromString<MessageStart>(event.data).message?.usage)

            EVENT_MESSAGE_DELTA ->
                usage.read(LlmJson.decodeFromString<MessageDelta>(event.data).usage)

            EVENT_ERROR -> {
                val message = LlmJson.decodeFromString<StreamError>(event.data).error?.message
                throw LlmException(AppError.Unexpected(message ?: "The provider ended the stream."))
            }

            else -> Unit
        }
    }

    /** Anthropic reports input tokens at the start and output tokens at the end. */
    private class UsageTally {
        private var input: Int? = null
        private var output: Int? = null
        private var cached: Int? = null

        fun read(usage: Usage?) {
            usage ?: return
            usage.inputTokens?.let { input = it }
            usage.outputTokens?.let { output = it }
            usage.cacheReadInputTokens?.let { cached = it }
        }

        fun toDone() = LlmEvent.Done(input, output, cached)
    }

    private companion object {
        const val MESSAGES_PATH = "v1/messages"
        const val MODELS_PATH = "v1/models"
        const val HEADER_KEY = "x-api-key"
        const val HEADER_VERSION = "anthropic-version"
        const val API_VERSION = "2023-06-01"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val EVENT_CONTENT_DELTA = "content_block_delta"
        const val EVENT_MESSAGE_START = "message_start"
        const val EVENT_MESSAGE_DELTA = "message_delta"
        const val EVENT_ERROR = "error"
        const val PING_SYSTEM = "Reply with the single word OK."
        const val PING_PROMPT = "ping"
        const val PING_MAX_TOKENS = 4
    }
}

@Serializable
private data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean,
    val system: List<SystemBlock>,
    val messages: List<RequestMessage>
)

@Serializable
private data class SystemBlock(
    val text: String,
    @SerialName("cache_control") val cacheControl: CacheControl? = null,
    val type: String = "text"
)

@Serializable
private data class CacheControl(val type: String = "ephemeral")

@Serializable
private data class RequestMessage(val role: String, val content: String)

@Serializable
private data class ContentBlockDelta(val delta: TextDelta? = null)

@Serializable
private data class TextDelta(val text: String? = null)

@Serializable
private data class MessageStart(val message: StartedMessage? = null)

@Serializable
private data class StartedMessage(val usage: Usage? = null)

@Serializable
private data class MessageDelta(val usage: Usage? = null)

@Serializable
private data class Usage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int? = null
)

@Serializable
private data class StreamError(val error: StreamErrorBody? = null)

@Serializable
private data class StreamErrorBody(val message: String? = null)

@Serializable
private data class ModelsResponse(val data: List<ModelEntry> = emptyList())

@Serializable
private data class ModelEntry(val id: String, @SerialName("display_name") val displayName: String? = null)
