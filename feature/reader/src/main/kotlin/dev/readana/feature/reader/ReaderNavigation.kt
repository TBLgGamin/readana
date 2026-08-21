package dev.readana.feature.reader

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 key that opens a library book full-screen. */
@Serializable
data class ReaderKey(val bookId: String) : NavKey

fun EntryProviderScope<NavKey>.readerEntry(onBack: () -> Unit) {
    entry<ReaderKey> { key -> ReaderRoute(bookId = key.bookId, onBack = onBack) }
}
