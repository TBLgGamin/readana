package dev.readana.source.api

import dev.readana.core.common.result.AppResult

/**
 * A source whose contents can be walked as well as searched: OPDS catalogs publish shelves
 * ("Fiction", "Recently added") that only make sense browsed. Plain [BookSource]s answer a query
 * and nothing else.
 */
interface CatalogSource : BookSource {
    /** Reads one feed; [feedUrl] null means the catalog's root. */
    suspend fun browse(feedUrl: String? = null): AppResult<CatalogPage>
}

/** One feed of a catalog: a heading, what it holds, and where the rest of it continues. */
data class CatalogPage(
    val title: String,
    val entries: List<CatalogEntry>,
    /** Absolute URL of the next page of this same feed, when the feed says it has one. */
    val nextHref: String? = null
)

sealed interface CatalogEntry {
    /** A shelf to open, not a book to read. */
    data class Section(val title: String, val href: String, val subtitle: String? = null) : CatalogEntry

    /** A book, in the same shape search results take, so downloading it needs no new code. */
    data class Publication(val result: SearchResult) : CatalogEntry
}
