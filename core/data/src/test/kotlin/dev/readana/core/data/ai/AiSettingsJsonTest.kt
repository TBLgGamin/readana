package dev.readana.core.data.ai

import dev.readana.core.model.AiContextSize
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiSettingsJsonTest {
    @Test
    fun `settings survive a round trip`() {
        val settings = AiSettings(
            providers = listOf(
                AiProviderConfig(
                    id = "custom-1",
                    kind = AiProviderKind.Custom,
                    name = "Laptop",
                    baseUrl = "http://192.168.1.5:11434/v1",
                    model = "llama3"
                )
            ),
            activeProviderId = "custom-1",
            contextSize = AiContextSize.Large,
            recapEnabled = false,
            recapAfterDays = 7
        ).withBuiltIns()

        val restored = settings.encodeToJson().decodeAiSettings()

        assertEquals(settings, restored)
        assertEquals("llama3", restored.active?.model)
    }

    @Test
    fun `an empty store yields a row for every built-in provider`() {
        val settings = null.decodeAiSettings()

        assertEquals(
            AiProviderKind.entries.filter { it != AiProviderKind.Custom }.map { it.name },
            settings.providers.map { it.id }
        )
        assertTrue(settings.active == null)
    }

    @Test
    fun `an unreadable blob falls back to defaults instead of failing`() {
        val settings = "{not json".decodeAiSettings()

        assertEquals(AiContextSize.Medium, settings.contextSize)
        assertNotNull(settings.providers.firstOrNull { it.kind == AiProviderKind.Anthropic })
    }

    @Test
    fun `a provider kind this build no longer knows is dropped`() {
        val json = """{"providers":[{"id":"x","kind":"Sorcery","name":"X"}],"activeProviderId":"x"}"""

        val settings = json.decodeAiSettings()

        assertTrue(settings.providers.none { it.id == "x" })
        assertTrue(settings.active == null)
    }

    @Test
    fun `out of range recap days are clamped`() {
        val settings = """{"recapAfterDays":99}""".decodeAiSettings()

        assertEquals(AiSettings.RECAP_DAYS_RANGE.last, settings.recapAfterDays)
    }
}
