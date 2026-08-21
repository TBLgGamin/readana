package dev.readana.feature.settings

import app.cash.turbine.test
import dev.readana.ai.client.LlmModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.ai.AiProviderProbe
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiContextSize
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiSettings
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import dev.readana.core.testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class AiSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = AiOnlySettingsRepository()
    private val probe = FakeProviderProbe()

    private fun viewModel() = AiSettingsViewModel(settings, probe)

    @Test
    fun `every built-in provider is listed, and none is active until it can be used`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(
                AiProviderKind.entries.filter { it != AiProviderKind.Custom }.map { it.name },
                state.settings.providers.map { it.id }
            )
            assertNull(state.settings.active)
        }
    }

    @Test
    fun `pasting a key makes a provider usable`() = runTest {
        val viewModel = viewModel()

        viewModel.setApiKey(AiProviderKind.Anthropic.name, " secret ")
        viewModel.setActiveProvider(AiProviderKind.Anthropic.name)
        advanceUntilIdle()

        val active = assertNotNull(settings.aiSettingsNow.active)
        assertEquals("secret", active.apiKey)
        assertTrue(settings.aiSettingsNow.isConfigured)
    }

    @Test
    fun `a custom provider is added and can be removed again`() = runTest {
        val viewModel = viewModel()

        viewModel.addCustomProvider("Laptop")
        advanceUntilIdle()
        val added = assertNotNull(settings.aiSettingsNow.providers.lastOrNull())
        assertEquals(AiProviderKind.Custom, added.kind)
        assertEquals(added.id, settings.aiSettingsNow.activeProviderId)

        viewModel.removeProvider(added.id)
        advanceUntilIdle()
        assertTrue(settings.aiSettingsNow.providers.none { it.id == added.id })
        assertNull(settings.aiSettingsNow.activeProviderId)
    }

    @Test
    fun `fetching models fills the chips`() = runTest {
        probe.models = listOf(LlmModel("claude-sonnet-5"), LlmModel("claude-haiku-4-5"))
        val viewModel = viewModel()

        // The state only recomputes while something is collecting it, as it does on screen.
        viewModel.uiState.test {
            awaitItem()
            viewModel.fetchModels(AiProviderKind.Anthropic.name)
            advanceUntilIdle()
            assertEquals(
                listOf("claude-haiku-4-5", "claude-sonnet-5"),
                expectMostRecentItem().probes.getValue(AiProviderKind.Anthropic.name).models
            )
        }
    }

    @Test
    fun `a failed test shows the provider's own message`() = runTest {
        probe.failure = AppError.Unauthorized("invalid x-api-key")
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.testProvider(AiProviderKind.Anthropic.name)
            advanceUntilIdle()
            val result = expectMostRecentItem().probes.getValue(AiProviderKind.Anthropic.name)
            assertEquals("invalid x-api-key", result.error)
            assertNull(result.latencyMillis)
        }
    }

    @Test
    fun `context size and recap settings are stored`() = runTest {
        val viewModel = viewModel()

        viewModel.setContextSize(AiContextSize.Large)
        viewModel.setRecapEnabled(false)
        viewModel.setRecapAfterDays(99)
        advanceUntilIdle()

        assertEquals(AiContextSize.Large, settings.aiSettingsNow.contextSize)
        assertEquals(false, settings.aiSettingsNow.recapEnabled)
        assertEquals(AiSettings.RECAP_DAYS_RANGE.last, settings.aiSettingsNow.recapAfterDays)
    }
}

/** Only the assistant's settings matter here; the rest of the interface is inert. */
private class AiOnlySettingsRepository : SettingsRepository {
    private val ai = MutableStateFlow(AiSettings().withBuiltIns())

    /** Latest value, for tests asserting on what was written. */
    val aiSettingsNow: AiSettings get() = ai.value

    override val readerSettings: Flow<ReaderSettings> = MutableStateFlow(ReaderSettings())

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = Unit

    override val sourceSettings: Flow<SourceSettings> = MutableStateFlow(SourceSettings())

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) = Unit

    override val conversionSettings: Flow<ConversionSettings> = MutableStateFlow(ConversionSettings())

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) = Unit

    override val aiSettings: Flow<AiSettings> = ai

    override suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings) = ai.update(transform)

    override val libraryFolderUri: Flow<String?> = MutableStateFlow(null)

    override suspend fun setLibraryFolderUri(uri: String?) = Unit
}

private class FakeProviderProbe : AiProviderProbe {
    var models: List<LlmModel> = emptyList()
    var failure: AppError? = null

    override suspend fun listModels(config: AiProviderConfig): AppResult<List<LlmModel>> =
        failure?.asFailure() ?: models.asSuccess()

    override suspend fun test(config: AiProviderConfig): AppResult<Long> =
        failure?.asFailure() ?: TEST_LATENCY_MS.asSuccess()

    private companion object {
        const val TEST_LATENCY_MS = 42L
    }
}
