package dev.readana.feature.library

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 key for the library (the app's start destination). */
@Serializable
data object LibraryKey : NavKey

fun EntryProviderScope<NavKey>.libraryEntry(onOpenBook: (bookId: String) -> Unit) {
    entry<LibraryKey> { LibraryRoute(onOpenBook = onOpenBook) }
}
