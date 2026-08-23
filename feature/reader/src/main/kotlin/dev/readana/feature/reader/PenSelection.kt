package dev.readana.feature.reader

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Turns a pen drag into a text selection the rest of the reader already understands.
 *
 * Rather than work out which words a stroke crossed — which would mean reimplementing text
 * hit-testing outside the WebView that owns the layout — the stroke is handed to the page itself.
 * `caretRangeFromPoint` resolves each end to a position in the DOM, the two are joined into a
 * range, and that range becomes the document's own selection.
 *
 * From there nothing is new: `SelectableNavigator.currentSelection()` reads the same DOM selection
 * it reads after a long press, and returns a proper `Locator`. The existing highlight path — Room,
 * decorations, the notes list, the Markdown export — carries on unchanged.
 *
 * Readium 3.3 has no hook for injecting a script (its `Configuration` offers `servedAssets`,
 * `javascriptInterfaces` and `decorationTemplates`, none of which run code on demand), so the
 * WebView is found in the navigator's own view tree and asked directly.
 */
internal object PenSelection {
    /**
     * Selects the text between [start] and [end], given in [root]'s coordinates.
     *
     * Returns false when there was nothing selectable under the stroke — an image, a margin, a gap
     * between paragraphs — so the caller can leave the page alone rather than clearing a selection
     * that was never made.
     */
    suspend fun selectBetween(root: View, start: PointF, end: PointF): Boolean {
        val webView = root.findWebViewAt(start) ?: return false
        val from = root.toWebViewPoint(webView, start)
        val to = root.toWebViewPoint(webView, end)
        val result = webView.evaluate(selectionScript(from, to))
        return result.trim().trim('"') == "true"
    }

    /**
     * `collapse` guards against the two ends resolving to the same spot, which would leave an empty
     * selection that reads as a stray caret. Wrapped in a try so a page that forbids selection, or
     * a cross-document range, fails quietly rather than throwing inside the page.
     */
    private fun selectionScript(from: PointF, to: PointF): String = """
        (function () {
          try {
            var a = document.caretRangeFromPoint(${from.x}, ${from.y});
            var b = document.caretRangeFromPoint(${to.x}, ${to.y});
            if (!a || !b) return false;
            var range = document.createRange();
            range.setStart(a.startContainer, a.startOffset);
            range.setEnd(b.startContainer, b.startOffset);
            if (range.collapsed) {
              range.setStart(b.startContainer, b.startOffset);
              range.setEnd(a.startContainer, a.startOffset);
            }
            if (range.collapsed) return false;
            var selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
            return true;
          } catch (e) {
            return false;
          }
        })();
    """.trimIndent()

    /**
     * The WebView under [point], in a two-page spread as much as in a single page: Readium lays
     * each resource out in its own WebView, so the one that was drawn on is the one that must be
     * asked.
     */
    private fun View.findWebViewAt(point: PointF): WebView? {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val screenX = location[0] + point.x
        val screenY = location[1] + point.y
        return webViews().firstOrNull { candidate ->
            val spot = IntArray(2)
            candidate.getLocationOnScreen(spot)
            screenX >= spot[0] && screenX <= spot[0] + candidate.width &&
                screenY >= spot[1] && screenY <= spot[1] + candidate.height
        }
    }

    private fun View.webViews(): List<WebView> = when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
        else -> emptyList()
    }

    /** The page scrolls and sits inset, so a point on the surface is not a point in the document. */
    private fun View.toWebViewPoint(webView: WebView, point: PointF): PointF {
        val here = IntArray(2)
        val there = IntArray(2)
        getLocationOnScreen(here)
        webView.getLocationOnScreen(there)
        val density = webView.resources.displayMetrics.density
        // caretRangeFromPoint works in CSS pixels, relative to the viewport.
        return PointF(
            (here[0] + point.x - there[0]) / density,
            (here[1] + point.y - there[1]) / density
        )
    }

    private suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { value -> continuation.resume(value.orEmpty()) }
    }
}
