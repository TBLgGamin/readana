package dev.readana.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import dev.readana.R
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.feature.discover.DiscoverKey
import dev.readana.feature.downloads.DownloadsKey
import dev.readana.feature.library.LibraryKey
import dev.readana.feature.settings.SettingsKey

/** Destinations shown in the navigation bar / rail. Order defines display order. */
enum class TopLevelDestination(val key: NavKey, val icon: ImageVector, @param:StringRes val labelRes: Int) {
    Library(LibraryKey, ReadanaIcons.Library, R.string.app_nav_library),
    Downloads(DownloadsKey, ReadanaIcons.Download, R.string.app_nav_downloads),
    Discover(DiscoverKey, ReadanaIcons.Discover, R.string.app_nav_discover),
    Settings(SettingsKey, ReadanaIcons.Settings, R.string.app_nav_settings)
    ;

    companion object {
        fun of(key: NavKey?): TopLevelDestination? = entries.firstOrNull { it.key == key }
    }
}
