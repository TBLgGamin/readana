package dev.readana.feature.downloads

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import dev.readana.engine.pdf2epub.ConversionPhase
import java.time.Instant

private val ScreenPadding = 16.dp

@Composable
internal fun DownloadsRoute(
    onOpenBook: (bookId: String) -> Unit,
    onOpenBrowser: (BrowserDownload) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenBrowser by rememberUpdatedState(onOpenBrowser)
    val currentOnEffectHandled by rememberUpdatedState(viewModel::consumeEffect)
    LaunchedEffect(uiState.effect) {
        when (val effect = uiState.effect) {
            is DownloadsEffect.OpenBrowser -> {
                currentOnOpenBrowser(effect.download)
                currentOnEffectHandled()
            }

            null -> Unit
        }
    }

    DownloadsScreen(
        uiState = uiState,
        actions = DownloadActions(
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onCancel = viewModel::cancel,
            onRetry = viewModel::retry,
            onDismiss = viewModel::dismiss,
            onOpenBook = onOpenBook
        ),
        onFilterChange = viewModel::onFilterChange,
        onRetryAll = viewModel::retryAll,
        onClear = viewModel::clearFinished,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(
    uiState: DownloadsUiState,
    actions: DownloadActions,
    onFilterChange: (DownloadsFilter) -> Unit,
    onRetryAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_downloads_title)) },
                actions = {
                    QueueMenu(uiState = uiState, onRetryAll = onRetryAll, onClear = onClear)
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!uiState.queueIsEmpty) {
                FilterRow(
                    filter = uiState.filter,
                    tally = uiState.tally,
                    onFilterChange = onFilterChange
                )
                SummaryLine(summary = uiState.summary)
            }
            DownloadsContent(uiState = uiState, actions = actions)
        }
    }
}

/** Bulk actions. Behind a menu because neither is something to reach for often. */
@Composable
private fun QueueMenu(uiState: DownloadsUiState, onRetryAll: () -> Unit, onClear: () -> Unit) {
    if (!uiState.hasFailed) return
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(ReadanaIcons.More, stringResource(R.string.feature_downloads_more_actions))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.feature_downloads_action_retry_all)) },
            leadingIcon = { Icon(ReadanaIcons.Refresh, contentDescription = null) },
            onClick = {
                expanded = false
                onRetryAll()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.feature_downloads_action_clear)) },
            leadingIcon = { Icon(ReadanaIcons.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onClear()
            }
        )
    }
}

/**
 * How the queue as a whole is doing, in one line of text.
 *
 * No bar of its own: every row already draws one, and a second bar totalling them is the same
 * information a second time in the place the eye lands first.
 */
@Composable
private fun SummaryLine(summary: QueueSummary?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (summary == null) return@Row
        Text(
            text = summary.line(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

/** Books, bytes, speed and time left, dropping whichever of them is not known yet. */
@Composable
private fun QueueSummary.line(): String {
    val books = pluralStringResource(
        R.plurals.feature_downloads_summary_title,
        activeCount,
        activeCount
    )
    val size = totalBytes?.let {
        stringResource(
            R.string.feature_downloads_summary_progress,
            humanSize(bytesDownloaded),
            humanSize(it)
        )
    }
    val speed = bytesPerSecond?.let {
        stringResource(R.string.feature_downloads_rate, humanSize(it))
    }
    val eta = etaMillis?.let { stringResource(R.string.feature_downloads_eta, humanDuration(it)) }
    return listOfNotNull(books, size, speed, eta).joinToString(" · ")
}

/** Chips carrying their own counts, so the filter doubles as the queue's tally. */
@Composable
private fun FilterRow(
    filter: DownloadsFilter,
    tally: DownloadsTally,
    onFilterChange: (DownloadsFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DownloadsFilter.entries.forEach { entry ->
            FilterChip(
                selected = entry == filter,
                onClick = { onFilterChange(entry) },
                label = {
                    Text(
                        stringResource(
                            R.string.feature_downloads_filter_count,
                            stringResource(entry.labelRes()),
                            tally.countFor(entry)
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun DownloadsContent(uiState: DownloadsUiState, actions: DownloadActions, modifier: Modifier = Modifier) {
    when {
        uiState.isLoading -> LoadingState(modifier = modifier)

        uiState.queueIsEmpty -> EmptyState(
            icon = ReadanaIcons.Download,
            title = stringResource(R.string.feature_downloads_empty_title),
            description = stringResource(R.string.feature_downloads_empty_description),
            modifier = modifier
        )

        uiState.items.isEmpty() -> EmptyState(
            icon = ReadanaIcons.Filter,
            title = stringResource(R.string.feature_downloads_empty_filter_title),
            description = stringResource(R.string.feature_downloads_empty_filter_description),
            modifier = modifier
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 8.dp,
                bottom = ScreenPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.items, key = { it.key }) { item ->
                when (item) {
                    is QueueItem.Download -> DownloadCard(item = item, actions = actions)
                    is QueueItem.Conversion -> ConversionCard(item = item)
                }
            }
        }
    }
}

private fun DownloadsTally.countFor(filter: DownloadsFilter): Int = when (filter) {
    DownloadsFilter.All -> all
    DownloadsFilter.Active -> active
    DownloadsFilter.Failed -> failed
}

private fun DownloadsFilter.labelRes(): Int = when (this) {
    DownloadsFilter.All -> R.string.feature_downloads_filter_all
    DownloadsFilter.Active -> R.string.feature_downloads_filter_active
    DownloadsFilter.Failed -> R.string.feature_downloads_filter_failed
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun DownloadsScreenPreview() {
    ReadanaTheme {
        DownloadsScreen(
            uiState = DownloadsUiState(
                isLoading = false,
                items = listOf(
                    QueueItem.Conversion(
                        ActiveConversion(
                            bookId = "book-9",
                            title = "The Anatomy of Melancholy",
                            phase = ConversionPhase.Extracting,
                            currentPage = 42,
                            totalPages = 380
                        )
                    ),
                    QueueItem.Download(
                        job = previewJob(
                            "1",
                            "Pride and Prejudice",
                            DownloadStatus.Downloading,
                            bytes = 3_000_000,
                            total = 8_000_000
                        ),
                        rate = DownloadRate(bytesPerSecond = 740_000, etaMillis = 6_700)
                    ),
                    QueueItem.Download(
                        previewJob(
                            "2",
                            "Moby-Dick",
                            DownloadStatus.Paused,
                            bytes = 1_200_000,
                            total = 9_400_000
                        )
                    ),
                    QueueItem.Download(
                        previewJob("3", "Frankenstein", DownloadStatus.Failed)
                            .copy(errorKind = DownloadErrorKind.QuotaExhausted)
                    )
                ),
                tally = DownloadsTally(all = 4, active = 3, failed = 1),
                summary = QueueSummary(
                    activeCount = 1,
                    bytesDownloaded = 3_000_000,
                    totalBytes = 8_000_000,
                    bytesPerSecond = 740_000,
                    etaMillis = 6_700
                )
            ),
            actions = previewActions(),
            onFilterChange = {},
            onRetryAll = {},
            onClear = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun DownloadsEmptyPreview() {
    ReadanaTheme {
        DownloadsScreen(
            uiState = DownloadsUiState(isLoading = false, queueIsEmpty = true),
            actions = previewActions(),
            onFilterChange = {},
            onRetryAll = {},
            onClear = {}
        )
    }
}

private fun previewActions() = DownloadActions(
    onPause = {},
    onResume = {},
    onCancel = {},
    onRetry = {},
    onDismiss = {},
    onOpenBook = {}
)

private fun previewJob(id: String, title: String, status: DownloadStatus, bytes: Long = 0, total: Long? = null) =
    DownloadJob(
        id = id,
        sourceId = "annas-archive",
        recordId = id,
        title = title,
        authors = listOf("Jane Austen"),
        format = BookFormat.EPUB,
        status = status,
        bytesDownloaded = bytes,
        totalBytes = total,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
