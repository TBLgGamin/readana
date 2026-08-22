package dev.readana.feature.downloads

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.readana.core.model.BrowserDownload
import kotlinx.serialization.Serializable

/** Navigation 3 key for the download queue. */
@Serializable
data object DownloadsKey : NavKey

fun EntryProviderScope<NavKey>.downloadsEntry(
    onOpenBook: (bookId: String) -> Unit,
    onOpenBrowser: (BrowserDownload) -> Unit
) {
    entry<DownloadsKey> { DownloadsRoute(onOpenBook = onOpenBook, onOpenBrowser = onOpenBrowser) }
}
