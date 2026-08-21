package dev.readana.source.annas

import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Extracts search hits from Anna's Archive result HTML.
 *
 * The site has no public search API, and its Tailwind class names change often, so this parser
 * anchors on the one stable thing — links to `/md5/<32 hex>` — and reads metadata with regexes
 * over the surrounding text ("English [en], .epub, 0.8MB, ..."). Refresh the test fixture
 * from a live page whenever the layout drifts.
 */
object AnnasSearchParser {
    private val MD5_PATH = Regex("/md5/([0-9a-fA-F]{32})")
    private val LANGUAGE = Regex("\\[([a-z]{2,3}(?:-[A-Za-z]{2,4})?)]")
    private val EXTENSION =
        Regex("\\.(epub|pdf|mobi|azw3|djvu|fb2|cbz|cbr|txt|doc|docx)\\b", RegexOption.IGNORE_CASE)
    private val SIZE = Regex("(\\d+(?:[.,]\\d+)?)\\s?(KB|MB|GB)", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("\\b(1[5-9]\\d{2}|20\\d{2})\\b")

    // Deliberately NOT the bare domain: parked/for-sale pages echo "annas-archive.li" in their title.
    private val SITE_MARKERS =
        listOf("Anna’s Archive", "Anna's Archive", "Anna&#39;s Archive", "Anna&#8217;s Archive")

    private const val MIN_CONTAINER_TEXT = 20
    private const val MAX_ANCESTOR_HOPS = 3
    private const val KIB = 1024L

    /** Heuristic guard against parked domains, DDoS challenge pages and ISP block notices. */
    fun looksLikeAnnasArchive(html: String): Boolean =
        SITE_MARKERS.any { html.contains(it, ignoreCase = true) } || MD5_PATH.containsMatchIn(html)

    fun parse(html: String, baseUrl: String, sourceId: SourceId): List<SearchResult> {
        val document = Jsoup.parse(html, baseUrl)
        val seen = HashSet<String>()
        return document.select("a[href*=/md5/]").mapNotNull { anchor ->
            val md5 =
                MD5_PATH.find(anchor.attr("href"))?.groupValues?.get(1)?.lowercase()
                    ?: return@mapNotNull null
            if (!seen.add(md5)) return@mapNotNull null
            val container = resultContainer(anchor)
            val title = container.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() }
                ?: anchor.text().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val metaText = container.text()

            SearchResult(
                sourceId = sourceId,
                recordId = md5,
                title = title.trim(),
                authors = container.selectFirst(".italic")?.text()?.splitAuthors().orEmpty(),
                year = YEAR.findAll(metaText).lastOrNull()?.value?.toIntOrNull(),
                language = LANGUAGE.find(metaText)?.groupValues?.get(1),
                format = EXTENSION.find(metaText)?.groupValues?.get(1)?.let {
                    BookFormat.fromExtension(it)
                },
                sizeBytes = SIZE.find(metaText)?.let {
                    parseSize(it.groupValues[1], it.groupValues[2])
                },
                coverUrl = container.selectFirst("img[src]")?.absUrl("src")?.takeIf {
                    it.isNotBlank()
                },
                publisher = container.select("div").map {
                    it.ownText()
                }.firstOrNull { YEAR.containsMatchIn(it) }
                    ?.substringBeforeLast(',')?.trim()?.takeIf {
                        it.isNotBlank() &&
                            !YEAR.matches(it)
                    },
                detailUrl = "$baseUrl/md5/$md5",
                extras = mapOf(EXTRA_MD5 to md5)
            )
        }
    }

    /** The anchor usually wraps the whole card; if it only wraps the title, walk up a little. */
    private fun resultContainer(anchor: Element): Element {
        var element = anchor
        var hops = 0
        while (element.text().length < MIN_CONTAINER_TEXT && hops < MAX_ANCESTOR_HOPS) {
            element = element.parent() ?: break
            hops++
        }
        return element
    }

    private fun String.splitAuthors(): List<String> = split(';', '&').map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseSize(amount: String, unit: String): Long? {
        val value = amount.replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (unit.uppercase()) {
            "KB" -> KIB
            "MB" -> KIB * KIB
            "GB" -> KIB * KIB * KIB
            else -> return null
        }
        return (value * multiplier).toLong()
    }

    const val EXTRA_MD5 = "md5"
}
