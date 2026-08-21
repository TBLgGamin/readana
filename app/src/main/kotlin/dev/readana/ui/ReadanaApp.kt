package dev.readana.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.readana.feature.browser.BrowserKey
import dev.readana.feature.browser.browserEntry
import dev.readana.feature.discover.discoverEntry
import dev.readana.feature.library.LibraryKey
import dev.readana.feature.library.libraryEntry
import dev.readana.feature.reader.ReaderKey
import dev.readana.feature.reader.readerEntry
import dev.readana.feature.settings.settingsEntry
import dev.readana.navigation.TopLevelDestination
import dev.readana.navigation.navigateToTopLevel
import dev.readana.navigation.readanaSavedStateConfiguration

/**
 * Root composable: adaptive navigation (bar on narrow windows, rail on the tablet) around a
 * Navigation 3 [NavDisplay]. The reader hides the navigation chrome for distraction-free reading.
 */
@Composable
fun ReadanaApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(readanaSavedStateConfiguration, LibraryKey)
    val currentKey = backStack.lastOrNull()
    val currentTopLevel = TopLevelDestination.of(currentKey)
    val layoutType = if (currentTopLevel == null) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination == currentTopLevel,
                    onClick = { backStack.navigateToTopLevel(destination.key) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) }
                )
            }
        },
        modifier = modifier,
        layoutType = layoutType
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            ),
            entryProvider = entryProvider {
                libraryEntry(
                    onOpenBook = { bookId -> backStack.add(ReaderKey(bookId)) },
                    onOpenBrowser = { backStack.add(BrowserKey.of(it)) }
                )
                discoverEntry(onOpenBrowser = { backStack.add(BrowserKey.of(it)) })
                settingsEntry()
                readerEntry(onBack = { backStack.removeLastOrNull() })
                browserEntry(onClose = { backStack.removeLastOrNull() })
            }
        )
    }
}
