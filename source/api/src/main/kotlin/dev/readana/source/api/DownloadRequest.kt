package dev.readana.source.api

import dev.readana.core.model.BookFormat

data class DownloadRequest(
    val url: String,
    val format: BookFormat,
    val suggestedFileName: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedSizeBytes: Long? = null,
    /**
     * True when the URL must be opened in an in-app browser (captcha, wait-list, cookie gate)
     * instead of being streamed by the download worker.
     */
    val requiresBrowser: Boolean = false,
    /** What the source's account allows right now, when it says so. */
    val quota: DownloadQuota? = null,
    /** How the in-app browser can sign in before loading [url]; only set when [requiresBrowser]. */
    val browserLogin: BrowserLogin? = null
)

/** Remaining allowance on a metered source such as an Anna's Archive membership. */
data class DownloadQuota(val remaining: Int?, val perDay: Int?) {
    val isExhausted: Boolean get() = remaining != null && remaining <= 0
}

/**
 * A form POST that signs the in-app browser in. Anna's Archive has no username/password: the
 * membership secret key posted to the account page is both the credential and the session.
 */
data class BrowserLogin(val url: String, val formBody: String)

/** Builds file-system-safe names like `Pride and Prejudice - Jane Austen.epub`. */
object FileNames {
    private const val MAX_BASE_LENGTH = 120
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val whitespace = Regex("""\s+""")

    fun suggest(title: String, authors: List<String>, format: BookFormat): String {
        val base = buildString {
            append(title.trim())
            if (authors.isNotEmpty()) {
                append(" - ")
                append(authors.joinToString(", "))
            }
        }
        val cleaned = base.replace(illegal, "").replace(whitespace, " ").trim().ifEmpty { "book" }
        return cleaned.take(MAX_BASE_LENGTH).trimEnd('.', ' ') + "." + format.extension
    }
}
