package dev.readana.feature.discover

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 key for searching book sources. */
@Serializable
data object DiscoverKey : NavKey

/**
 * One feed of one catalog. [feedUrl] null means the catalog's root; opening a shelf pushes another
 * key rather than mutating this one, so system back walks back out of the catalog.
 */
@Serializable
data class CatalogKey(val sourceId: String, val feedUrl: String? = null, val title: String = "") : NavKey

fun EntryProviderScope<NavKey>.discoverEntry(
    onVerifyInBrowser: (url: String, title: String) -> Unit,
    onOpenCatalog: (CatalogKey) -> Unit
) {
    entry<DiscoverKey> {
        DiscoverRoute(onVerifyInBrowser = onVerifyInBrowser, onOpenCatalog = onOpenCatalog)
    }
}

fun EntryProviderScope<NavKey>.catalogEntry(onBack: () -> Unit, onOpenSection: (CatalogKey) -> Unit) {
    entry<CatalogKey> { key ->
        CatalogRoute(catalogKey = key, onBack = onBack, onOpenSection = onOpenSection)
    }
}
