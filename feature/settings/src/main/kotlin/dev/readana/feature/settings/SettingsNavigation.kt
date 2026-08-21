package dev.readana.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 key for app and reader settings. */
@Serializable
data object SettingsKey : NavKey

fun EntryProviderScope<NavKey>.settingsEntry() {
    entry<SettingsKey> { SettingsRoute() }
}
