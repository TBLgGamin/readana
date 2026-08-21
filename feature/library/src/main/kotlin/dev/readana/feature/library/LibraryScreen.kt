package dev.readana.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookCoverPlaceholder
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ReadingProgress
import java.time.Instant

@Composable
internal fun LibraryRoute(
    onOpenBook: (bookId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(uiState = uiState, onOpenBook = onOpenBook, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    onOpenBook: (bookId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_library_title)) }) }
    ) { padding ->
        when (uiState) {
            LibraryUiState.Loading -> LoadingState(Modifier.padding(padding))

            LibraryUiState.Empty -> EmptyState(
                icon = ReadanaIcons.Library,
                title = stringResource(R.string.feature_library_empty_title),
                description = stringResource(R.string.feature_library_empty_description),
                modifier = Modifier.padding(padding)
            )

            is LibraryUiState.Loaded -> BookGrid(
                books = uiState.books,
                onOpenBook = onOpenBook,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun BookGrid(books: List<Book>, onOpenBook: (bookId: String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onOpenBook(book.id) })
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        BookCoverPlaceholder(title = book.title, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
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
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
private fun LibraryScreenPreview() {
    ReadanaTheme {
        LibraryScreen(
            uiState = LibraryUiState.Loaded(
                listOf(
                    previewBook("1", "Pride and Prejudice", "Jane Austen", progress = 0.42f),
                    previewBook("2", "Moby-Dick; or, The Whale", "Herman Melville"),
                    previewBook("3", "Frankenstein", "Mary Shelley", progress = 0.9f)
                )
            ),
            onOpenBook = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenEmptyPreview() {
    ReadanaTheme { LibraryScreen(uiState = LibraryUiState.Empty, onOpenBook = {}) }
}

private fun previewBook(id: String, title: String, author: String, progress: Float? = null) = Book(
    id = id,
    title = title,
    authors = listOf(author),
    language = "en",
    format = BookFormat.EPUB,
    fileUri = "content://preview/$id",
    coverUri = null,
    sizeBytes = null,
    addedAt = Instant.EPOCH,
    lastOpenedAt = null,
    progress = progress?.let { ReadingProgress("{}", it) },
    origin = null
)
