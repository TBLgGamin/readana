package dev.readana.core.model

/**
 * A large-language-model provider Readana knows how to talk to. Everything but [Custom] ships with
 * working defaults, so configuring one is a matter of pasting a key.
 *
 * The endpoint dialect is not part of this enum: `ai/client` maps each kind onto a wire format, so
 * `core/model` stays free of transport concerns.
 */
enum class AiProviderKind(
    val displayName: String,
    /** Base URL used when the user has not overridden it. Null only for [Custom]. */
    val defaultBaseUrl: String?,
    val defaultModel: String?,
    /** Where the user gets a key, opened from the settings screen. */
    val consoleUrl: String?,
    /** Local servers (and some gateways) accept requests without one. */
    val keyRequired: Boolean = true
) {
    Anthropic(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-sonnet-5",
        consoleUrl = "https://platform.claude.com/settings/keys"
    ),
    OpenAi(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5.6-luna",
        consoleUrl = "https://platform.openai.com/api-keys"
    ),
    Gemini(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-3.6-flash",
        consoleUrl = "https://aistudio.google.com/apikey"
    ),
    OpenRouter(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "anthropic/claude-sonnet-5",
        consoleUrl = "https://openrouter.ai/keys"
    ),
    DeepSeek(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-v4-flash",
        consoleUrl = "https://platform.deepseek.com/api_keys"
    ),

    /** Any OpenAI-compatible endpoint: Ollama, LM Studio, Groq, Mistral, a private gateway. */
    Custom(
        displayName = "Custom",
        defaultBaseUrl = null,
        defaultModel = null,
        consoleUrl = null,
        keyRequired = false
    );

    /** True when the user may point this provider somewhere else than its default host. */
    val allowsCustomBaseUrl: Boolean get() = this == Custom
}

/**
 * How much of the already-read text is sent along with a question. Bigger means better answers
 * about earlier chapters and a bigger bill; the text is always cut at the reading position.
 */
enum class AiContextSize(val maxChars: Int) {
    Small(60_000),
    Medium(240_000),
    Large(800_000),

    /** Everything read so far. Only sensible on providers with very large context windows. */
    Whole(Int.MAX_VALUE)
}

/** One configured provider. Built-in kinds get a row even before a key is pasted. */
data class AiProviderConfig(
    val id: String,
    val kind: AiProviderKind,
    val name: String,
    /** Overrides [AiProviderKind.defaultBaseUrl]; required for [AiProviderKind.Custom]. */
    val baseUrl: String? = null,
    /** Stored in app-private storage; never logged. */
    val apiKey: String? = null,
    val model: String? = null
) {
    val effectiveBaseUrl: String? get() = baseUrl?.trim()?.ifEmpty { null } ?: kind.defaultBaseUrl

    val effectiveModel: String? get() = model?.trim()?.ifEmpty { null } ?: kind.defaultModel

    /** Usable when it knows where to go, what to ask for, and has a key if the host wants one. */
    val isUsable: Boolean
        get() = !effectiveBaseUrl.isNullOrBlank() &&
            !effectiveModel.isNullOrBlank() &&
            (!kind.keyRequired || !apiKey.isNullOrBlank())
}

/** User-editable configuration for the reading assistant; persisted as JSON in DataStore. */
data class AiSettings(
    val providers: List<AiProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val contextSize: AiContextSize = AiContextSize.Medium,
    /** Offer a recap when a book is reopened after [recapAfterDays]. */
    val recapEnabled: Boolean = true,
    val recapAfterDays: Int = DEFAULT_RECAP_AFTER_DAYS
) {
    val active: AiProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    /** True once a provider is selected and complete enough to send a request. */
    val isConfigured: Boolean get() = active?.isUsable == true

    /** Adds a row for every built-in kind that has none, so the settings list is always complete. */
    fun withBuiltIns(): AiSettings {
        val missing = AiProviderKind.entries
            .filter { it != AiProviderKind.Custom }
            .filter { kind -> providers.none { it.id == kind.name } }
            .map { kind -> AiProviderConfig(id = kind.name, kind = kind, name = kind.displayName) }
        return if (missing.isEmpty()) this else copy(providers = providers + missing)
    }

    fun withProvider(id: String, transform: (AiProviderConfig) -> AiProviderConfig): AiSettings =
        copy(providers = providers.map { if (it.id == id) transform(it) else it }).withActiveDefaulted()

    /**
     * Picks a provider to use when none is chosen, or when the chosen one has become unusable.
     * Pasting a key is the whole of what a reader means to do; making them also find a radio
     * button before anything works is a trap.
     */
    fun withActiveDefaulted(): AiSettings {
        if (active?.isUsable == true) return this
        return copy(activeProviderId = providers.firstOrNull { it.isUsable }?.id ?: activeProviderId)
    }

    companion object {
        const val DEFAULT_RECAP_AFTER_DAYS = 3
        val RECAP_DAYS_RANGE = 1..14
        const val CUSTOM_ID_PREFIX = "custom-"
    }
}
