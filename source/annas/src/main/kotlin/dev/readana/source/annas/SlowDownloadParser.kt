package dev.readana.source.annas

import org.jsoup.Jsoup

/**
 * Reads Anna's Archive's free "partner download" page.
 *
 * The page is one of two things: the signed download link itself, good for a couple of hours, or a
 * countdown that has to run out first. Which one depends on the server picked and on how much the
 * connection has downloaded today.
 */
internal object SlowDownloadParser {
    sealed interface Outcome {
        /** The file, on a partner server. A plain GET, no cookies needed. */
        data class Link(val url: String) : Outcome

        /** The server wants the page reloaded in this many seconds. */
        data class Wait(val seconds: Int) : Outcome

        /** Neither: a wrong server number, a record page, or a layout we do not know. */
        data object Unrecognised : Outcome
    }

    // "let waitSeconds = 42;" drives the page's own countdown.
    private val WAIT_SCRIPT = Regex("""waitSeconds\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val WAIT_TEXT = Regex("""(\d+)\s*seconds""", RegexOption.IGNORE_CASE)

    // Partner links are served from their own hosts under a /d<digit>/ path.
    private val PARTNER_PATH = Regex("""^https?://[^/]+/d\d+/""", RegexOption.IGNORE_CASE)

    fun parse(html: String, pageUrl: String, md5: String): Outcome {
        val document = Jsoup.parse(html, pageUrl)
        val link = document.select("a[href]")
            .map { it.absUrl("href") }
            .firstOrNull { PARTNER_PATH.containsMatchIn(it) && it.contains(md5, ignoreCase = true) }
            ?: document.select("a[href]").map { it.absUrl("href") }
                .firstOrNull { PARTNER_PATH.containsMatchIn(it) }
        if (link != null) return Outcome.Link(link)

        val seconds = WAIT_SCRIPT.find(html)?.groupValues?.get(1)
            ?: WAIT_TEXT.find(document.text())?.groupValues?.get(1)
        return seconds?.toIntOrNull()?.let { Outcome.Wait(it) } ?: Outcome.Unrecognised
    }
}
