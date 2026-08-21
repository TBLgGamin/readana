package dev.readana

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.ui.ReadanaApp

/**
 * Single-activity host. Extends [FragmentActivity] (not just ComponentActivity) because Readium's
 * EPUB navigator is fragment-based; everything else is Compose.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ReadanaTheme {
                ReadanaApp()
            }
        }
    }
}
