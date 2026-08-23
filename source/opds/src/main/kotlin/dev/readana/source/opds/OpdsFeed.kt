package dev.readana.source.opds

/**
 * One parsed OPDS feed, in the shape both flavours (Atom 1.2 and JSON 2.0) reduce to. The source
 * turns this into `CatalogPage` or `SearchPage` depending on what was asked for.
 */
internal data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    /** Absolute URL of the next page of this feed. */
    val nextHref: String? = null,
    /** OpenSearch template (`{searchTerms}`) or a plain search URL, when the feed offers one. */
    val searchTemplate: String? = null
)

internal data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val year: Int? = null,
    val coverUrl: String? = null,
    /** The record's own web page, when the feed links one. */
    val detailUrl: String? = null,
    /** Where the file itself can be fetched, and in what format. */
    val acquisition: OpdsAcquisition? = null,
    /** Set instead of [acquisition] when the entry is a shelf to open rather than a book. */
    val navigationHref: String? = null
)

internal data class OpdsAcquisition(val href: String, val mimeType: String, val sizeBytes: Long? = null)
