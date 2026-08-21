package dev.readana.feature.discover

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookCover
import dev.readana.core.designsystem.component.BookGridSkeleton
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.WavyProgress
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import dev.readana.source.trending.TrendingBook

@Composable
internal fun DiscoverRoute(
    onVerifyInBrowser: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val askForNotifications = rememberNotificationPermissionRequest()
    val currentOnVerify by rememberUpdatedState(onVerifyInBrowser)
    val currentOnEffectHandled by rememberUpdatedState(viewModel::consumeEffect)

    LaunchedEffect(uiState.effect) {
        when (val effect = uiState.effect) {
            is DiscoverEffect.Verify -> {
                currentOnVerify(effect.url, effect.sourceName)
                currentOnEffectHandled()
            }

            null -> Unit
        }
    }

    // The browser hands control back here, and a check that passed makes the search worth redoing.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    DiscoverScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onDownload = { result ->
            // Progress is only visible in the queue without it, so a refusal must not block anything.
            askForNotifications()
            viewModel.download(result)
        },
        onSelectTrending = viewModel::searchFor,
        onDismissMessage = viewModel::consumeMessage,
        onVerify = viewModel::verifyInBrowser,
        onDownloadTrending = { book ->
            askForNotifications()
            viewModel.download(book)
        },
        modifier = modifier
    )
}

/** Asks once for [Manifest.permission.POST_NOTIFICATIONS]; a no-op below Android 13. */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * One source's complaint. A site that insists on a browser check gets an action, because opening
 * the page once is all it takes and there is nothing else the user could do about it.
 */
@Composable
private fun SourceErrorRow(sourceError: SourceError, onVerify: (SourceError) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(
            text = stringResource(
                R.string.feature_discover_source_error,
                sourceError.sourceName,
                sourceError.error.message
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        if (sourceError.verifyUrl != null) {
            TextButton(onClick = { onVerify(sourceError) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.feature_discover_verify_in_browser))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoverScreen(
    uiState: DiscoverUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDownload: (SearchResult) -> Unit,
    onSelectTrending: (TrendingBook) -> Unit,
    onDismissMessage: () -> Unit,
    onVerify: (SourceError) -> Unit,
    onDownloadTrending: (TrendingBook) -> Unit,
    modifier: Modifier = Modifier
) {
    // Long-pressing a cover opens what the source knows about it, the way the library does.
    var details by remember { mutableStateOf<SearchResult?>(null) }
    var editions by remember { mutableStateOf<ResultGroup?>(null) }
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
                // Leading, as in the library. It still runs the search, because unlike the library
                // this one costs a network round trip and only happens when asked.
                leadingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            ReadanaIcons.Discover,
                            contentDescription = stringResource(
                                R.string.feature_discover_search_action
                            )
                        )
                    }
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                ReadanaIcons.Close,
                                contentDescription = stringResource(R.string.feature_discover_search_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            // Only while re-searching over results already on screen; a fresh search shows skeletons.
            if (uiState.isSearching && uiState.results.isNotEmpty()) {
                WavyProgress(Modifier.fillMaxWidth())
            }
            if (uiState.isVerifying) {
                Text(
                    text = stringResource(R.string.feature_discover_verifying),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            uiState.sourceErrors.forEach { sourceError ->
                SourceErrorRow(sourceError = sourceError, onVerify = onVerify)
            }
            DiscoverContent(
                uiState = uiState,
                onDownload = onDownload,
                onSelectTrending = onSelectTrending,
                onDownloadTrending = onDownloadTrending,
                onShowDetails = { details = it },
                onShowEditions = { editions = it }
            )
        }
    }

    details?.let { result ->
        ResultDetailsSheet(result = result, onDismiss = { details = null })
    }
    editions?.let { group ->
        EditionsSheet(
            group = group,
            onDownload = onDownload,
            onDismiss = { editions = null }
        )
    }
}

@Composable
private fun DiscoverContent(
    uiState: DiscoverUiState,
    onDownload: (SearchResult) -> Unit,
    onSelectTrending: (TrendingBook) -> Unit,
    onDownloadTrending: (TrendingBook) -> Unit,
    onShowDetails: (SearchResult) -> Unit,
    onShowEditions: (ResultGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val waitingForFirstResults = uiState.isSearching && uiState.results.isEmpty()
    val waitingForShelf = !uiState.hasSearched && uiState.isLoadingTrending
    when {
        // A grid of pulsing placeholders beats an empty screen while covers arrive.
        waitingForFirstResults || waitingForShelf ->
            BookGridSkeleton(modifier = modifier, columns = GRID_COLUMNS)

        !uiState.hasSearched && uiState.trending.isNotEmpty() -> TrendingGrid(
            books = uiState.trending,
            onSelect = onSelectTrending,
            onDownload = onDownloadTrending,
            modifier = modifier
        )

        !uiState.hasSearched -> EmptyState(
            icon = ReadanaIcons.Discover,
            title = stringResource(R.string.feature_discover_empty_title),
            description = stringResource(
                R.string.feature_discover_empty_description,
                uiState.sourceNames.joinToString(", ")
            ),
            modifier = modifier
        )

        uiState.results.isEmpty() -> EmptyState(
            icon = ReadanaIcons.Discover,
            title = stringResource(R.string.feature_discover_no_results_title),
            modifier = modifier
        )

        else -> ResultGrid(
            results = uiState.results,
            onDownload = onDownload,
            onShowDetails = onShowDetails,
            onShowEditions = onShowEditions,
            modifier = modifier
        )
    }
}

/**
 * Opening a book's details: hold it, as in the library. A plain tap does the same, because a cover
 * that answers nothing at all reads as broken.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bookDetailsGesture(onShowDetails: () -> Unit): Modifier {
    val label = stringResource(R.string.feature_discover_details_action)
    return combinedClickable(
        onClickLabel = label,
        onLongClickLabel = label,
        onClick = onShowDetails,
        onLongClick = onShowDetails
    )
}

/** How many other copies of this book the search found; opens the picker. */
@Composable
private fun EditionsBadge(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = BADGE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = pluralStringResource(R.plurals.feature_discover_editions_badge, count, count),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Search hits as a two-up cover grid. */
@Composable
private fun ResultGrid(
    results: List<ResultGroup>,
    onDownload: (SearchResult) -> Unit,
    onShowDetails: (SearchResult) -> Unit,
    onShowEditions: (ResultGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        items(results, key = { it.key }) { group ->
            ResultCard(
                group = group,
                onDownload = { onDownload(group.best) },
                onShowDetails = { onShowDetails(group.best) },
                onShowEditions = { onShowEditions(group) }
            )
        }
    }
}

@Composable
private fun ResultCard(
    group: ResultGroup,
    onDownload: () -> Unit,
    onShowDetails: () -> Unit,
    onShowEditions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = group.best
    Column(modifier = modifier) {
        Box {
            BookCover(
                title = result.title,
                coverUri = result.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .bookDetailsGesture(onShowDetails)
            )
            // Over the cover so a long title never pushes the action out of reach.
            FilledIconButton(
                onClick = onDownload,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
            ) {
                Icon(
                    ReadanaIcons.Download,
                    contentDescription = stringResource(R.string.feature_discover_download_action)
                )
            }
            if (group.hasChoices) {
                EditionsBadge(
                    count = group.editions.size,
                    onClick = onShowEditions,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = result.metaLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** What to browse before searching: covers you can tap to search for. */
@Composable
private fun TrendingGrid(
    books: List<TrendingBook>,
    onSelect: (TrendingBook) -> Unit,
    onDownload: (TrendingBook) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.feature_discover_trending_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(books, key = { it.searchQuery }) { book ->
            Column {
                Box {
                    BookCover(
                        title = book.title,
                        coverUri = book.coverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(book) }
                    )
                    // The same reach as a search hit: fetch it without searching first.
                    FilledIconButton(
                        onClick = { onDownload(book) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    ) {
                        Icon(
                            ReadanaIcons.Download,
                            contentDescription = stringResource(R.string.feature_discover_download_action)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun messageText(message: DiscoverMessage): String = when (message) {
    is DiscoverMessage.Queued -> message.fastDownloadsLeft?.let { left ->
        pluralStringResource(R.plurals.feature_discover_message_queued_quota, left, message.title, left)
    } ?: stringResource(R.string.feature_discover_message_queued, message.title)

    is DiscoverMessage.AlreadyInLibrary -> stringResource(
        R.string.feature_discover_message_in_library,
        message.title
    )

    is DiscoverMessage.NothingFound -> stringResource(
        R.string.feature_discover_message_not_found,
        message.title
    )

    is DiscoverMessage.AlreadyQueued -> stringResource(
        R.string.feature_discover_message_already_queued,
        message.title
    )

    is DiscoverMessage.Failed -> stringResource(
        R.string.feature_discover_message_failed,
        message.error.message
    )
}

private const val BYTES_PER_KIB = 1024.0

/** Two across, as covers are the point of this screen. */
private const val GRID_COLUMNS = 2
private val GridSpacing = 16.dp

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
                sourceNames = listOf("Anna's Archive"),
                results = ResultGrouping.group(
                    listOf(
                        previewResult("0123456789abcdef0123456789abcdef", "Pride and Prejudice", "Jane Austen"),
                        previewResult("aaaabbbbccccddddeeeeffff00001111", "Pride and Prejudice", "Austen, Jane"),
                        previewResult("fedcba9876543210fedcba9876543210", "Emma", "Jane Austen")
                    )
                )
            ),
            onQueryChange = {},
            onSearch = {},
            onDownload = {},
            onSelectTrending = {},
            onDismissMessage = {},
            onVerify = {},
            onDownloadTrending = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun DiscoverTrendingPreview() {
    ReadanaTheme {
        DiscoverScreen(
            uiState = DiscoverUiState(
                sourceNames = listOf("Anna's Archive"),
                trending = listOf(
                    TrendingBook("Atomic Habits", listOf("James Clear"), coverUrl = null),
                    TrendingBook("The Psychology of Money", listOf("Morgan Housel"), coverUrl = null)
                )
            ),
            onQueryChange = {},
            onSearch = {},
            onDownload = {},
            onSelectTrending = {},
            onDismissMessage = {},
            onVerify = {},
            onDownloadTrending = {}
        )
    }
}

private const val BADGE_ALPHA = 0.85f

private fun previewResult(md5: String, title: String, author: String) = SearchResult(
    sourceId = SourceId("annas-archive"),
    recordId = md5,
    title = title,
    authors = listOf(author),
    language = "en",
    format = BookFormat.EPUB,
    sizeBytes = 812_000
)
