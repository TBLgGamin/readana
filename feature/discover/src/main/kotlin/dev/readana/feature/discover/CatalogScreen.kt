package dev.readana.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.source.api.CatalogEntry
import dev.readana.source.api.SearchResult
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun CatalogRoute(
    catalogKey: CatalogKey,
    onBack: () -> Unit,
    onOpenSection: (CatalogKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    LaunchedEffect(catalogKey) { viewModel.open(catalogKey) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CatalogScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenSection = { section ->
            onOpenSection(CatalogKey(catalogKey.sourceId, section.href, section.title))
        },
        onDownload = viewModel::download,
        onLoadMore = viewModel::loadMore,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogScreen(
    uiState: CatalogUiState,
    onBack: () -> Unit,
    onOpenSection: (CatalogEntry.Section) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            ReadanaIcons.Back,
                            contentDescription = stringResource(R.string.feature_discover_catalog_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())

                uiState.error != null -> EmptyState(
                    icon = ReadanaIcons.Failed,
                    title = stringResource(R.string.feature_discover_catalog_failed),
                    description = uiState.error.message
                )

                uiState.entries.isEmpty() -> EmptyState(
                    icon = ReadanaIcons.Discover,
                    title = stringResource(R.string.feature_discover_catalog_empty)
                )

                else -> CatalogGrid(
                    uiState = uiState,
                    onOpenSection = onOpenSection,
                    onDownload = onDownload,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun CatalogGrid(
    uiState: CatalogUiState,
    onOpenSection: (CatalogEntry.Section) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    // Ask for the next page a little before the end, so the list does not visibly stall.
    LaunchedEffect(gridState, uiState.canLoadMore, uiState.entries.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { uiState.canLoadMore && it >= uiState.entries.size - LOAD_AHEAD }
            .collect { currentOnLoadMore() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        items(
            items = uiState.entries,
            key = { it.gridKey() },
            // A shelf reads as a row across the page; a book is one cover-sized cell.
            span = { entry ->
                if (entry is CatalogEntry.Section) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }
        ) { entry ->
            when (entry) {
                is CatalogEntry.Section -> ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = entry.subtitle?.let { subtitle -> { Text(subtitle) } },
                    trailingContent = { Icon(ReadanaIcons.ExpandMore, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSection(entry) }
                )

                is CatalogEntry.Publication -> ResultCard(
                    // A curated shelf lists each entry on purpose, so nothing is collapsed here:
                    // every publication is a group of exactly one.
                    group = ResultGroup(entry.result.recordId, listOf(entry.result)),
                    onDownload = { onDownload(entry.result) },
                    onShowDetails = { onDownload(entry.result) },
                    onShowEditions = {}
                )
            }
        }
        if (uiState.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingState(modifier = Modifier.fillMaxWidth().height(LoadingRowHeight))
            }
        }
    }
}

/** Sections and publications share the list, so their keys must not collide. */
private fun CatalogEntry.gridKey(): String = when (this) {
    is CatalogEntry.Section -> "section:$href"
    is CatalogEntry.Publication -> "book:${result.recordId}"
}

private const val GRID_COLUMNS = 2
private const val LOAD_AHEAD = 4
private val GridSpacing = 12.dp
private val LoadingRowHeight = 72.dp
