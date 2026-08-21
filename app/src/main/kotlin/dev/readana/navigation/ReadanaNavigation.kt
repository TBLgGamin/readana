package dev.readana.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.readana.feature.browser.BrowserKey
import dev.readana.feature.discover.DiscoverKey
import dev.readana.feature.library.LibraryKey
import dev.readana.feature.reader.ReaderKey
import dev.readana.feature.settings.SettingsKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Navigation 3 persists the back stack across process death with kotlinx.serialization, which
 * needs every [NavKey] registered as a polymorphic subclass. Add new keys here.
 */
val readanaSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LibraryKey::class)
            subclass(DiscoverKey::class)
            subclass(SettingsKey::class)
            subclass(ReaderKey::class)
            subclass(BrowserKey::class)
        }
    }
}

/** Switches tabs while keeping the library as the root, so system back always lands there. */
fun NavBackStack<NavKey>.navigateToTopLevel(key: NavKey) {
    if (lastOrNull() == key) return
    val root = firstOrNull() ?: LibraryKey
    clear()
    add(root)
    if (key != root) add(key)
}
