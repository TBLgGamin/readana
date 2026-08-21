package dev.readana.source.annas

import dev.readana.core.common.coroutines.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.jsoup.Jsoup

/**
 * Finds a Library Genesis download link for a file Anna's Archive indexes.
 *
 * Anna's Archive is a catalogue: most of what it lists actually lives in Library Genesis, which
 * serves the same file by md5 with no browser check and no wait. Asking there first means a free
 * download usually costs one request and starts immediately.
 *
 * The link page hands out a signed `get.php` URL; it is the only part that has to be scraped, and
 * it is a plain anchor.
 */
internal class LibgenDirectLinks(
    private val client: HttpClient,
    private val hosts: List<String> = AnnasConfig.LIBGEN_HOSTS
) {
    /** The download URL and the page it came from, which the file server expects as the referer. */
    data class Link(val url: String, val referer: String)

    suspend fun resolve(md5: String): Link? {
        hosts.forEach { host ->
            val page = "$host/ads.php?md5=$md5"
            val html = suspendRunCatching {
                val response = client.get(page)
                if (response.status.isSuccess()) response.bodyAsText() else null
            }.getOrNull() ?: return@forEach

            // Every Libgen mirror serves the same catalogue, so "not here" is not worth re-asking.
            return directLink(html, page)?.let { Link(it, page) }
        }
        return null
    }

    private companion object {
        fun directLink(html: String, pageUrl: String): String? = Jsoup.parse(html, pageUrl)
            .select("a[href*=get.php]")
            .firstOrNull { it.attr("href").contains("md5=", ignoreCase = true) }
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
    }
}
