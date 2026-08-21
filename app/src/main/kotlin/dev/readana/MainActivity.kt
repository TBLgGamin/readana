package dev.readana

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
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

    /** Handles "Open with Readana": the file is imported and shows up in the library. */
    private fun importFrom(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { bookImportManager.enqueue(listOf(it.toString())) }
    }
}
