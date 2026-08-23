package dev.readana.source.opds

import dev.readana.source.opds.AtomXml.childText
import org.jsoup.nodes.Element

/** Reads an OPDS 1.2 feed: Atom with the OPDS link relations layered on top. */
internal object OpdsAtomParser {
    fun parse(xml: String, baseUrl: String): OpdsFeed {
        val document = AtomXml.parse(xml, baseUrl)
        val root = document.selectFirst("feed") ?: error("not an Atom feed")
        val feedLinks = root.select("> link")
        return OpdsFeed(
            title = root.childText("title").orEmpty(),
            entries = root.select("> entry").map { it.toEntry(baseUrl) },
            nextHref = feedLinks
                .firstOrNull { OpdsLinks.isNext(it.attr("rel")) }
                ?.let { AtomXml.resolve(baseUrl, it.attr("href")) },
            searchTemplate = feedLinks
                .firstOrNull { OpdsLinks.isSearch(it.attr("rel")) }
                ?.let { AtomXml.resolve(baseUrl, it.attr("href")) }
        )
    }

    private fun Element.toEntry(baseUrl: String): OpdsEntry {
        val links = select("> link")
        val acquisition = links
            .firstOrNull { OpdsLinks.isAcquisition(it.attr("rel"), it.attr("type")) }
            ?.let { link ->
                val href = AtomXml.resolve(baseUrl, link.attr("href")) ?: return@let null
                OpdsAcquisition(
                    href = href,
                    mimeType = link.attr("type"),
                    // Calibre-Web and Standard Ebooks both publish the size; most others do not.
                    sizeBytes = link.attr("length").toLongOrNull()
                )
            }
        val navigation = links
            .firstOrNull { OpdsLinks.isNavigation(it.attr("rel"), it.attr("type")) }
            ?.let { AtomXml.resolve(baseUrl, it.attr("href")) }

        return OpdsEntry(
            id = childText("id") ?: childText("title").orEmpty(),
            title = childText("title").orEmpty(),
            authors = select("> author > name").mapNotNull { it.text().trim().takeIf(String::isNotEmpty) },
            summary = childText("summary") ?: childText("content"),
            publisher = childText("dcterms|publisher") ?: childText("dc|publisher"),
            language = childText("dcterms|language") ?: childText("dc|language"),
            year = AtomXml.yearOf(
                childText("dcterms|issued") ?: childText("published") ?: childText("updated")
            ),
            coverUrl = links
                .firstOrNull { OpdsLinks.isCover(it.attr("rel")) }
                ?.let { AtomXml.resolve(baseUrl, it.attr("href")) },
            detailUrl = links
                .firstOrNull { OpdsLinks.isSelfPage(it.attr("rel")) }
                ?.let { AtomXml.resolve(baseUrl, it.attr("href")) },
            acquisition = acquisition,
            // An entry with a downloadable file is a book even when it also links a sub-feed.
            navigationHref = navigation.takeIf { acquisition == null }
        )
    }
}
