package dev.readana.feature.reader

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readana.core.model.HighlightStyle
import dev.readana.core.model.HighlightTint
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Locator

/** Decoration group the reader owns; Readium keeps groups independent of each other. */
private const val HIGHLIGHTS_GROUP = "highlights"

private val TintColors = mapOf(
    HighlightTint.Yellow to Color(0xFFFFF176),
    HighlightTint.Green to Color(0xFFA5D6A7),
    HighlightTint.Blue to Color(0xFF90CAF9),
    HighlightTint.Pink to Color(0xFFF48FB1),
    HighlightTint.Purple to Color(0xFFCE93D8)
)

/** Draws the saved highlights over the page and opens the editor when one is tapped. */
@Composable
internal fun HighlightDecorations(
    navigator: Navigator?,
    highlights: List<HighlightItem>,
    locateHighlight: (Long) -> Locator?,
    onActivate: (Long) -> Unit
) {
    val decorable = navigator as? DecorableNavigator
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentLocateHighlight by rememberUpdatedState(locateHighlight)

    LaunchedEffect(decorable, highlights) {
        decorable ?: return@LaunchedEffect
        val decorations = highlights.mapNotNull { highlight ->
            val locator = currentLocateHighlight(highlight.id) ?: return@mapNotNull null
            Decoration(
                id = highlight.id.toString(),
                locator = locator,
                style = highlight.tint.toDecorationStyle(highlight.style)
            )
        }
        decorable.applyDecorations(decorations, HIGHLIGHTS_GROUP)
    }

    DisposableEffect(decorable) {
        if (decorable == null) {
            onDispose {}
        } else {
            val listener = object : DecorableNavigator.Listener {
                override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                    val id = event.decoration.id.toLongOrNull() ?: return false
                    currentOnActivate(id)
                    return true
                }
            }
            decorable.addDecorationListener(HIGHLIGHTS_GROUP, listener)
            onDispose { decorable.removeDecorationListener(listener) }
        }
    }
}

/** Note, colour and deletion for one highlight. */
@Composable
internal fun HighlightEditorDialog(
    highlight: HighlightItem,
    onSave: (Long, HighlightTint, String?) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var note by rememberSaveable(highlight.id) { mutableStateOf(highlight.note.orEmpty()) }
    var tint by rememberSaveable(highlight.id) { mutableStateOf(highlight.tint) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.feature_reader_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = highlight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HighlightTint.entries.forEach { option ->
                        FilterChip(
                            selected = option == tint,
                            onClick = { tint = option },
                            label = { Text(option.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feature_reader_note_hint)) },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(highlight.id, tint, note) }) {
                Text(stringResource(R.string.feature_reader_note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDelete(highlight.id) }) {
                Text(stringResource(R.string.feature_reader_highlight_delete))
            }
        }
    )
}

private fun HighlightTint.toDecorationStyle(style: HighlightStyle): Decoration.Style {
    val argb = color().toArgb()
    return when (style) {
        HighlightStyle.Highlight -> Decoration.Style.Highlight(tint = argb)
        HighlightStyle.Underline -> Decoration.Style.Underline(tint = argb)
    }
}

private fun HighlightTint.color(): Color = TintColors.getValue(this)
