package dev.readana.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.R
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.HighlightTint
import kotlin.math.roundToInt

private val MarkWidth = 4.dp
private val MarkHeight = 36.dp
private const val PERCENT = 100

/**
 * One marked passage, with its note if it has one.
 *
 * Shared rather than owned by the reader because the same row appears in the library's book
 * details, and a feature module may not import another feature module.
 */
@Composable
fun NoteRow(
    text: String,
    tint: HighlightTint,
    modifier: Modifier = Modifier,
    note: String? = null,
    progression: Double? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        overlineContent = progression?.let {
            {
                Text(
                    text = stringResource(
                        R.string.core_designsystem_note_progress,
                        (it * PERCENT).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        supportingContent = note?.takeIf { it.isNotBlank() }?.let {
            { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        },
        // A stripe in the margin, the way a highlighter leaves one, rather than a coloured chip.
        leadingContent = {
            Box(
                Modifier
                    .width(MarkWidth)
                    .height(MarkHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(tint.color())
            )
        },
        trailingContent = trailing
    ) {
        Text(text = text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun NoteRowPreview() {
    ReadanaTheme {
        NoteRow(
            text = "It is a truth universally acknowledged, that a single man in possession of a " +
                "good fortune, must be in want of a wife.",
            tint = HighlightTint.Yellow,
            note = "The whole book in one sentence.",
            progression = 0.02
        )
    }
}
