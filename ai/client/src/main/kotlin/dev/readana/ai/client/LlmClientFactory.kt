package dev.readana.ai.client

import dev.readana.core.model.AiProviderConfig
import io.ktor.client.HttpClient

/**
 * Builds the client for a configured provider. An interface so tests can hand the assistant a
 * canned client without going near HTTP.
 */
interface LlmClientFactory {
    /** Null when the provider is not complete enough to send a request. */
    fun create(config: AiProviderConfig): LlmClient?
}

/**
 * Clients are stateless, so one is made per call rather than cached; what matters is that they all
 * share the streaming-friendly [HttpClient] the data layer provides (the shared one carries
 * browser cookies and cuts long reads).
 */
class DefaultLlmClientFactory(private val httpClient: HttpClient) : LlmClientFactory {
    override fun create(config: AiProviderConfig): LlmClient? = config.toEndpoint()?.let { create(it) }

    fun create(endpoint: LlmEndpoint): LlmClient = when (endpoint.kind.wireFormat) {
        LlmWireFormat.Anthropic -> AnthropicClient(httpClient, endpoint)
        LlmWireFormat.OpenAiCompatible -> OpenAiCompatibleClient(httpClient, endpoint)
    }
}

/** Resolves a stored provider row down to what a request needs, or null when something is missing. */
fun AiProviderConfig.toEndpoint(): LlmEndpoint? {
    if (!isUsable) return null
    return LlmEndpoint(
        kind = kind,
        baseUrl = effectiveBaseUrl ?: return null,
        apiKey = apiKey?.trim()?.ifEmpty { null },
        model = effectiveModel ?: return null
    )
}
