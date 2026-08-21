package dev.readana.core.data.ai

import dev.readana.core.model.AiContextSize
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wire form of [AiSettings]. The whole object lives in one DataStore string: the provider list is
 * a list of records, which the flat key-per-field style used elsewhere cannot express.
 *
 * A blob that cannot be read falls back to defaults, so a bad write never bricks the settings
 * screen — the user simply pastes the key again.
 */
@Serializable
private data class AiSettingsDto(
    val providers: List<AiProviderDto> = emptyList(),
    val activeProviderId: String? = null,
    val contextSize: String? = null,
    val recapEnabled: Boolean = true,
    val recapAfterDays: Int = AiSettings.DEFAULT_RECAP_AFTER_DAYS
)

@Serializable
private data class AiProviderDto(
    val id: String,
    val kind: String,
    val name: String,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val model: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

internal fun AiSettings.encodeToJson(): String = json.encodeToString(
    AiSettingsDto(
        providers = providers.map {
            AiProviderDto(
                id = it.id,
                kind = it.kind.name,
                name = it.name,
                baseUrl = it.baseUrl,
                apiKey = it.apiKey,
                model = it.model
            )
        },
        activeProviderId = activeProviderId,
        contextSize = contextSize.name,
        recapEnabled = recapEnabled,
        recapAfterDays = recapAfterDays
    )
)

/** Always returns usable settings, with a row for every built-in provider. */
internal fun String?.decodeAiSettings(): AiSettings {
    if (this.isNullOrBlank()) return AiSettings().withBuiltIns()
    val dto = try {
        json.decodeFromString<AiSettingsDto>(this)
    } catch (_: SerializationException) {
        return AiSettings().withBuiltIns()
    } catch (_: IllegalArgumentException) {
        return AiSettings().withBuiltIns()
    }
    return AiSettings(
        providers = dto.providers.mapNotNull { it.toModel() },
        activeProviderId = dto.activeProviderId,
        contextSize = AiContextSize.entries.firstOrNull { it.name == dto.contextSize }
            ?: AiContextSize.Medium,
        recapEnabled = dto.recapEnabled,
        recapAfterDays = dto.recapAfterDays.coerceIn(AiSettings.RECAP_DAYS_RANGE)
    ).withBuiltIns().withActiveDefaulted()
}

/** A row naming a provider kind this build no longer knows is dropped rather than guessed at. */
private fun AiProviderDto.toModel(): AiProviderConfig? {
    val providerKind = AiProviderKind.entries.firstOrNull { it.name == kind } ?: return null
    return AiProviderConfig(
        id = id,
        kind = providerKind,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model
    )
}
