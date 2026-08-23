package dev.readana.feature.reader

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.readana.core.model.PenMode
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator

/**
 * Points the pen at whichever of the two things it is meant to do, and keeps palm rejection in
 * step with the setting.
 *
 * A highlight made with the pen goes through exactly the path a long press does: the stroke sets
 * the document's own selection ([PenSelection]), Readium reads it back as a `Locator`, and the
 * ordinary [ReaderViewModel.addHighlight] stores it. Nothing about highlights is duplicated here.
 */
@Composable
internal fun PenGestures(
    surface: ReaderSurfaceView?,
    navigator: Navigator?,
    penMode: PenMode,
    palmRejection: Boolean,
    onHighlight: (locator: org.readium.r2.shared.publication.Locator, text: String) -> Unit,
    onInkStroke: (points: List<PointF>, surfaceWidth: Int, surfaceHeight: Int) -> Unit,
    onInkInProgress: (List<PointF>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentOnHighlight by rememberUpdatedState(onHighlight)
    val currentOnInkStroke by rememberUpdatedState(onInkStroke)
    val currentOnInkInProgress by rememberUpdatedState(onInkInProgress)

    DisposableEffect(surface, navigator, penMode, palmRejection) {
        if (surface == null) return@DisposableEffect onDispose {}

        surface.palmRejectionEnabled = palmRejection
        surface.penMode = when (penMode) {
            PenMode.Off -> PenGestureMode.Off
            PenMode.Highlight -> PenGestureMode.Highlight
            PenMode.Ink -> PenGestureMode.Ink
        }

        surface.onPenStrokeProgress = { points ->
            if (penMode == PenMode.Ink) currentOnInkInProgress(points)
        }

        surface.onPenStroke = { start, end, points ->
            when (penMode) {
                PenMode.Ink -> currentOnInkStroke(points, surface.width, surface.height)

                PenMode.Highlight -> scope.launch {
                    val visual = navigator as? VisualNavigator ?: return@launch
                    val selectable = navigator as? SelectableNavigator ?: return@launch
                    // Nothing selectable under the stroke — a margin, an image, a gap between
                    // paragraphs — so the page is left exactly as it was.
                    if (!PenSelection.selectBetween(visual.publicationView, start, end)) return@launch
                    val selection = selectable.currentSelection()
                    selectable.clearSelection()
                    val locator = selection?.locator ?: return@launch
                    currentOnHighlight(locator, locator.text.highlight.orEmpty())
                }

                PenMode.Off -> Unit
            }
        }

        onDispose {
            surface.onPenStroke = null
            surface.onPenStrokeProgress = null
            surface.penMode = PenGestureMode.Off
        }
    }
}
