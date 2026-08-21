package dev.readana.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Padding a details sheet uses around its content, so every sheet in the app lines up. */
val BookDetailsPadding = 24.dp

private val CoverWidth = 120.dp
private val ValueWidth = 220.dp
private const val TITLE_LINES = 3

/**
 * Cover, title and author, the way a book introduces itself at the top of a details sheet.
 * Shared so a search hit and a book already on the shelf look the same.
 */
@Composable
fun BookDetailsHeader(title: String, subtitle: String, coverUri: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        BookCover(title = title, coverUri = coverUri, modifier = Modifier.width(CoverWidth))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One labelled line of a details sheet: what it is on the left, what it says on the right. */
@Composable
fun BookFact(label: String, value: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ValueWidth)
        )
    }
}
