package dev.readana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.ai.AiProviderProbe
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiContextSize
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiSettings
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a provider row knows about itself beyond its stored configuration. */
data class ProviderProbe(
    val isBusy: Boolean = false,
    val models: List<String> = emptyList(),
    /** Round-trip time of a successful test, in milliseconds. */
    val latencyMillis: Long? = null,
    val error: String? = null
)

data class AiSettingsUiState(
    val settings: AiSettings = AiSettings(),
    val probes: Map<String, ProviderProbe> = emptyMap()
)

/**
 * The assistant's provider list. Separate from [SettingsViewModel] because it is its own screenful
 * of state — keys, model lists, connection tests — and mixing the two would make either class hard
 * to follow.
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val probe: AiProviderProbe
) : ViewModel() {
    private val probes = MutableStateFlow<Map<String, ProviderProbe>>(emptyMap())

    val uiState: StateFlow<AiSettingsUiState> =
        combine(settingsRepository.aiSettings, probes) { settings, probeStates ->
            AiSettingsUiState(settings = settings, probes = probeStates)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AiSettingsUiState())

    fun setActiveProvider(id: String) = update { it.copy(activeProviderId = id) }

    fun setApiKey(id: String, key: String) = updateProvider(id) {
        it.copy(apiKey = key.trim().ifEmpty { null })
    }

    fun setModel(id: String, model: String) = updateProvider(id) {
        it.copy(model = model.trim().ifEmpty { null })
    }

    fun setBaseUrl(id: String, url: String) = updateProvider(id) {
        it.copy(baseUrl = url.trim().ifEmpty { null })
    }

    /** Adds an OpenAI-compatible endpoint: a local server, a gateway, anything that speaks it. */
    fun addCustomProvider(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val id = AiSettings.CUSTOM_ID_PREFIX + UUID.randomUUID()
        update { settings ->
            settings.copy(
                providers = settings.providers + AiProviderConfig(
                    id = id,
                    kind = AiProviderKind.Custom,
                    name = trimmed
                ),
                activeProviderId = settings.activeProviderId ?: id
            )
        }
    }

    /** Only custom rows can go; the built-ins stay so their keys can be pasted back. */
    fun removeProvider(id: String) = update { settings ->
        settings.copy(
            providers = settings.providers.filterNot { it.id == id },
            activeProviderId = settings.activeProviderId.takeIf { it != id }
        )
    }

    fun fetchModels(id: String) = probeWith(id) { config ->
        when (val result = probe.listModels(config)) {
            is AppResult.Success -> ProviderProbe(models = result.value.map { it.id }.sorted())
            is AppResult.Failure -> ProviderProbe(error = result.error.message)
        }
    }

    fun testProvider(id: String) = probeWith(id) { config ->
        when (val result = probe.test(config)) {
            is AppResult.Success -> ProviderProbe(latencyMillis = result.value)
            is AppResult.Failure -> ProviderProbe(error = result.error.message)
        }
    }

    fun setContextSize(size: AiContextSize) = update { it.copy(contextSize = size) }

    fun setRecapEnabled(enabled: Boolean) = update { it.copy(recapEnabled = enabled) }

    fun setRecapAfterDays(days: Int) = update {
        it.copy(recapAfterDays = days.coerceIn(AiSettings.RECAP_DAYS_RANGE))
    }

    private fun probeWith(id: String, run: suspend (AiProviderConfig) -> ProviderProbe) {
        viewModelScope.launch {
            // Read from the store, not from uiState: nothing may be collecting it yet.
            val config = settingsRepository.aiSettings.first()
                .providers.firstOrNull { it.id == id } ?: return@launch
            val existing = probes.value[id] ?: ProviderProbe()
            probes.update { it + (id to existing.copy(isBusy = true, error = null)) }
            val outcome = run(config)
            // A fresh model list should not wipe an earlier test result, or the other way round.
            probes.update {
                it + (
                    id to existing.copy(
                        isBusy = false,
                        models = outcome.models.ifEmpty { existing.models },
                        latencyMillis = outcome.latencyMillis ?: existing.latencyMillis,
                        error = outcome.error
                    )
                    )
            }
        }
    }

    private fun updateProvider(id: String, transform: (AiProviderConfig) -> AiProviderConfig) =
        update { it.withProvider(id, transform) }

    private fun update(transform: (AiSettings) -> AiSettings) {
        viewModelScope.launch { settingsRepository.updateAiSettings(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
