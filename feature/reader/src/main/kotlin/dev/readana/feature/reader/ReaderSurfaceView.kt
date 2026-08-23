package dev.readana.feature.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * The publication's container, and the last place a touch can still be told apart by what made it.
 *
 * Two jobs, both of which have to happen before the WebView sees the event:
 *
 * - **Palm rejection**: while the pen is in use, fingers are dropped, so a hand resting on the page
 *   does not turn it or start a selection.
 * - **Pen strokes**: a drag made with the pen is reported rather than delivered, so it can become a
 *   highlight or a line of ink instead of a text selection.
 */
internal class ReaderSurfaceView(context: Context) : FrameLayout(context) {
    private val router = StylusTouchRouter()

    /** What the pen is for right now; when null, the pen behaves like a finger. */
    var penMode: PenGestureMode = PenGestureMode.Off

    /** Called with the start and end of a completed pen drag, in this view's coordinates. */
    var onPenStroke: ((start: PointF, end: PointF, points: List<PointF>) -> Unit)? = null

    /** Called as a pen drag is being made, so it can be drawn while it happens. */
    var onPenStrokeProgress: ((points: List<PointF>) -> Unit)? = null

    var palmRejectionEnabled: Boolean = true

    private var stroke: MutableList<PointF>? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // A pen drag is ours to draw; everything else belongs to the page underneath.
        if (penMode != PenGestureMode.Off && event.isPen()) {
            return event.actionMasked == MotionEvent.ACTION_MOVE && stroke != null
        }
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (penMode != PenGestureMode.Off && event.isPen()) {
            trackStroke(event)
            return true
        }
        if (palmRejectionEnabled && !router.shouldDeliver(event)) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    /** Hover carries no touch, but it is what tells the router the pen has arrived. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (palmRejectionEnabled) router.shouldDeliver(event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDetachedFromWindow() {
        router.reset()
        stroke = null
        super.onDetachedFromWindow()
    }

    private fun trackStroke(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> stroke = mutableListOf(event.point())

            MotionEvent.ACTION_MOVE -> stroke?.let { points ->
                points += event.point()
                onPenStrokeProgress?.invoke(points.toList())
            }

            MotionEvent.ACTION_UP -> {
                val points = stroke?.plus(event.point()).orEmpty()
                stroke = null
                onPenStrokeProgress?.invoke(emptyList())
                // A tap is not a stroke: without a minimum the pen would highlight a single word
                // every time it touched the page.
                if (points.size >= MIN_STROKE_POINTS) {
                    onPenStroke?.invoke(points.first(), points.last(), points)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                stroke = null
                onPenStrokeProgress?.invoke(emptyList())
            }

            else -> Unit
        }
    }

    private fun MotionEvent.point() = PointF(x, y)

    private companion object {
        const val MIN_STROKE_POINTS = 3
    }
}

/** What the pen does when it touches the page. */
internal enum class PenGestureMode {
    /** The pen behaves like a finger: it taps, turns pages and selects text. */
    Off,

    /** A pen drag across text highlights it. */
    Highlight,

    /** A pen drag leaves a line on the page. */
    Ink
}
