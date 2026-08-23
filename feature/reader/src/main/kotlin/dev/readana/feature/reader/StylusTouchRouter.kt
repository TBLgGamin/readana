package dev.readana.feature.reader

import android.view.MotionEvent

/**
 * Decides which touches reach the page while a pen is in use.
 *
 * The reader cannot do this any higher up. Readium's `InputListener` carries only a point —
 * `TapEvent(PointF)` — and for EPUB the events are raised by JavaScript inside the WebView, which
 * binds `touchstart` and never looks at `pointerType`. By the time Readium reports a tap, whether
 * it came from a pen or a palm is gone. So the decision is made here, on the raw `MotionEvent`,
 * before the WebView ever sees it.
 *
 * Kept free of Android views on purpose: the rule is the part worth testing, and this way it can
 * be tested without a device.
 */
internal class StylusTouchRouter(
    private val clock: () -> Long = System::currentTimeMillis,
    private val graceMillis: Long = PALM_GRACE_MS
) {
    private var penDown = false
    private var penHovering = false
    private var lastPenAt = 0L

    /** True while the pen is touching the screen or hovering just above it. */
    val penInUse: Boolean
        get() = penDown || penHovering

    /**
     * Whether [event] should be passed on to the page.
     *
     * A finger is dropped while the pen is in use and for a short while after it lifts, because
     * that is exactly when the hand resting on the screen lands. Nothing is dropped when the pen
     * is away, so reading with fingers alone behaves as it always did.
     */
    fun shouldDeliver(event: MotionEvent): Boolean {
        observe(event)
        if (!event.isFinger()) return true
        if (penInUse) return false
        // The palm usually touches down a moment after the nib lifts, not at the same time.
        return clock() - lastPenAt > graceMillis
    }

    /** Follows the pen so [penInUse] and the grace window stay current. */
    private fun observe(event: MotionEvent) {
        if (!event.isPen()) return
        lastPenAt = clock()
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> penHovering = true

            MotionEvent.ACTION_HOVER_EXIT -> penHovering = false

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> penDown = true

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL ->
                penDown = false

            else -> Unit
        }
    }

    /** Forgets the pen, for when the reader is torn down mid-stroke. */
    fun reset() {
        penDown = false
        penHovering = false
        lastPenAt = 0L
    }

    private companion object {
        /**
         * How long after the pen lifts a finger is still ignored. Long enough to cover the hand
         * settling, short enough that deliberately putting the pen down and tapping still works.
         */
        const val PALM_GRACE_MS = 300L
    }
}

/** The pointer that raised this event, which is what a palm and a nib differ by. */
internal fun MotionEvent.isPen(): Boolean {
    val tool = getToolType(actionIndex)
    return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
}

internal fun MotionEvent.isFinger(): Boolean = getToolType(actionIndex) == MotionEvent.TOOL_TYPE_FINGER
