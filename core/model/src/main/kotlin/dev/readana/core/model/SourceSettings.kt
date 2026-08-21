package dev.readana.core.model

/** User-editable configuration for book sources; persisted in DataStore. */
data class SourceSettings(
    /**
     * Explicit per-source enable/disable choices keyed by source id. Sources absent from the map
     * use their own default (Anna's Archive is off until the user opts in).
     */
    val sourceOverrides: Map<String, Boolean> = emptyMap(),
    /** Base URLs tried in order; empty falls back to the source's built-in defaults. */
    val annasMirrors: List<String> = emptyList(),
    /** Anna's Archive member key. Stored in app-private storage; never logged. */
    val annasSecretKey: String? = null,
    /** The Anna's Archive mirror that last answered, so a search does not re-probe dead ones. */
    val annasPreferredMirror: String? = null,
    /** Fast downloads the membership had left when it last answered; null until a download runs. */
    val annasDownloadsLeft: Int? = null,
    val annasDownloadsPerDay: Int? = null
) {
    fun isEnabled(sourceId: String, enabledByDefault: Boolean): Boolean = sourceOverrides[sourceId] ?: enabledByDefault

    fun withSourceEnabled(sourceId: String, enabled: Boolean): SourceSettings =
        copy(sourceOverrides = sourceOverrides + (sourceId to enabled))
}
