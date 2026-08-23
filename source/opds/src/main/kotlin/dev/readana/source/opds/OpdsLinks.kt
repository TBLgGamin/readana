package dev.readana.source.opds

import dev.readana.core.model.BookFormat

/**
 * Which OPDS link means what. Feeds are inconsistent about `rel` and `type` — some catalogs mark a
 * shelf with `rel="subsection"`, others only with an OPDS catalog media type — so both are
 * consulted before deciding.
 */
internal object OpdsLinks {
    const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
    const val REL_OPEN_ACCESS = "http://opds-spec.org/acquisition/open-access"
    const val REL_IMAGE = "http://opds-spec.org/image"
    const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"

    private const val CATALOG_TYPE = "application/atom+xml;profile=opds-catalog"
    private const val ENTRY_KIND = "kind=acquisition"

    /**
     * Acquisition kinds we deliberately do not offer. Borrowing and buying end in a DRM licence
     * this app cannot open, and showing them would mean a download that always fails.
     */
    private val UNSUPPORTED_ACQUISITION = setOf(
        "http://opds-spec.org/acquisition/borrow",
        "http://opds-spec.org/acquisition/buy",
        "http://opds-spec.org/acquisition/subscribe",
        "http://opds-spec.org/acquisition/sample"
    )

    fun isAcquisition(rel: String?, type: String?): Boolean {
        val relations = rel.relations()
        if (relations.any { it in UNSUPPORTED_ACQUISITION }) return false
        if (relations.any { it == REL_ACQUISITION || it == REL_OPEN_ACCESS }) return true
        // Feedbooks and some Calibre-Web builds leave `rel` off the download link entirely and
        // rely on the media type alone.
        return relations.isEmpty() && BookFormat.fromMimeType(type.orEmpty().baseType()) != null
    }

    fun isNavigation(rel: String?, type: String?): Boolean {
        val relations = rel.relations()
        if (relations.any { it == "subsection" || it == "collection" }) return true
        val normalized = type.orEmpty().lowercase().replace(" ", "")
        return normalized.startsWith(CATALOG_TYPE) && !normalized.contains(ENTRY_KIND)
    }

    fun isSearch(rel: String?): Boolean = rel.relations().any { it == "search" }

    fun isNext(rel: String?): Boolean = rel.relations().any { it == "next" }

    fun isCover(rel: String?): Boolean = rel.relations().any { it == REL_IMAGE || it == REL_THUMBNAIL || it == "cover" }

    fun isSelfPage(rel: String?): Boolean = rel.relations().any { it == "alternate" }

    /** Maps an acquisition link's media type onto something the app can actually open. */
    fun formatOf(mimeType: String?): BookFormat? = BookFormat.fromMimeType(mimeType.orEmpty().baseType())

    /** `rel` may hold several space-separated relations; compare them one at a time. */
    private fun String?.relations(): List<String> =
        orEmpty().trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Strips `;profile=…` and friends so a media type can be compared for equality. */
    private fun String.baseType(): String = substringBefore(';').trim().lowercase()
}
