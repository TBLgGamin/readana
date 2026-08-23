package dev.readana.core.data.repository

import dev.readana.core.model.CatalogPreset
import dev.readana.source.annas.AnnasConfig
import dev.readana.source.opds.PresetCatalogs

/**
 * Source defaults that the UI needs to show. Re-exported here because the source modules are an
 * implementation detail of `core/data`, and features should not depend on them directly.
 */
object SourceDefaults {
    /** Mirrors used when the user has not entered any of their own. */
    val ANNAS_MIRRORS: List<String> = AnnasConfig.DEFAULT_MIRRORS

    /** Catalogs offered ready-made on the Sources settings page. */
    val CATALOG_PRESETS: List<CatalogPreset> = PresetCatalogs.all.map {
        CatalogPreset(id = it.id, name = it.name, feedUrl = it.feedUrl, description = it.description)
    }
}
