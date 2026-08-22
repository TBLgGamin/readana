package dev.readana.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.component.BookCover
import dev.readana.core.designsystem.component.SteadyProgress
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.Book
import kotlin.math.roundToInt

private val CoverWidth = 88.dp
private const val PERCENT = 100
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

/**
 * The book you were last reading, at the top of the library.
 *
 * A library sorted by recency already puts this book first, but a grid of covers asks the reader to
 * recognise one — this says outright where they were and how much is left. It is a shortcut, so it
 * is hidden as soon as a search or a filter is on: those are questions, and this is not an answer
 * to them.
 */
@Composable
internal fun ContinueReadingCard(book: Book, timeLeftMillis: Long?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(
                title = book.title,
                coverUri = book.coverUri,
                modifier = Modifier.width(CoverWidth)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.feature_library_continue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorsLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.progress?.let { progress ->
                    SteadyProgress(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    Text(
                        text = progressLine(progress.fraction, timeLeftMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** How far in, and — once the reader's pace is known — how much of the book is still to come. */
@Composable
private fun progressLine(fraction: Float, timeLeftMillis: Long?): String {
    val percent = stringResource(R.string.feature_library_progress, (fraction * PERCENT).roundToInt())
    val minutes = timeLeftMillis?.let { (it / MILLIS_PER_MINUTE).toInt() } ?: return percent
    val left = when {
        minutes < 1 -> stringResource(R.string.feature_library_left_almost_done)

        minutes < MINUTES_PER_HOUR ->
            pluralStringResource(R.plurals.feature_library_left_minutes, minutes, minutes)

        else -> {
            val hours = minutes / MINUTES_PER_HOUR
            pluralStringResource(
                R.plurals.feature_library_left_hours,
                hours,
                hours,
                minutes % MINUTES_PER_HOUR
            )
        }
    }
    return stringResource(R.string.feature_library_continue_line, percent, left)
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun ContinueReadingCardPreview() {
    ReadanaTheme {
        ContinueReadingCard(
            book = previewBook("1", "Pride and Prejudice", "Jane Austen", progress = 0.42f),
            timeLeftMillis = 3L * 60 * 60 * 1000 + 20 * 60 * 1000,
            onOpen = {}
        )
    }
}
