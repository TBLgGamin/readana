package dev.readana.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookCover
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.designsystem.component.SteadyProgress
import dev.readana.core.designsystem.component.WavyProgress
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import dev.readana.core.model.ReadingProgress
import java.time.Instant

/** MIME types the system file picker offers when importing. */
private val ImportMimeTypes = arrayOf("application/epub+zip", "application/pdf")

private val CoverMinSize = 150.dp
private val GridSpacing = 16.dp

@Composable
internal fun LibraryRoute(
    onOpenBook: (bookId: String) -> Unit,
    onOpenBrowser: (BrowserDownload) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Files are copied straight away, so the one-shot read grant the picker returns is enough.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris.map(Uri::toString))
        }
    }
    val currentOnOpenBrowser by rememberUpdatedState(onOpenBrowser)
    val currentOnEffectHandled by rememberUpdatedState(viewModel::consumeEffect)
    LaunchedEffect(uiState.effect) {
        when (val effect = uiState.effect) {
            is LibraryEffect.OpenBrowser -> {
                currentOnOpenBrowser(effect.download)
                currentOnEffectHandled()
            }

            null -> Unit
        }
    }

    LibraryScreen(
        uiState = uiState,
        onOpenBook = onOpenBook,
        onImport = { importLauncher.launch(ImportMimeTypes) },
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        onShowDetails = viewModel::setDetails,
        onRequestDelete = viewModel::setPendingDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onRetryDownload = viewModel::retryDownload,
        onCancelDownload = viewModel::cancelDownload,
        onDismissDownload = viewModel::dismissDownload,
        onConvert = viewModel::convert,
        onDismissMessage = viewModel::consumeMessage,
        modifier = modifier
    )
}

/** Determinate where the extractor reports pages, indeterminate during the later phases. */
@Composable
private fun ConversionStrip(conversion: ActiveConversion, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (conversion.totalPages > 0) {
            WavyProgress(
                progress = { conversion.currentPage.toFloat() / conversion.totalPages },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            WavyProgress(Modifier.fillMaxWidth())
        }
        Text(
            text = if (conversion.totalPages > 0) {
                stringResource(
                    R.string.feature_library_converting_pages,
                    conversion.title,
                    conversion.currentPage,
                    conversion.totalPages
                )
            } else {
                stringResource(R.string.feature_library_converting, conversion.title)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = GridSpacing, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    onOpenBook: (bookId: String) -> Unit,
    onImport: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onShowDetails: (Book?) -> Unit,
    onRequestDelete: (Book?) -> Unit,
    onConfirmDelete: () -> Unit,
    onRetryDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDismissDownload: (String) -> Unit,
    onConvert: (String) -> Unit,
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_library_title)) },
                actions = {
                    if (!uiState.libraryIsEmpty) {
                        SortMenu(sort = uiState.sort, onSortChange = onSortChange)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onImport) {
                Icon(
                    ReadanaIcons.Add,
                    contentDescription = stringResource(R.string.feature_library_import)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!uiState.libraryIsEmpty) {
                SearchField(query = uiState.query, onQueryChange = onQueryChange)
            }
            if (uiState.activeImports > 0) {
                WavyProgress(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.feature_library_importing),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = GridSpacing, vertical = 4.dp)
                )
            }
            uiState.conversions.forEach { conversion ->
                ConversionStrip(conversion)
            }
            DownloadsSection(
                downloads = uiState.downloads,
                onOpenBook = onOpenBook,
                onRetry = onRetryDownload,
                onCancel = onCancelDownload,
                onDismiss = onDismissDownload
            )
            LibraryContent(
                uiState = uiState,
                onOpenBook = onOpenBook,
                onShowDetails = onShowDetails
            )
        }
    }

    uiState.detailsBook?.let { book ->
        BookDetailsSheet(
            book = book,
            isConverting = uiState.conversions.any { it.bookId == book.id },
            onDelete = { onRequestDelete(book) },
            onConvert = { onConvert(book.id) },
            onDismiss = { onShowDetails(null) }
        )
    }
    uiState.pendingDelete?.let { book ->
        DeleteBookDialog(
            book = book,
            onConfirm = onConfirmDelete,
            onDismiss = { onRequestDelete(null) }
        )
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryUiState,
    onOpenBook: (bookId: String) -> Unit,
    onShowDetails: (Book?) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingState(modifier)

        uiState.libraryIsEmpty -> EmptyState(
            icon = ReadanaIcons.Library,
            title = stringResource(R.string.feature_library_empty_title),
            description = stringResource(R.string.feature_library_empty_description),
            modifier = modifier
        )

        uiState.books.isEmpty() -> EmptyState(
            icon = ReadanaIcons.Discover,
            title = stringResource(R.string.feature_library_no_results_title),
            description = stringResource(R.string.feature_library_no_results_description),
            modifier = modifier
        )

        else -> BookGrid(
            books = uiState.books,
            onOpenBook = onOpenBook,
            onShowDetails = onShowDetails,
            modifier = modifier
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GridSpacing, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.feature_library_search_placeholder)) },
        leadingIcon = { Icon(ReadanaIcons.Discover, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        ReadanaIcons.Close,
                        contentDescription = stringResource(R.string.feature_library_search_clear)
                    )
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun SortMenu(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(ReadanaIcons.Sort, contentDescription = stringResource(R.string.feature_library_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        LibrarySort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                onClick = {
                    onSortChange(option)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    onOpenBook: (bookId: String) -> Unit,
    onShowDetails: (Book?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CoverMinSize),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onClick = { onOpenBook(book.id) },
                onLongClick = { onShowDetails(book) }
            )
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        BookCover(
            title = book.title,
            coverUri = book.coverUri,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // One line, always: a title that wraps pushes this card's progress bar below its
        // neighbours' and the row stops lining up.
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = book.authorsLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        book.progress?.let { progress ->
            // Steady, not wavy: this is how far you have read, not something happening now.
            SteadyProgress(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Recent -> R.string.feature_library_sort_recent
    LibrarySort.Title -> R.string.feature_library_sort_title
    LibrarySort.Author -> R.string.feature_library_sort_author
}

@Composable
private fun messageText(message: LibraryMessage): String = when (message) {
    is LibraryMessage.Imported -> stringResource(R.string.feature_library_import_success, message.title)

    is LibraryMessage.Duplicate -> stringResource(R.string.feature_library_import_duplicate, message.title)

    is LibraryMessage.ImportFailed -> stringResource(
        R.string.feature_library_import_failed,
        message.error.message
    )

    is LibraryMessage.Deleted -> stringResource(R.string.feature_library_deleted, message.title)

    is LibraryMessage.DeleteFailed -> stringResource(
        R.string.feature_library_delete_failed,
        message.error.message
    )

    is LibraryMessage.DownloadFailed -> stringResource(
        R.string.feature_library_download_error,
        message.error.message
    )

    is LibraryMessage.Converted -> stringResource(R.string.feature_library_converted, message.title)

    is LibraryMessage.ConversionFailed -> stringResource(
        R.string.feature_library_conversion_failed,
        message.error.message
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
private fun LibraryScreenPreview() {
    ReadanaTheme {
        LibraryScreen(
            uiState = LibraryUiState(
                isLoading = false,
                books = listOf(
                    previewBook("1", "Pride and Prejudice", "Jane Austen", progress = 0.42f),
                    previewBook("2", "Moby-Dick; or, The Whale", "Herman Melville"),
                    previewBook("3", "Frankenstein", "Mary Shelley", progress = 0.9f)
                )
            ),
            onOpenBook = {},
            onImport = {},
            onQueryChange = {},
            onSortChange = {},
            onShowDetails = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onRetryDownload = {},
            onCancelDownload = {},
            onDismissDownload = {},
            onConvert = {},
            onDismissMessage = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenEmptyPreview() {
    ReadanaTheme {
        LibraryScreen(
            uiState = LibraryUiState(isLoading = false, libraryIsEmpty = true),
            onOpenBook = {},
            onImport = {},
            onQueryChange = {},
            onSortChange = {},
            onShowDetails = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onRetryDownload = {},
            onCancelDownload = {},
            onDismissDownload = {},
            onConvert = {},
            onDismissMessage = {}
        )
    }
}

internal fun previewBook(id: String, title: String, author: String, progress: Float? = null) = Book(
    id = id,
    title = title,
    authors = listOf(author),
    language = "en",
    format = BookFormat.EPUB,
    fileUri = "file:///storage/emulated/0/Books/$id.epub",
    coverUri = null,
    sizeBytes = 1_248_576,
    addedAt = Instant.EPOCH,
    lastOpenedAt = null,
    progress = progress?.let { ReadingProgress("{}", it) },
    origin = null
)
