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
    val requiresBrowser: Boolean = false
)

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
