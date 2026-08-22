package dev.readana.feature.reader

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
 * Offers a written-out file to whatever the reader wants to send it to, once per export.
 *
 * A chooser rather than a direct send: notes go to a different place every time — a notes app, a
 * mail draft, a chat — and the system remembers nothing the reader did not choose.
 */
@Composable
internal fun ShareFileEffect(uri: String?, title: String, mimeType: String, onConsume: () -> Unit) {
    val context = LocalContext.current
    val currentOnConsume by rememberUpdatedState(onConsume)
    LaunchedEffect(uri) {
        val target = uri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, target.toUri())
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
        currentOnConsume()
    }
}
