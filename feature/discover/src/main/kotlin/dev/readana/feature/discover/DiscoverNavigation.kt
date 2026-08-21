package dev.readana.feature.discover

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 key for searching book sources. */
@Serializable
data object DiscoverKey : NavKey

fun EntryProviderScope<NavKey>.discoverEntry(onVerifyInBrowser: (url: String, title: String) -> Unit) {
    entry<DiscoverKey> { DiscoverRoute(onVerifyInBrowser = onVerifyInBrowser) }
}
