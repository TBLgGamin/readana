package dev.readana.feature.discover

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookDetailsPadding
import dev.readana.source.api.SearchResult

/**
 * Every copy of one book a search found, so a reader can take the language or format they want
 * rather than whichever copy happened to sort first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditionsSheet(
    group: ResultGroup,
    onDownload: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = BookDetailsPadding)) {
            Column(modifier = Modifier.padding(horizontal = BookDetailsPadding)) {
                Text(
                    text = group.best.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_discover_editions_subtitle,
                        group.editions.size,
                        group.editions.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(group.editions, key = { "${it.sourceId}:${it.recordId}" }) { edition ->
                    EditionRow(
                        edition = edition,
                        onClick = {
                            onDownload(edition)
                            onDismiss()
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EditionRow(edition: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.feature_discover_details_unknown)
    val facts = listOfNotNull(
        edition.format?.extension?.uppercase(),
        edition.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
        edition.year?.toString(),
        edition.publisher
    ).joinToString(SEPARATOR)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = BookDetailsPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = edition.language?.let { languageName(it) } ?: unknown,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = facts.ifEmpty { unknown },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            ReadanaIcons.Download,
            contentDescription = stringResource(R.string.feature_discover_download_action)
        )
    }
}

private const val SEPARATOR = " · "
