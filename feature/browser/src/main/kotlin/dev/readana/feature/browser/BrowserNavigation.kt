package dev.readana.feature.browser

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import kotlinx.serialization.Serializable

/**
 * Navigation 3 key for the in-app browser. All fields are primitives so the back stack survives
 * process death without custom serializers.
 */
@Serializable
data class BrowserKey(
    val url: String,
    val sourceId: String,
    val recordId: String,
    val title: String,
    val authors: String,
    val format: String,
    val loginUrl: String? = null,
    val loginFormBody: String? = null
) : NavKey {
    internal fun toDownload(capturedUrl: String, headers: Map<String, String>, sizeBytes: Long?) = CapturedFile(
        sourceId = sourceId,
        recordId = recordId,
        title = title,
        authors = authors.split('|').filter { it.isNotBlank() },
        format = BookFormat.entries.firstOrNull { it.name == format } ?: BookFormat.EPUB,
        url = capturedUrl,
        headers = headers,
        sizeBytes = sizeBytes
    )

    companion object {
        fun of(download: BrowserDownload) = BrowserKey(
            url = download.url,
            sourceId = download.sourceId,
            recordId = download.recordId,
            title = download.title,
            authors = download.authors.joinToString("|"),
            format = download.format.name,
            loginUrl = download.loginUrl,
            loginFormBody = download.loginFormBody
        )
    }
}

/** What the WebView's download listener saw. */
internal data class CapturedFile(
    val sourceId: String,
    val recordId: String,
    val title: String,
    val authors: List<String>,
    val format: BookFormat,
    val url: String,
    val headers: Map<String, String>,
    val sizeBytes: Long?
)

fun EntryProviderScope<NavKey>.browserEntry(onClose: () -> Unit) {
    entry<BrowserKey> { key -> BrowserRoute(key = key, onClose = onClose) }
}
