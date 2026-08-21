package dev.readana.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState

@Composable
internal fun ReaderRoute(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.open(bookId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(uiState = uiState, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(uiState: ReaderUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title =
        (uiState as? ReaderUiState.Ready)?.title ?: stringResource(R.string.feature_reader_title)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            ReadanaIcons.Back,
                            contentDescription = stringResource(R.string.feature_reader_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            ReaderUiState.Loading -> LoadingState(Modifier.padding(padding))

            ReaderUiState.NotFound -> EmptyState(
                icon = ReadanaIcons.Library,
                title = stringResource(R.string.feature_reader_not_found),
                modifier = Modifier.padding(padding)
            )

            is ReaderUiState.Failed -> EmptyState(
                icon = ReadanaIcons.Library,
                title = stringResource(R.string.feature_reader_open_failed),
                description = uiState.error.message,
                modifier = Modifier.padding(padding)
            )

            is ReaderUiState.Ready -> PublicationSummary(uiState, Modifier.padding(padding))
        }
    }
}

/** Stand-in for the Readium navigator: proves the publication opens and shows its outline. */
@Composable
private fun PublicationSummary(state: ReaderUiState.Ready, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.feature_reader_navigator_pending),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            text = stringResource(R.string.feature_reader_contents),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.tableOfContents) { entry ->
                ListItem(headlineContent = { Text(entry) })
            }
        }
    }
}
