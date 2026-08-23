package dev.readana.source.opds

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * The little bit of Atom handling that both OPDS 1.2 and the arXiv API need: parse in XML mode
 * (HTML mode lowercases and re-nests tags, which loses `<opds:price>` and friends), and turn the
 * relative hrefs feeds are full of into absolute ones.
 */
object AtomXml {
    /** Parses [xml] as XML rather than HTML, so element names and namespaces survive. */
    fun parse(xml: String, baseUrl: String): Document = Jsoup.parse(xml, baseUrl, Parser.xmlParser())

    /** Text of the first matching child, trimmed, or null when it is absent or empty. */
    fun Element.childText(tag: String): String? = selectFirst("> $tag")?.text()?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Resolves [href] against [base]. Feeds mix absolute URLs, root-relative paths and plain
     * relative ones, and a download URL that stayed relative is simply broken.
     *
     * OpenSearch templates carry `{searchTerms}` placeholders, and braces are not legal in a URI,
     * so they are hidden from the resolver and put back afterwards. Without this every templated
     * search link resolves to nothing and the catalog looks unsearchable.
     */
    fun resolve(base: String, href: String?): String? {
        val target = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val escaped = target.replace("{", BRACE_OPEN).replace("}", BRACE_CLOSE)
        return runCatching { URI(base).resolve(escaped).toString() }
            .getOrElse { escaped.takeIf { it.startsWith("http://") || it.startsWith("https://") } }
            ?.replace(BRACE_OPEN, "{")
            ?.replace(BRACE_CLOSE, "}")
    }

    /** The first four digits of a date like `2019-04-01T00:00:00Z`. */
    fun yearOf(date: String?): Int? = date?.trim()?.take(YEAR_DIGITS)?.toIntOrNull()

    private const val YEAR_DIGITS = 4
    private const val BRACE_OPEN = "%7B"
    private const val BRACE_CLOSE = "%7D"
}
