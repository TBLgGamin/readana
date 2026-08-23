package dev.readana.feature.reader

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * The palm-rejection rule, on its own. `MotionEvent.obtain` needs Android's own implementation,
 * hence Robolectric, but nothing here draws or touches a view.
 */
@RunWith(AndroidJUnit4::class)
class StylusTouchRouterTest {
    private var now = 1_000L
    private val router = StylusTouchRouter(clock = { now }, graceMillis = GRACE_MS)

    @Test
    fun `a finger is delivered when no pen has been near`() {
        assertTrue(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `a finger is dropped while the pen is touching the page`() {
        router.shouldDeliver(pen(MotionEvent.ACTION_DOWN))

        assertFalse(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `a finger is dropped while the pen is only hovering`() {
        // Hover is what gives away that the hand is on its way down, before the nib lands.
        router.shouldDeliver(pen(MotionEvent.ACTION_HOVER_ENTER))

        assertFalse(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `the pen itself is always delivered`() {
        assertTrue(router.shouldDeliver(pen(MotionEvent.ACTION_DOWN)))
        assertTrue(router.shouldDeliver(pen(MotionEvent.ACTION_MOVE)))
        assertTrue(router.shouldDeliver(pen(MotionEvent.ACTION_UP)))
    }

    @Test
    fun `a palm landing just after the pen lifts is still dropped`() {
        router.shouldDeliver(pen(MotionEvent.ACTION_DOWN))
        router.shouldDeliver(pen(MotionEvent.ACTION_UP))
        now += GRACE_MS / 2

        assertFalse(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `a finger works again once the pen has been away long enough`() {
        router.shouldDeliver(pen(MotionEvent.ACTION_DOWN))
        router.shouldDeliver(pen(MotionEvent.ACTION_UP))
        now += GRACE_MS * 2

        assertTrue(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `hover exit ends the pen's hold on the page`() {
        router.shouldDeliver(pen(MotionEvent.ACTION_HOVER_ENTER))
        router.shouldDeliver(pen(MotionEvent.ACTION_HOVER_EXIT))
        now += GRACE_MS * 2

        assertFalse(router.penInUse)
        assertTrue(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `a cancelled pen stroke does not leave the page ignoring fingers forever`() {
        router.shouldDeliver(pen(MotionEvent.ACTION_DOWN))
        router.shouldDeliver(pen(MotionEvent.ACTION_CANCEL))
        now += GRACE_MS * 2

        assertTrue(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun `resetting forgets a stroke left half-finished`() {
        router.shouldDeliver(pen(MotionEvent.ACTION_DOWN))

        router.reset()

        assertFalse(router.penInUse)
        assertTrue(router.shouldDeliver(finger(MotionEvent.ACTION_DOWN)))
    }

    private fun pen(action: Int) = event(action, MotionEvent.TOOL_TYPE_STYLUS)

    private fun finger(action: Int) = event(action, MotionEvent.TOOL_TYPE_FINGER)

    private fun event(action: Int, toolType: Int): MotionEvent {
        val pointer = MotionEvent.PointerProperties().apply {
            id = 0
            this.toolType = toolType
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = 10f
            y = 10f
        }
        return MotionEvent.obtain(
            0L,
            now,
            action,
            1,
            arrayOf(pointer),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0
        )
    }

    private companion object {
        const val GRACE_MS = 300L
    }
}
