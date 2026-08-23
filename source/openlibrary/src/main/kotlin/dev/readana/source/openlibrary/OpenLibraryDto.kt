package dev.readana.source.openlibrary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponse(val numFound: Int = 0, val start: Int = 0, val docs: List<SearchDoc> = emptyList())

@Serializable
internal data class SearchDoc(
    val key: String? = null,
    val title: String? = null,
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    @SerialName("cover_i") val coverId: Int? = null,
    val language: List<String> = emptyList(),
    val publisher: List<String> = emptyList(),
    /** Internet Archive identifiers holding scans of this work. */
    val ia: List<String> = emptyList(),
    /**
     * `public`, `borrowable`, `printdisabled` or `no_ebook`. Only `public` can actually be
     * downloaded; everything else ends in a DRM licence this app cannot open.
     */
    @SerialName("ebook_access") val ebookAccess: String? = null
)

/** The subset of an Internet Archive item's metadata needed to pick a file. */
@Serializable
internal data class ArchiveMetadata(
    val files: List<ArchiveFile> = emptyList(),
    @SerialName("server") val server: String? = null,
    val dir: String? = null
)

@Serializable
internal data class ArchiveFile(val name: String? = null, val format: String? = null, val size: String? = null)
