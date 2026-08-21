package dev.readana.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
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
    SubcomposeAsyncImage(
        model = coverUri,
        // The title is always rendered next to the cover, so announcing it twice adds nothing.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        loading = {},
        error = { BookCoverPlaceholder(title = title) }
    )
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

@Preview
@Composable
private fun BookCoverPlaceholderPreview() {
    ReadanaTheme {
        BookCoverPlaceholder(title = "Pride and Prejudice", modifier = Modifier.padding(8.dp))
    }
}
