package dev.readana.ai.client

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.model.AiProviderKind
import kotlinx.coroutines.flow.Flow

/** The two request shapes Readana speaks. Every supported provider talks one of them. */
enum class LlmWireFormat {
    /** Anthropic's Messages API, kept native so the book context can be marked cacheable. */
    Anthropic,

    /** `POST {base}/chat/completions`, as served by OpenAI, Gemini, OpenRouter, DeepSeek, Ollama… */
    OpenAiCompatible
}

/** Which dialect [this] provider expects. */
val AiProviderKind.wireFormat: LlmWireFormat
    get() = when (this) {
        AiProviderKind.Anthropic -> LlmWireFormat.Anthropic

        AiProviderKind.OpenAi,
        AiProviderKind.Gemini,
        AiProviderKind.OpenRouter,
        AiProviderKind.DeepSeek,
        AiProviderKind.Custom -> LlmWireFormat.OpenAiCompatible
    }

/** A provider resolved down to what a request actually needs. */
data class LlmEndpoint(val kind: AiProviderKind, val baseUrl: String, val apiKey: String?, val model: String) {
    /** Headers some gateways want on top of the key; OpenRouter uses them for attribution. */
    val extraHeaders: Map<String, String>
        get() = when (kind) {
            AiProviderKind.OpenRouter -> mapOf(
                "HTTP-Referer" to APP_URL,
                "X-Title" to APP_NAME
            )

            else -> emptyMap()
        }

    private companion object {
        const val APP_URL = "https://github.com/TBLgGamin/readana"
        const val APP_NAME = "Readana"
    }
}

/**
 * A piece of the system prompt. [cacheable] blocks are marked for the provider's prompt cache,
 * which is what makes follow-up questions about the same book cheap.
 */
data class PromptBlock(val text: String, val cacheable: Boolean = false)

enum class ChatRole { User, Assistant }

data class ChatMessage(val role: ChatRole, val text: String)

/**
 * A picture handed to the model along with the question — a figure from the pages the reader has
 * already been through. [id] is the marker the answer refers to it by, so what the model writes
 * can be turned back into the picture on screen.
 */
class ImageAttachment(val id: String, val mediaType: String, val bytes: ByteArray)

data class LlmRequest(
    val system: List<PromptBlock>,
    val messages: List<ChatMessage>,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    /** Attached to the last user turn, which is the one the model is answering. */
    val images: List<ImageAttachment> = emptyList()
) {
    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1_024
    }
}

/** What comes back while an answer is being written. */
sealed interface LlmEvent {
    data class Delta(val text: String) : LlmEvent

    data class Done(
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        /** Input tokens served from the provider's prompt cache, when it reports them. */
        val cachedInputTokens: Int? = null,
        /** Why the model stopped: "end_turn", "max_tokens", "stop", "length", … */
        val stopReason: String? = null
    ) : LlmEvent
}

/** One model as the provider lists it. */
data class LlmModel(val id: String, val displayName: String? = null, val contextLength: Int? = null)

/**
 * Talks to one provider. Implementations are stateless; a client is cheap to create per request.
 *
 * [stream] fails by throwing [LlmException], so collectors can map the carried [AppError].
 */
interface LlmClient {
    fun stream(request: LlmRequest): Flow<LlmEvent>

    suspend fun listModels(): AppResult<List<LlmModel>>

    /** A minimal round trip used by the settings screen; returns the latency in milliseconds. */
    suspend fun ping(): AppResult<Long>
}

/** Carries a typed [AppError] out of a streaming flow. */
class LlmException(val error: AppError) : Exception(error.message)
