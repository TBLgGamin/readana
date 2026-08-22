package dev.readana.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.readana.core.data.messages.UiMessage
import dev.readana.feature.browser.BrowserKey
import dev.readana.feature.browser.BrowserVerifyKey
import dev.readana.feature.browser.browserEntry
import dev.readana.feature.browser.browserVerifyEntry
import dev.readana.feature.discover.discoverEntry
import dev.readana.feature.downloads.downloadsEntry
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
fun ReadanaApp(modifier: Modifier = Modifier, viewModel: AppShellViewModel = hiltViewModel()) {
    val backStack = rememberNavBackStack(readanaSavedStateConfiguration, LibraryKey)
    val currentKey = backStack.lastOrNull()
    val currentTopLevel = TopLevelDestination.of(currentKey)
    val pendingDownloads by viewModel.pendingDownloads.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    // Collected here rather than in any one screen: a message outlives whatever raised it, so a
    // download queued from the browser is still announced after the browser has closed itself.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(resources.resolve(message))
        }
    }
    val layoutType = if (currentTopLevel == null) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val badgeCount = destination.badgeCount(pendingDownloads)
                item(
                    selected = destination == currentTopLevel,
                    onClick = { backStack.navigateToTopLevel(destination.key) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                    badge = badgeCount?.let { count -> { Badge { Text(count.toString()) } } }
                )
            }
        },
        modifier = modifier,
        layoutType = layoutType
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator<NavKey>()
                ),
                entryProvider = entryProvider {
                    libraryEntry(onOpenBook = { bookId -> backStack.add(ReaderKey(bookId)) })
                    downloadsEntry(
                        onOpenBook = { bookId -> backStack.add(ReaderKey(bookId)) },
                        onOpenBrowser = { backStack.add(BrowserKey.of(it)) }
                    )
                    discoverEntry(
                        onVerifyInBrowser = { url, title -> backStack.add(BrowserVerifyKey(url, title)) }
                    )
                    settingsEntry()
                    readerEntry(onBack = { backStack.removeLastOrNull() })
                    browserEntry(onClose = { backStack.removeLastOrNull() })
                    browserVerifyEntry(onDone = { backStack.removeLastOrNull() })
                }
            )
            // Inside the scaffold's content, so it floats above the page but never under the
            // navigation bar or behind the rail.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.ime)
            )
        }
    }
}

/** Words a message in the reader's own language, at the moment it is finally shown. */
private fun Resources.resolve(message: UiMessage): String = when (message) {
    is UiMessage.Text -> getString(message.id, *message.args.toTypedArray())

    is UiMessage.Quantity ->
        getQuantityString(message.id, message.count, *message.args.toTypedArray())
}

/** Only the queue carries a number, and only when there is something in it. */
private fun TopLevelDestination.badgeCount(pendingDownloads: Int): Int? =
    pendingDownloads.takeIf { this == TopLevelDestination.Downloads && it > 0 }
