package dev.readana.core.data.datastore

import dev.readana.core.model.CatalogRef
import dev.readana.core.model.StoredQuota
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Source configuration is nested (source id → field key → value) and open-ended, so it is stored
 * as one JSON blob per concern rather than as a DataStore key per field: a new source must not
 * mean a new preference key.
 */
internal object SourceSettingsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encodeConfig(config: Map<String, Map<String, String>>): String = json.encodeToString(config)

    fun decodeConfig(raw: String?): Map<String, Map<String, String>> = decode(raw, emptyMap())

    fun encodeQuotas(quotas: Map<String, StoredQuota>): String =
        json.encodeToString(quotas.mapValues { (_, quota) -> QuotaDto(quota.remaining, quota.perDay) })

    fun decodeQuotas(raw: String?): Map<String, StoredQuota> =
        decode<Map<String, QuotaDto>>(raw, emptyMap()).mapValues { (_, dto) ->
            StoredQuota(dto.remaining, dto.perDay)
        }

    fun encodeCatalogs(catalogs: List<CatalogRef>): String =
        json.encodeToString(catalogs.map { CatalogDto(it.id, it.name, it.feedUrl, it.presetId) })

    fun decodeCatalogs(raw: String?): List<CatalogRef> =
        decode<List<CatalogDto>>(raw, emptyList()).map { CatalogRef(it.id, it.name, it.feedUrl, it.presetId) }

    /** A blob written by an older build must never crash the app; a bad one reads as absent. */
    private inline fun <reified T> decode(raw: String?, fallback: T): T {
        val text = raw?.takeIf { it.isNotBlank() } ?: return fallback
        return runCatching { json.decodeFromString<T>(text) }.getOrDefault(fallback)
    }

    @Serializable
    private data class QuotaDto(val remaining: Int? = null, val perDay: Int? = null)

    @Serializable
    private data class CatalogDto(val id: String, val name: String, val feedUrl: String, val presetId: String? = null)
}
