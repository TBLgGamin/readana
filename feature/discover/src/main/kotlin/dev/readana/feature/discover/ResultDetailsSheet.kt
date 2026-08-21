package dev.readana.feature.discover

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.component.BookDetailsHeader
import dev.readana.core.designsystem.component.BookDetailsPadding
import dev.readana.core.designsystem.component.BookFact
import dev.readana.source.api.SearchResult
import java.util.Locale

/**
 * Everything a search knows about a book, in the same sheet the library uses for one it already
 * has. No actions: the cover it was opened from carries the download button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultDetailsSheet(result: SearchResult, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.feature_discover_details_unknown)

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BookDetailsPadding)
                .padding(bottom = BookDetailsPadding)
        ) {
            BookDetailsHeader(
                title = result.title,
                subtitle = result.authors.joinToString(", ").ifEmpty { unknown },
                coverUri = result.coverUrl
            )
            Spacer(Modifier.height(BookDetailsPadding))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BookFact(
                    label = stringResource(R.string.feature_discover_details_format),
                    value = result.format?.extension?.uppercase() ?: unknown
                )
                BookFact(
                    label = stringResource(R.string.feature_discover_details_size),
                    value = result.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: unknown
                )
                BookFact(
                    label = stringResource(R.string.feature_discover_details_language),
                    value = result.language?.let { languageName(it) } ?: unknown
                )
                BookFact(
                    label = stringResource(R.string.feature_discover_details_year),
                    value = result.year?.toString() ?: unknown
                )
                BookFact(
                    label = stringResource(R.string.feature_discover_details_publisher),
                    value = result.publisher ?: unknown,
                    maxLines = 2
                )
                BookFact(
                    label = stringResource(R.string.feature_discover_details_source),
                    value = result.sourceId.value
                )
            }
            result.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(BookDetailsPadding))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "en" says little on a details sheet; the reader's own language name says plenty. */
internal fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifEmpty { code }
