package dev.readana.feature.library

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** Notes are exported as Markdown; every notes app and mail client understands it. */
private const val NOTES_MIME_TYPE = "text/markdown"

/**
 * Offers a written-out notes file to whatever the reader wants to send it to, once per export.
 *
 * A chooser rather than a direct send: notes go somewhere different every time — a notes app, a
 * mail draft, a chat — and the system remembers nothing the reader did not choose.
 */
@Composable
internal fun ShareNotesEffect(uri: String?, title: String, onConsume: () -> Unit) {
    val context = LocalContext.current
    val currentOnConsume by rememberUpdatedState(onConsume)
    LaunchedEffect(uri) {
        val target = uri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = NOTES_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, target.toUri())
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
        currentOnConsume()
    }
}
