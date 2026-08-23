package dev.readana.core.model

/** User-editable configuration for book sources; persisted in DataStore. */
data class SourceSettings(
    /**
     * Explicit per-source enable/disable choices keyed by source id. Sources absent from the map
     * use their own default (Anna's Archive is off until the user opts in).
     */
    val sourceOverrides: Map<String, Boolean> = emptyMap(),
    /**
     * What the user filled in for each source, keyed by source id and then by the key of the
     * `SourceConfigField` it belongs to. Mirrors, API keys and catalog credentials all live here,
     * so no source needs a field of its own on this class.
     */
    val config: Map<String, Map<String, String>> = emptyMap(),
    /** What a metered source said it had left, keyed by source id. */
    val quotas: Map<String, StoredQuota> = emptyMap(),
    /** Catalogs the user added, in the order they were added. */
    val catalogs: List<CatalogRef> = emptyList()
) {
    fun isEnabled(sourceId: String, enabledByDefault: Boolean): Boolean = sourceOverrides[sourceId] ?: enabledByDefault

    fun withSourceEnabled(sourceId: String, enabled: Boolean): SourceSettings =
        copy(sourceOverrides = sourceOverrides + (sourceId to enabled))

    fun configFor(sourceId: String): Map<String, String> = config[sourceId].orEmpty()

    /** Blank values are removed rather than stored, so "empty" always means "use the default". */
    fun withConfigValue(sourceId: String, key: String, value: String): SourceSettings {
        val existing = configFor(sourceId)
        val updated = if (value.isBlank()) existing - key else existing + (key to value.trim())
        return copy(
            config = if (updated.isEmpty()) config - sourceId else config + (sourceId to updated)
        )
    }

    fun withQuota(sourceId: String, quota: StoredQuota?): SourceSettings =
        copy(quotas = if (quota == null) quotas - sourceId else quotas + (sourceId to quota))

    fun withCatalog(catalog: CatalogRef): SourceSettings =
        copy(catalogs = catalogs.filterNot { it.id == catalog.id } + catalog)

    /** Forgetting a catalog forgets what was configured for it too; nothing else refers to it. */
    fun withoutCatalog(catalogId: String): SourceSettings = copy(
        catalogs = catalogs.filterNot { it.id == catalogId },
        config = config - catalogId,
        sourceOverrides = sourceOverrides - catalogId,
        quotas = quotas - catalogId
    )
}

/** A catalog the user added. Its [id] doubles as the source id once the registry builds it. */
data class CatalogRef(
    val id: String,
    val name: String,
    val feedUrl: String,
    /** Set when the catalog came from the built-in list rather than a pasted URL. */
    val presetId: String? = null
)

/** What a metered source (an Anna's Archive membership, say) had left when it last answered. */
data class StoredQuota(val remaining: Int? = null, val perDay: Int? = null)
