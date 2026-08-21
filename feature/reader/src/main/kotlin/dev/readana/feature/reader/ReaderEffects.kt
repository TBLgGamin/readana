package dev.readana.feature.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val BrightnessZoneWidth = 48.dp

/** How much of the screen height a full-height drag covers, in brightness. */
private const val BRIGHTNESS_DRAG_RANGE = 400f
private const val MIN_BRIGHTNESS = 0.01f

/** Keeps the screen awake while reading, and lets the system take over again afterwards. */
@Composable
internal fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = (view.context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * Hides the status and navigation bars while the reader chrome is hidden. The bars stay reachable
 * with a swipe, which is also how HyperOS expects immersive apps to behave.
 */
@Composable
internal fun ImmersiveModeEffect(immersive: Boolean) {
    val view = LocalView.current
    DisposableEffect(immersive, view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * Vertical drags along the left edge dim or brighten the screen, the way most readers do. The zone
 * is narrow so page taps and text selection elsewhere are untouched.
 */
@Composable
internal fun BrightnessGestureZone(modifier: Modifier = Modifier) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    var brightness by remember {
        mutableFloatStateOf(
            window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: DEFAULT_BRIGHTNESS
        )
    }
    Box(
        modifier = modifier
            .width(BrightnessZoneWidth)
            .fillMaxHeight()
            .pointerInput(window) {
                detectVerticalDragGestures { _, dragAmount ->
                    val next = (brightness - dragAmount / BRIGHTNESS_DRAG_RANGE)
                        .coerceIn(MIN_BRIGHTNESS, 1f)
                    brightness = next
                    window?.attributes = window?.attributes?.apply { screenBrightness = next }
                }
            }
    )
}

private const val DEFAULT_BRIGHTNESS = 0.5f
