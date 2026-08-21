package dev.readana.feature.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.BookFormat
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId

@Composable
internal fun DiscoverRoute(modifier: Modifier = Modifier, viewModel: DiscoverViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onDownload = viewModel::download,
        onDismissMessage = viewModel::consumeMessage,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoverScreen(
    uiState: DiscoverUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDownload: (SearchResult) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = uiState.message?.let { messageText(it) }
    val currentOnMessageShown by rememberUpdatedState(onDismissMessage)
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            currentOnMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_discover_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.feature_discover_search_hint)) },
                trailingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            ReadanaIcons.Discover,
                            contentDescription = stringResource(
                                R.string.feature_discover_search_action
                            )
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            if (uiState.isSearching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            uiState.sourceErrors.forEach { sourceError ->
                Text(
                    text = stringResource(
                        R.string.feature_discover_source_error,
                        sourceError.sourceName,
                        sourceError.error.message
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            when {
                !uiState.hasSearched -> EmptyState(
                    icon = ReadanaIcons.Discover,
                    title = stringResource(R.string.feature_discover_empty_title),
                    description = stringResource(
                        R.string.feature_discover_empty_description,
                        uiState.sourceNames.joinToString(", ")
                    )
                )

                !uiState.isSearching && uiState.results.isEmpty() -> EmptyState(
                    icon = ReadanaIcons.Discover,
                    title = stringResource(R.string.feature_discover_no_results_title)
                )

                else -> ResultList(results = uiState.results, onDownload = onDownload)
            }
        }
    }
}

@Composable
private fun ResultList(results: List<SearchResult>, onDownload: (SearchResult) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(results, key = { "${it.sourceId}:${it.recordId}" }) { result ->
            ListItem(
                headlineContent = {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = { Text(result.metaLine()) },
                overlineContent = { Text(result.sourceId.value) },
                trailingContent = {
                    IconButton(onClick = { onDownload(result) }) {
                        Icon(
                            ReadanaIcons.Download,
                            contentDescription = stringResource(
                                R.string.feature_discover_download_action
                            )
                        )
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun messageText(message: DiscoverMessage): String = when (message) {
    is DiscoverMessage.ReadyToDownload -> stringResource(
        R.string.feature_discover_message_ready,
        message.url
    )

    is DiscoverMessage.OpenInBrowser -> stringResource(
        R.string.feature_discover_message_browser,
        message.url
    )

    is DiscoverMessage.Failed -> stringResource(
        R.string.feature_discover_message_failed,
        message.error.message
    )
}

private const val BYTES_PER_KIB = 1024.0

private fun SearchResult.metaLine(): String = listOfNotNull(
    authors.takeIf { it.isNotEmpty() }?.joinToString(", "),
    year?.toString(),
    language,
    format?.extension?.uppercase(),
    sizeBytes?.let(::humanSize)
).joinToString(" · ")

private fun humanSize(bytes: Long): String {
    val kib = bytes / BYTES_PER_KIB
    val mib = kib / BYTES_PER_KIB
    return if (mib >= 1) "%.1f MB".format(mib) else "%.0f KB".format(kib)
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun DiscoverScreenPreview() {
    ReadanaTheme {
        DiscoverScreen(
            uiState = DiscoverUiState(
                query = "austen",
                hasSearched = true,
                sourceNames = listOf("Project Gutenberg"),
                results = listOf(
                    SearchResult(
                        sourceId = SourceId("gutenberg"),
                        recordId = "1342",
                        title = "Pride and Prejudice",
                        authors = listOf("Jane Austen"),
                        language = "en",
                        format = BookFormat.EPUB,
                        sizeBytes = 812_000
                    )
                )
            ),
            onQueryChange = {},
            onSearch = {},
            onDownload = {},
            onDismissMessage = {}
        )
    }
}
