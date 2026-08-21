package dev.readana

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.readana.core.common.input.VolumeKey
import dev.readana.core.common.input.VolumeKeyRelay
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.ui.ReadanaApp
import javax.inject.Inject

/**
 * Single-activity host. Extends [FragmentActivity] (not just ComponentActivity) because Readium's
 * EPUB navigator is fragment-based; everything else is Compose.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var bookImportManager: BookImportManager

    @Inject
    lateinit var volumeKeyRelay: VolumeKeyRelay

    private var volumeDownConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: a recreated activity would import the same file again.
        if (savedInstanceState == null) {
            importFrom(intent)
        }
        setContent {
            ReadanaTheme {
                ReadanaApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFrom(intent)
    }

    /** Offers volume presses to whatever is reading before the system changes the volume. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = keyCode.asVolumeKey()
        if (key != null && volumeKeyRelay.dispatch(key)) {
            volumeDownConsumed = true
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Swallows the release of a press the reader took, so the system volume stays put. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode.asVolumeKey() != null && volumeDownConsumed) {
            volumeDownConsumed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.asVolumeKey(): VolumeKey? = when (this) {
        KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.Up
        KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.Down
        else -> null
    }

    /** Handles "Open with Readana": the file is imported and shows up in the library. */
    private fun importFrom(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { bookImportManager.enqueue(listOf(it.toString())) }
    }
}
