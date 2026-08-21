package dev.readana.source.annas

import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Extracts search hits from Anna's Archive result HTML.
 *
 * The site has no public search API. Its result list comes in several layouts, and part of it is
 * shipped inside HTML comments that the page un-comments in JavaScript, so this parser
 *
 * 1. pulls the commented-out records back into the document,
 * 2. reads the `display=table` layout, whose columns are positional and therefore stable, and
 * 3. falls back to the card layouts, anchored on the one thing that never moves — links to
 *    `/md5/<32 hex>` — with regexes over the record's own meta line.
 *
 * Refresh the test fixtures from a live page whenever the layout drifts.
 */
object AnnasSearchParser {
    /** The md5 of the file, needed by every download route. */
    const val EXTRA_MD5 = "md5"

    /** Which shadow libraries hold the file ("libgen.li, z-library"), lowercased; may be absent. */
    const val EXTRA_COLLECTIONS = "collections"

    /** The file extension as the site reported it. */
    const val EXTRA_EXTENSION = "extension"

    /** Results Anna's Archive puts on one page; a full page means there is probably another. */
    const val RESULTS_PER_PAGE = 50

    private val MD5_PATH = Regex("/md5/([0-9a-fA-F]{32})")
    private val LANGUAGE = Regex("\\[([a-z]{2,3}(?:-[A-Za-z]{2,4})?)]")

    // Matches "EPUB" on its own and ".epub" in a filename; the lookarounds keep it out of words.
    private val EXTENSION = Regex(
        "(?<![A-Za-z0-9])\\.?(epub|pdf|mobi|azw3|azw|djvu|fb2|cbz|cbr|txt|rtf|doc|docx|lit)(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE
    )
    private val SIZE = Regex("(\\d+(?:[.,]\\d+)?)\\s?(KB|MB|GB)", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("\\b(1[5-9]\\d{2}|20\\d{2})\\b")
    private val META_REFRESH = Regex("""http-equiv=["']?refresh""", RegexOption.IGNORE_CASE)

    // Deliberately NOT the bare domain: parked/for-sale pages echo "annas-archive.li" in their title.
    private val SITE_MARKERS =
        listOf("Anna’s Archive", "Anna's Archive", "Anna&#39;s Archive", "Anna&#8217;s Archive")

    private const val MIN_CONTAINER_TEXT = 20
    private const val MAX_ANCESTOR_HOPS = 4
    private const val KIB = 1024L

    /** The card layout's bottom row: language, format, size, content type and collections. */
    private const val META_ROW = "div[class*=text-gray-800]"

    // Column order of the display=table layout, from the site's own aarecord_list.html macro.
    private const val COL_COVER = 0
    private const val COL_TITLE = 1
    private const val COL_AUTHOR = 2
    private const val COL_PUBLISHER = 3
    private const val COL_YEAR = 4
    private const val COL_SOURCES = 6
    private const val COL_LANGUAGES = 7
    private const val COL_EXTENSION = 9
    private const val COL_FILESIZE = 10
    private const val TABLE_COLUMNS = COL_FILESIZE + 1

    /** One page of hits, plus how many records the page held before unusable formats were dropped. */
    data class Page(val results: List<SearchResult>, val recordCount: Int)

    /** Heuristic guard against parked domains, challenge pages and ISP block notices. */
    fun looksLikeAnnasArchive(html: String): Boolean =
        SITE_MARKERS.any { html.contains(it, ignoreCase = true) } || MD5_PATH.containsMatchIn(html)

    /**
     * True for a gateway page that only bounces the browser somewhere else (`.li` answers every
     * search with a `<meta http-equiv="refresh">` carrying a tracking id). It mentions the site, so
     * [looksLikeAnnasArchive] accepts it, and it holds no results — without this check a search
     * would quietly report "no results" instead of moving to the next mirror.
     */
    fun isRedirectInterstitial(html: String): Boolean =
        !MD5_PATH.containsMatchIn(html) && META_REFRESH.containsMatchIn(html)

    fun parse(html: String, baseUrl: String, sourceId: SourceId): Page {
        val document = Jsoup.parse(html, baseUrl)
        val roots = listOf<Element>(document) + lazyFragments(document, baseUrl)
        val seen = HashSet<String>()
        val records = roots
            .flatMap { root -> tableRecords(root).ifEmpty { cardRecords(root) } }
            .filter { seen.add(it.md5) }

        return Page(
            results = records.mapNotNull { it.toSearchResult(baseUrl, sourceId) },
            recordCount = records.size
        )
    }

    /**
     * Anna's Archive ships part of the result list inside comment nodes and un-comments it in the
     * browser, so a plain DOM query finds nothing there. Parsed as a fragment, table rows only
     * survive inside a table, hence the wrapper.
     */
    private fun lazyFragments(document: Element, baseUrl: String): List<Element> = document.allElements
        .flatMap { element -> element.childNodes().filterIsInstance<Comment>() }
        .map { it.data }
        .filter { MD5_PATH.containsMatchIn(it) }
        .map { data ->
            val html = if (data.trimStart().startsWith("<tr", ignoreCase = true)) {
                "<table>$data</table>"
            } else {
                data
            }
            Jsoup.parseBodyFragment(html, baseUrl).body()
        }

    private fun tableRecords(root: Element): List<Record> = root.select("tr:has(a[href*=/md5/])")
        .mapNotNull { row ->
            val md5 = row.md5() ?: return@mapNotNull null
            val cells = row.children().filter { it.tagName().equals("td", ignoreCase = true) }
            if (cells.size < TABLE_COLUMNS) return@mapNotNull null
            val title = cells[COL_TITLE].text().trim()
            if (title.isEmpty()) return@mapNotNull null

            Record(
                md5 = md5,
                title = title,
                authors = cells[COL_AUTHOR].text().splitAuthors(),
                publisher = cells[COL_PUBLISHER].text().trim().takeIf { it.isNotEmpty() },
                year = YEAR.find(cells[COL_YEAR].text())?.value?.toIntOrNull(),
                language = cells[COL_LANGUAGES].text().language(),
                extension = cells[COL_EXTENSION].text().extension(),
                sizeBytes = cells[COL_FILESIZE].text().sizeBytes(),
                coverUrl = cells[COL_COVER].coverUrl(),
                collections = cells[COL_SOURCES].text().trim().lowercase().takeIf { it.isNotEmpty() }
            )
        }

    private fun cardRecords(root: Element): List<Record> = root.select("a[href*=/md5/]")
        .mapNotNull { anchor ->
            val md5 = anchor.md5() ?: return@mapNotNull null
            val container = resultContainer(anchor)
            val title = container.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() }
                ?: anchor.text().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            // The bottom row carries "English [en], EPUB, 3.8MB, Libgen.li"; the whole card's text
            // would drag the title and the blurb into the format, year and size regexes.
            val meta = container.selectFirst(META_ROW)?.text() ?: container.text()
            val searchLinks = container.select("a[href*=/search?q=]")
            val italic = container.selectFirst(".italic")?.text()

            Record(
                md5 = md5,
                title = title.trim(),
                authors = (italic ?: searchLinks.firstOrNull()?.text()).orEmpty().splitAuthors(),
                publisher = container.publisher(searchLinks),
                year = YEAR.findAll(meta).lastOrNull()?.value?.toIntOrNull(),
                language = meta.language(),
                extension = meta.extension(),
                sizeBytes = meta.sizeBytes(),
                // The card keeps its cover outside the text column, one level up.
                coverUrl = container.coverUrl() ?: container.parent()?.coverUrl(),
                collections = null
            )
        }

    private data class Record(
        val md5: String,
        val title: String,
        val authors: List<String>,
        val publisher: String?,
        val year: Int?,
        val language: String?,
        val extension: String?,
        val sizeBytes: Long?,
        val coverUrl: String?,
        val collections: String?
    ) {
        /**
         * Null when the site knows the format and it is one the app cannot open: passing it on
         * would download an .azw3 under an .epub name and fail at import instead of at search.
         */
        fun toSearchResult(baseUrl: String, sourceId: SourceId): SearchResult? {
            val format = extension?.let { BookFormat.fromExtension(it) }
            if (extension != null && format == null) return null

            return SearchResult(
                sourceId = sourceId,
                recordId = md5,
                title = title,
                authors = authors,
                year = year,
                language = language,
                format = format,
                sizeBytes = sizeBytes,
                coverUrl = coverUrl,
                publisher = publisher,
                detailUrl = "$baseUrl/md5/$md5",
                extras = buildMap {
                    put(EXTRA_MD5, md5)
                    extension?.let { put(EXTRA_EXTENSION, it) }
                    collections?.let { put(EXTRA_COLLECTIONS, it) }
                }
            )
        }
    }

    /**
     * The link may wrap the whole card, only the cover, or only the title, so walk up until the
     * record's meta row ("English [en], EPUB, 3.8MB, ...") is in scope. A title short enough to
     * look like an empty card is common, so length alone is only the last resort.
     */
    private fun resultContainer(anchor: Element): Element {
        val candidates = generateSequence(anchor) { it.parent() }.take(MAX_ANCESTOR_HOPS + 1).toList()
        return candidates.firstOrNull { it.selectFirst(META_ROW) != null }
            ?: candidates.firstOrNull { it.text().length >= MIN_CONTAINER_TEXT }
            ?: candidates.last()
    }

    private fun Element.md5(): String? {
        val href = selectFirst("a[href*=/md5/]")?.attr("href") ?: attr("href")
        return MD5_PATH.find(href)?.groupValues?.get(1)?.lowercase()
    }

    private fun Element.coverUrl(): String? = selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() }

    /**
     * The card layout links author and publisher to searches of their own, in that order; the
     * compact layout has neither, so fall back to the first line that carries a year.
     */
    private fun Element.publisher(searchLinks: Elements): String? = when {
        searchLinks.size > 1 -> searchLinks[1].text()

        else -> select("div").map { it.ownText() }.firstOrNull { YEAR.containsMatchIn(it) }
            ?.substringBeforeLast(',')
    }?.trim()?.takeIf { it.isNotBlank() && !YEAR.matches(it) }

    private fun String.language(): String? = LANGUAGE.find(this)?.groupValues?.get(1)

    private fun String.extension(): String? = EXTENSION.find(this)?.groupValues?.get(1)?.lowercase()

    private fun String.sizeBytes(): Long? = SIZE.find(this)?.let { match ->
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "KB" -> KIB
            "MB" -> KIB * KIB
            "GB" -> KIB * KIB * KIB
            else -> return null
        }
        (value * multiplier).toLong()
    }

    private fun String.splitAuthors(): List<String> = split(';', '&').map { it.trim() }.filter { it.isNotEmpty() }
}
