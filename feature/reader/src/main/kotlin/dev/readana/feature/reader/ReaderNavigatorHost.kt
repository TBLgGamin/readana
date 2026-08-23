package dev.readana.feature.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderSettings
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Locator

/** Share of the viewport width, per side, that turns a page instead of toggling the chrome. */
private const val EDGE_FRACTION = 0.3
private const val MIN_EDGE_PX = 80.0

/**
 * Renders the Readium navigator for the open session and keeps it in step with the app: reading
 * preferences are pushed on every change, the current locator is reported back, and taps in the
 * middle of the page toggle the reader chrome while edge taps turn pages.
 *
 * [sessionId] identifies the open publication; a new value composes a fresh host, which is how the
 * reader recovers after process death.
 */
@Composable
internal fun ReaderNavigatorHost(
    sessionId: Long,
    settings: ReaderSettings,
    onLocatorChange: (Locator) -> Unit,
    onCenterTap: () -> Unit,
    onNavigator: (Navigator?) -> Unit,
    /** Hands out the surface the pen draws on, once the fragment has built it. */
    onSurface: (ReaderSurfaceView?) -> Unit,
    modifier: Modifier = Modifier
) {
    var navigator by remember(sessionId) { mutableStateOf<Navigator?>(null) }
    val currentOnLocatorChanged by rememberUpdatedState(onLocatorChange)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    val currentOnNavigator by rememberUpdatedState(onNavigator)
    val currentOnSurface by rememberUpdatedState(onSurface)
    val systemInDarkTheme = isSystemInDarkTheme()

    key(sessionId) {
        AndroidFragment<ReaderNavigatorHostFragment>(modifier = modifier) { host ->
            host.setOnNavigatorChanged { attached ->
                navigator = attached
                currentOnNavigator(attached)
            }
            currentOnSurface(host.surface())
        }
    }

    LaunchedEffect(navigator, settings, systemInDarkTheme) {
        navigator?.submitReaderPreferences(settings, systemInDarkTheme)
    }

    val scrolling = settings.pageTurnMode == PageTurnMode.Scrolled
    LaunchedEffect(navigator, scrolling) {
        var previous: Locator? = null
        navigator?.currentLocator?.collect { locator ->
            if (shouldSnapToResourceStart(previous, locator, scrolling)) {
                navigator?.go(locator.atResourceStart(), animated = false)
            }
            previous = locator
            currentOnLocatorChanged(locator)
        }
    }

    val turnPagesOnTap = settings.tapToTurnPages
    DisposableEffect(navigator, turnPagesOnTap) {
        val overflowable = navigator as? OverflowableNavigator
        if (overflowable == null) {
            onDispose {}
        } else {
            // Ours runs first, so a tap in the middle toggles the chrome and never turns a page.
            val chrome = ChromeTapListener(overflowable, turnPagesOnTap) { currentOnCenterTap() }
            val paging = DirectionalNavigationAdapter(
                navigator = overflowable,
                tapEdges = if (turnPagesOnTap) {
                    setOf(DirectionalNavigationAdapter.TapEdge.Horizontal)
                } else {
                    emptySet()
                },
                minimumHorizontalEdgeSize = MIN_EDGE_PX,
                horizontalEdgeThresholdPercent = EDGE_FRACTION,
                animatedTransition = true
            )
            overflowable.addInputListener(chrome)
            overflowable.addInputListener(paging)
            onDispose {
                overflowable.removeInputListener(paging)
                overflowable.removeInputListener(chrome)
            }
        }
    }
}

/**
 * Toggles the reader chrome for taps outside the page-turn edges. Returns true for those taps so
 * the page-turn adapter behind it stays out of the way.
 */
private class ChromeTapListener(
    private val navigator: OverflowableNavigator,
    private val turnPagesOnTap: Boolean,
    private val onToggle: () -> Unit
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        if (turnPagesOnTap && !isCentre(event)) return false
        onToggle()
        return true
    }

    private fun isCentre(event: TapEvent): Boolean {
        val width = navigator.publicationView.width.toDouble()
        if (width <= 0) return true
        val edge = maxOf(MIN_EDGE_PX, EDGE_FRACTION * width)
        return event.point.x > edge && event.point.x < width - edge
    }
}

private fun Navigator.submitReaderPreferences(settings: ReaderSettings, systemInDarkTheme: Boolean) {
    when (this) {
        is EpubNavigatorFragment ->
            submitPreferences(settings.toEpubPreferences(systemInDarkTheme))

        is PdfNavigatorFragment<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            (this as PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>)
                .submitPreferences(settings.toPdfiumPreferences())
        }
    }
}
