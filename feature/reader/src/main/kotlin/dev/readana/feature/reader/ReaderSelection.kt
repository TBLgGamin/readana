package dev.readana.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Locator

/** A passage the reader picked out, with where it sits in the book. */
internal data class ReaderSelection(
    val locator: Locator,
    val text: String,
    /** The first element visible on the page: where the assistant's context is cut. */
    val anchor: Locator?
)

/**
 * Fills in what the text-selection menu does. The selection lives in the navigator, so every
 * action reads it back before handing it on.
 */
@Composable
internal fun SelectionActions(
    relay: ReaderSelectionRelay,
    navigator: Navigator?,
    assistantEnabled: Boolean,
    onHighlight: (Locator, String) -> Unit,
    onAsk: (ReaderSelection) -> Unit,
    onExplain: (ReaderSelection) -> Unit
) {
    val scope = rememberCoroutineScope()
    val labels = ReaderSelectionRelay.Labels(
        highlight = stringResource(R.string.feature_reader_highlight),
        ask = stringResource(R.string.feature_reader_ai_ask),
        explain = stringResource(R.string.feature_reader_ai_explain)
    )
    val currentOnHighlight by rememberUpdatedState(onHighlight)
    val currentOnAsk by rememberUpdatedState(onAsk)
    val currentOnExplain by rememberUpdatedState(onExplain)

    DisposableEffect(relay, navigator, labels, assistantEnabled) {
        relay.labels = labels
        relay.assistantEnabled = assistantEnabled
        relay.actions = object : ReaderSelectionRelay.Actions {
            override fun highlight() = scope.withSelection(navigator) { selection ->
                currentOnHighlight(selection.locator, selection.text)
            }

            override fun ask() = scope.withSelection(navigator) { currentOnAsk(it) }

            override fun explain() = scope.withSelection(navigator) { currentOnExplain(it) }
        }
        onDispose { relay.actions = null }
    }
}

/**
 * Reads the live selection and clears it, then hands it on. The order matters: the WebView keeps
 * the selection until it is cleared, and clearing it is what dismisses the highlight handles.
 */
private fun CoroutineScope.withSelection(navigator: Navigator?, block: (ReaderSelection) -> Unit) {
    val selectable = navigator as? SelectableNavigator ?: return
    launch {
        val selection = selectable.currentSelection()
        selectable.clearSelection()
        val locator = selection?.locator ?: return@launch
        block(
            ReaderSelection(
                locator = locator,
                text = locator.text.highlight.orEmpty(),
                anchor = (navigator as? VisualNavigator)?.firstVisibleElementLocator()
            )
        )
    }
}
