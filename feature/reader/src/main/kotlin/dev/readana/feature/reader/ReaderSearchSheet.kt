package dev.readana.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.WavyProgress

/** Full-text search within the open book, one page of hits at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSearchSheet(
    state: SearchState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The draft is local so typing does not re-run the search on every keystroke.
    var draft by rememberSaveable { mutableStateOf(state.query) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.feature_reader_search_hint)) },
            trailingIcon = {
                IconButton(onClick = { onSearch(draft) }) {
                    Icon(
                        ReadanaIcons.Discover,
                        contentDescription = stringResource(R.string.feature_reader_search)
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(draft) })
        )
        if (state.isSearching) {
            WavyProgress(Modifier.fillMaxWidth())
        }
        when {
            !state.hasSearched -> Unit

            state.hits.isEmpty() && !state.isSearching -> EmptyState(
                icon = ReadanaIcons.Discover,
                title = stringResource(R.string.feature_reader_search_no_results)
            )

            else -> LazyColumn {
                items(state.hits, key = { it.index }) { hit ->
                    SearchHitRow(hit = hit, onSelect = onSelect)
                }
                if (state.hasMore) {
                    item {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text(stringResource(R.string.feature_reader_search_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onSelect: (SearchHit) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable { onSelect(hit) },
        overlineContent = hit.chapter?.let { chapter -> { Text(chapter) } },
        content = {
            Column {
                Text(
                    text = buildAnnotatedString {
                        append(hit.before)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(hit.highlight) }
                        append(hit.after)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }
        }
    )
}
