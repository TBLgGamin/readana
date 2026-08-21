package dev.readana.core.data.ai

import dev.readana.ai.client.LlmClientFactory
import dev.readana.ai.client.LlmModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.model.AiProviderConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers the two questions the settings screen asks of a provider: which models does it offer,
 * and does this key actually work? Both go through the same clients the assistant uses, so a green
 * "Test" means the assistant will work too.
 */
interface AiProviderProbe {
    suspend fun listModels(config: AiProviderConfig): AppResult<List<LlmModel>>

    /** Latency in milliseconds when the provider answers. */
    suspend fun test(config: AiProviderConfig): AppResult<Long>
}

@Singleton
class DefaultAiProviderProbe @Inject constructor(private val clientFactory: LlmClientFactory) : AiProviderProbe {
    override suspend fun listModels(config: AiProviderConfig): AppResult<List<LlmModel>> {
        // Listing needs no model, so a row without one may still be asked what it offers.
        val probeable = config.takeIf { it.model != null } ?: config.copy(model = PLACEHOLDER_MODEL)
        val client = clientFactory.create(probeable) ?: return incomplete()
        return client.listModels()
    }

    override suspend fun test(config: AiProviderConfig): AppResult<Long> {
        val client = clientFactory.create(config) ?: return incomplete()
        return client.ping()
    }

    private fun incomplete(): AppResult<Nothing> =
        AppError.Unauthorized("Fill in the base URL, model and key first.").asFailure()

    private companion object {
        const val PLACEHOLDER_MODEL = "unknown"
    }
}
