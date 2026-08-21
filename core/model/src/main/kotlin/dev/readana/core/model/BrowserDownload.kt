package dev.readana.core.model

/**
 * Everything the in-app browser needs to finish a download the API could not serve: the record
 * page to open, who it belongs to, and an optional form post that signs the session in first.
 *
 * Lives in `core/model` because features cannot see each other: Discover and Library hand this to
 * the app, which turns it into the browser feature's navigation key.
 */
data class BrowserDownload(
    val sourceId: String,
    val recordId: String,
    val title: String,
    val authors: List<String>,
    val format: BookFormat,
    val url: String,
    val loginUrl: String? = null,
    val loginFormBody: String? = null
)
