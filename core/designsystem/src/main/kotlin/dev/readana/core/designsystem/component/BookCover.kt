package dev.readana.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.theme.ReadanaTheme

private const val COVER_ASPECT_RATIO = 2f / 3f
private const val MAX_TITLE_LINES = 4

/**
 * A book's cover: the extracted image when the import found one, and a tinted title card
 * otherwise. The placeholder also stands in while the image loads or if the file has gone.
 */
@Composable
fun BookCover(title: String, coverUri: String?, modifier: Modifier = Modifier) {
    if (coverUri == null) {
        BookCoverPlaceholder(title = title, modifier = modifier)
        return
    }
    // AsyncImage rather than its Subcompose sibling: subcomposing every cell of a scrolling grid
    // costs a measurable amount of frame time, and the only thing it bought here was a composable
    // error slot, which a flag and a Box give for free.
    var failed by remember(coverUri) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (failed) {
            BookCoverPlaceholder(title = title, modifier = Modifier.matchParentSize())
        } else {
            AsyncImage(
                model = coverUri,
                // The title is always rendered next to the cover, so announcing it twice adds nothing.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/**
 * Placeholder cover showing the title on a tinted card, used for books whose file carries no
 * cover image.
 */
@Composable
fun BookCoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = MAX_TITLE_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A cover at thumbnail size, for lists that are about the book rather than about browsing covers.
 *
 * The full-size placeholder sets the title across the card, which at forty device-independent
 * pixels wide is four unreadable lines, so this one falls back to a book mark instead.
 */
@Composable
fun BookCoverThumb(title: String, coverUri: String?, modifier: Modifier = Modifier) {
    if (coverUri == null) {
        Box(
            modifier = modifier
                .aspectRatio(COVER_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ReadanaIcons.Library,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(6.dp)
            )
        }
        return
    }
    var failed by remember(coverUri) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (failed) {
            BookCoverThumb(title = title, coverUri = null, modifier = Modifier.matchParentSize())
        } else {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Preview
@Composable
private fun BookCoverPlaceholderPreview() {
    ReadanaTheme {
        BookCoverPlaceholder(title = "Pride and Prejudice", modifier = Modifier.padding(8.dp))
    }
}
