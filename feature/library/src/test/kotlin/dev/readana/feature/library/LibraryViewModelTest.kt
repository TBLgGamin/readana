package dev.readana.feature.library

import app.cash.turbine.test
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ReadingProgress
import dev.readana.core.testing.MainDispatcherRule
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()

    @Test
    fun `starts loading, then reflects the repository`() = runTest {
        val viewModel = LibraryViewModel(repository)

        // Before anyone collects, the state is the initial Loading value.
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)

        viewModel.uiState.test {
            // The unconfined dispatcher runs the upstream eagerly, so the first
            // observed item is already the repository-backed Empty state.
            assertEquals(LibraryUiState.Empty, awaitItem())

            repository.upsert(book("1"))
            val loaded = assertIs<LibraryUiState.Loaded>(awaitItem())
            assertEquals(listOf("1"), loaded.books.map { it.id })

            repository.remove("1")
            assertEquals(LibraryUiState.Empty, awaitItem())
        }
    }

    private fun book(id: String) = Book(
        id = id,
        title = "Book $id",
        authors = listOf("Author"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "content://test/$id",
        coverUri = null,
        sizeBytes = null,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )
}

private class FakeLibraryRepository : LibraryRepository {
    private val state = MutableStateFlow<Map<String, Book>>(emptyMap())

    override val books: Flow<List<Book>> = state.map { it.values.toList() }

    override fun observeBook(id: String): Flow<Book?> = state.map { it[id] }

    override suspend fun getBook(id: String): Book? = state.value[id]

    override suspend fun upsert(book: Book) = state.update { it + (book.id to book) }

    override suspend fun remove(id: String) = state.update { it - id }

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) =
        state.update { books ->
            val book = books[id] ?: return@update books
            books + (id to book.copy(progress = progress, lastOpenedAt = openedAt))
        }
}
