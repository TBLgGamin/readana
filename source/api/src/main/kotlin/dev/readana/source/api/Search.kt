package dev.readana.source.api

import dev.readana.core.model.BookFormat

data class SearchQuery(
    val text: String,
    val formats: Set<BookFormat> = BookFormat.entries.toSet(),
    /** ISO 639-1 code such as `en` or `nl`, or null for any language. */
    val language: String? = null,
    val page: Int = 1
) {
    init {
        require(page >= 1) { "page is 1-based" }
        require(formats.isNotEmpty()) { "at least one format must be requested" }
    }
}

data class SearchPage(
    val results: List<SearchResult>,
    val page: Int,
    val hasMore: Boolean,
    /**
     * Where the next page lives, for sources that link to it rather than counting pages (OPDS
     * feeds carry `rel="next"`). Page-numbered sources leave it null and use [hasMore].
     */
    val nextHref: String? = null
)

data class SearchResult(
    val sourceId: SourceId,
    /** Stable identifier inside the source (Gutenberg id, MD5, ...). */
    val recordId: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val language: String? = null,
    val format: BookFormat? = null,
    val sizeBytes: Long? = null,
    val coverUrl: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    /** Web page for the record, handy for a "view on site" action. */
    val detailUrl: String? = null,
    /** Source-private data needed later by [BookSource.resolveDownload] (download URLs etc.). */
    val extras: Map<String, String> = emptyMap()
)
