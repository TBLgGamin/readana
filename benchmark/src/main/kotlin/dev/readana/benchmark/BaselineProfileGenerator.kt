package dev.readana.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths a cold start walks, so ART can compile them ahead of time instead of
 * interpreting them on the way to the first frame.
 *
 * Run it with the tablet attached:
 *
 * ```
 * ./gradlew :app:generateBaselineProfile
 * ```
 *
 * and commit what lands in `app/src/release/generated/baselineProfiles/`. CI has no device, so the
 * profile is a committed artifact rather than something the release build produces (docs/RELEASING.md).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndLibrary() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()

        // The library is the start destination and the screen a cold start actually lands on.
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }

        // Discover and Settings are the other two the navigation bar reaches directly, and both
        // pull in Compose machinery the library alone never touches.
        listOf(DISCOVER_LABEL, SETTINGS_LABEL).forEach { label ->
            device.findObject(By.text(label))?.click()
            device.wait(Until.hasObject(By.text(label)), UI_TIMEOUT_MS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "dev.readana"
        const val DISCOVER_LABEL = "Discover"
        const val SETTINGS_LABEL = "Settings"
        const val UI_TIMEOUT_MS = 5_000L

        /** Keeps a fling clear of the system gesture insets at the screen edges. */
        const val GESTURE_MARGIN_FRACTION = 5
    }
}
