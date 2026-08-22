package dev.readana.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
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
import dev.readana.core.model.ReadingProgress
import dev.readana.core.model.ReadingStatus
import java.time.Instant

/** MIME types the system file picker offers when importing. */
private val ImportMimeTypes = arrayOf("application/epub+zip", "application/pdf")

private val CoverMinSize = 150.dp
private val GridSpacing = 16.dp

@Composable
internal fun LibraryRoute(
    onOpenBook: (bookId: String) -> Unit,
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
    LibraryScreen(
        uiState = uiState,
        onOpenBook = onOpenBook,
        onImport = { importLauncher.launch(ImportMimeTypes) },
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        onFilterChange = viewModel::onFilterChange,
        onShelfChange = viewModel::onShelfChange,
        onSetCollectionMembership = viewModel::setCollectionMembership,
        onCreateCollection = viewModel::createCollection,
        onFinishedChange = viewModel::setFinished,
        onShowDetails = viewModel::setDetails,
        onStartShelfSelection = viewModel::startShelfSelection,
        onToggleShelfSelection = viewModel::toggleShelfSelection,
        onCancelShelfSelection = viewModel::cancelShelfSelection,
        onConfirmShelfSelection = viewModel::confirmShelfSelection,
        onRequestDelete = viewModel::setPendingDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onConvert = viewModel::convert,
        onShareNotes = viewModel::shareNotes,
        onConsumeNotesExport = viewModel::consumeNotesExport,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    onOpenBook: (bookId: String) -> Unit,
    onImport: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onShelfChange: (Shelf?) -> Unit,
    onSetCollectionMembership: (String, Boolean) -> Unit,
    onCreateCollection: (String) -> Unit,
    onFinishedChange: (String, Boolean) -> Unit,
    onShowDetails: (Book?) -> Unit,
    onStartShelfSelection: (Shelf.Collection) -> Unit,
    onToggleShelfSelection: (String) -> Unit,
    onCancelShelfSelection: () -> Unit,
    onConfirmShelfSelection: () -> Unit,
    onRequestDelete: (Book?) -> Unit,
    onConfirmDelete: () -> Unit,
    onConvert: (String) -> Unit,
    onShareNotes: () -> Unit,
    onConsumeNotesExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            val selection = uiState.shelfSelection
            if (selection == null) {
                TopAppBar(
                    title = { Text(stringResource(R.string.feature_library_title)) },
                    actions = {
                        if (!uiState.libraryIsEmpty) {
                            FilterMenu(filter = uiState.filter, onFilterChange = onFilterChange)
                            SortMenu(sort = uiState.sort, onSortChange = onSortChange)
                        }
                    }
                )
            } else {
                SelectionBar(
                    selection = selection,
                    onCancel = onCancelShelfSelection,
                    onConfirm = onConfirmShelfSelection
                )
            }
        },
        floatingActionButton = {
            // Importing is not what the grid is for while a shelf is being filled.
            if (uiState.shelfSelection == null) {
                FloatingActionButton(onClick = onImport) {
                    Icon(
                        ReadanaIcons.Add,
                        contentDescription = stringResource(R.string.feature_library_import)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Everything that narrows or adds to the library steps aside while it is being used
            // as a picker: the only question on screen then is which books go on the shelf.
            if (!uiState.libraryIsEmpty && uiState.shelfSelection == null) {
                SearchField(query = uiState.query, onQueryChange = onQueryChange)
                ShelfBar(
                    shelves = uiState.shelves,
                    selected = uiState.shelf,
                    onSelect = onShelfChange,
                    onStartShelfSelection = onStartShelfSelection,
                    onCreateCollection = onCreateCollection
                )
            }
            if (uiState.activeImports > 0) {
                WavyProgress(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.feature_library_importing),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = GridSpacing, vertical = 4.dp)
                )
            }
            uiState.continueReading?.takeIf { uiState.shelfSelection == null }?.let { book ->
                ContinueReadingCard(
                    book = book,
                    timeLeftMillis = uiState.continueTimeLeftMillis,
                    onOpen = { onOpenBook(book.id) },
                    modifier = Modifier.padding(horizontal = GridSpacing, vertical = 8.dp)
                )
            }
            LibraryContent(
                uiState = uiState,
                onOpenBook = onOpenBook,
                onShowDetails = onShowDetails,
                onToggleShelfSelection = onToggleShelfSelection
            )
        }
    }

    uiState.detailsBook?.let { book ->
        BookDetailsSheet(
            book = book,
            isConverting = book.id in uiState.converting,
            notes = uiState.detailsNotes,
            collections = uiState.collections,
            memberOf = uiState.detailsCollections,
            actions = BookDetailsActions(
                onDelete = { onRequestDelete(book) },
                onConvert = { onConvert(book.id) },
                onShareNotes = onShareNotes,
                onSetCollectionMembership = onSetCollectionMembership,
                onCreateCollection = onCreateCollection,
                onFinishedChange = { finished -> onFinishedChange(book.id, finished) },
                onDismiss = { onShowDetails(null) }
            )
        )
        ShareNotesEffect(
            uri = uiState.notesExportUri,
            title = book.title,
            onConsume = onConsumeNotesExport
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
    onToggleShelfSelection: (String) -> Unit,
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
            selection = uiState.shelfSelection,
            onOpenBook = onOpenBook,
            onShowDetails = onShowDetails,
            onToggleShelfSelection = onToggleShelfSelection,
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

/** Reading status, in the same shape the sort menu already uses so there is nothing new to learn. */
@Composable
private fun FilterMenu(filter: LibraryFilter, onFilterChange: (LibraryFilter) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(ReadanaIcons.Filter, contentDescription = stringResource(R.string.feature_library_filter))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        LibraryFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = { RadioButton(selected = option == filter, onClick = null) },
                onClick = {
                    onFilterChange(option)
                    expanded = false
                }
            )
        }
    }
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.All -> R.string.feature_library_filter_all
    LibraryFilter.Reading -> R.string.feature_library_filter_reading
    LibraryFilter.Unread -> R.string.feature_library_filter_unread
    LibraryFilter.Finished -> R.string.feature_library_filter_finished
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
    selection: ShelfSelection?,
    onOpenBook: (bookId: String) -> Unit,
    onShowDetails: (Book?) -> Unit,
    onToggleShelfSelection: (String) -> Unit,
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
            if (selection == null) {
                BookCard(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                    onLongClick = { onShowDetails(book) }
                )
            } else {
                // A tap picks rather than opens, and holding does nothing: a picker that also
                // opened books would lose the whole selection on a mis-tap.
                BookCard(
                    book = book,
                    selected = book.id in selection.bookIds,
                    onClick = { onToggleShelfSelection(book.id) },
                    onLongClick = { onToggleShelfSelection(book.id) }
                )
            }
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null
) {
    Column(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            BookCover(
                title = book.title,
                coverUri = book.coverUri,
                modifier = Modifier.fillMaxWidth()
            )
            if (selected != null) SelectionMark(selected, Modifier.align(Alignment.TopEnd))
            if (book.readingStatus == ReadingStatus.Finished) {
                FinishedMark(Modifier.align(Alignment.BottomEnd))
            }
        }
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

/**
 * The shelves, with a way to make another one pinned to the right of them.
 *
 * The chips scroll and the button does not, so a library with a dozen shelves still offers the
 * same button in the same place. Making a shelf used to be possible only from inside a book's
 * details sheet, which is a strange place to look for it when you have no book in mind.
 */
@Composable
private fun ShelfBar(
    shelves: List<Shelf>,
    selected: Shelf?,
    onSelect: (Shelf?) -> Unit,
    onStartShelfSelection: (Shelf.Collection) -> Unit,
    onCreateCollection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(start = GridSpacing, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shelves.forEach { shelf ->
                ShelfChip(
                    shelf = shelf,
                    selected = shelf == selected,
                    onClick = { onSelect(shelf) },
                    onLongClick = { (shelf as? Shelf.Collection)?.let(onStartShelfSelection) }
                )
            }
        }
        IconButton(onClick = { naming = true }) {
            Icon(
                ReadanaIcons.Collection,
                contentDescription = stringResource(R.string.feature_library_collections_create)
            )
        }
    }
    if (naming) {
        NewCollectionDialog(
            onCreate = {
                onCreateCollection(it)
                naming = false
            },
            onDismiss = { naming = false }
        )
    }
}

/**
 * One shelf.
 *
 * Built from a Surface rather than a FilterChip because it answers two gestures: a tap narrows the
 * grid to the shelf, and holding a shelf you made turns the grid into a picker for it. A chip
 * swallows the whole gesture for its own click, leaving nowhere for the second one to land.
 */
@Composable
private fun ShelfChip(
    shelf: Shelf,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addLabel = stringResource(R.string.feature_library_shelf_add_books)
    Surface(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = if (shelf is Shelf.Collection) onLongClick else null,
            onLongClickLabel = addLabel.takeIf { shelf is Shelf.Collection }
        ),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when (shelf) {
                    is Shelf.Collection -> ReadanaIcons.Collection
                    is Shelf.Series -> ReadanaIcons.Series
                },
                contentDescription = null,
                modifier = Modifier.size(ShelfIconSize)
            )
            Text(text = shelf.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            // Tapping the chosen shelf shows everything again; the cross says so out loud rather
            // than leaving the reader to discover it.
            if (selected) {
                Icon(
                    imageVector = ReadanaIcons.Close,
                    contentDescription = stringResource(R.string.feature_library_shelf_clear),
                    modifier = Modifier.size(ShelfIconSize)
                )
            }
        }
    }
}

/** The app bar while the grid is a picker: what is being filled, and the two ways out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    selection: ShelfSelection,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Column {
                Text(text = selection.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_library_shelf_selected,
                        selection.bookIds.size,
                        selection.bookIds.size
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    ReadanaIcons.Close,
                    contentDescription = stringResource(R.string.feature_library_shelf_cancel)
                )
            }
        },
        actions = {
            IconButton(onClick = onConfirm) {
                Icon(
                    ReadanaIcons.Done,
                    contentDescription = stringResource(R.string.feature_library_shelf_save)
                )
            }
        }
    )
}

/** A tick over the cover, and a hollow ring over the ones not picked so the grid reads as a picker. */
@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp).size(SelectionMarkSize),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = UNPICKED_MARK_ALPHA)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        if (selected) {
            Icon(
                imageVector = ReadanaIcons.Done,
                contentDescription = null,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/**
 * A book you are done with, marked on the cover.
 *
 * Same corner and same size as the download button on a search result, so the two read as one
 * family of marks on a cover. A plain tick on a filled disc rather than a ringed one: the disc is
 * already the ring, and at this size two circles inside each other turn to mush.
 */
@Composable
private fun FinishedMark(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp).size(SelectionMarkSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary
    ) {
        Icon(
            imageVector = ReadanaIcons.Done,
            contentDescription = stringResource(R.string.feature_library_finished_mark),
            modifier = Modifier.padding(4.dp)
        )
    }
}

/** Just a name: a new shelf has nothing else to decide before it exists. */
@Composable
private fun NewCollectionDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.feature_library_collections_create)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feature_library_collections_new)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.feature_library_collections_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_library_delete_dialog_cancel))
            }
        }
    )
}

private val ShelfIconSize = 18.dp
private val SelectionMarkSize = 28.dp
private const val UNPICKED_MARK_ALPHA = 0.7f

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Recent -> R.string.feature_library_sort_recent
    LibrarySort.Title -> R.string.feature_library_sort_title
    LibrarySort.Author -> R.string.feature_library_sort_author
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
            onFilterChange = {},
            onShelfChange = {},
            onSetCollectionMembership = { _, _ -> },
            onCreateCollection = {},
            onFinishedChange = { _, _ -> },
            onShowDetails = {},
            onStartShelfSelection = {},
            onToggleShelfSelection = {},
            onCancelShelfSelection = {},
            onConfirmShelfSelection = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onConvert = {},
            onShareNotes = {},
            onConsumeNotesExport = {}
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
            onFilterChange = {},
            onShelfChange = {},
            onSetCollectionMembership = { _, _ -> },
            onCreateCollection = {},
            onFinishedChange = { _, _ -> },
            onShowDetails = {},
            onStartShelfSelection = {},
            onToggleShelfSelection = {},
            onCancelShelfSelection = {},
            onConfirmShelfSelection = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onConvert = {},
            onShareNotes = {},
            onConsumeNotesExport = {}
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
