package dev.readana.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportEvent
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the grid is ordered. */
enum class LibrarySort { Recent, Title, Author }

data class LibraryUiState(
    val isLoading: Boolean = true,
    /** Books after search and sorting; not the whole library. */
    val books: List<Book> = emptyList(),
    /** True when the library itself holds nothing, as opposed to a search matching nothing. */
    val libraryIsEmpty: Boolean = false,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Recent,
    val detailsBook: Book? = null,
    val pendingDelete: Book? = null,
    val activeImports: Int = 0,
    val message: LibraryMessage? = null
)

/** One-shot outcomes worth a snackbar. */
sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage

    data class Duplicate(val title: String) : LibraryMessage

    data class ImportFailed(val error: AppError) : LibraryMessage

    data class Deleted(val title: String) : LibraryMessage

    data class DeleteFailed(val error: AppError) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val importManager: BookImportManager,
    private val importer: BookImporter
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.Recent)
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(libraryRepository.books, query, sort, ::Content).collect { content ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        books = content.books.matching(content.query)
                            .sortedWith(content.sort.comparator()),
                        libraryIsEmpty = content.books.isEmpty(),
                        query = content.query,
                        sort = content.sort
                    )
                }
            }
        }
        viewModelScope.launch {
            importManager.events.collect { event ->
                _uiState.update { it.copy(message = event.toMessage()) }
            }
        }
        viewModelScope.launch {
            importManager.activeImports.collect { count ->
                _uiState.update { it.copy(activeImports = count) }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSortChange(value: LibrarySort) {
        sort.value = value
    }

    fun importFiles(uris: List<String>) = importManager.enqueue(uris)

    /** Opens the details sheet for [book], or closes it when passed null. */
    fun setDetails(book: Book?) = _uiState.update { it.copy(detailsBook = book) }

    /** Arms the delete confirmation for [book], or dismisses it when passed null. */
    fun setPendingDelete(book: Book?) = _uiState.update { it.copy(pendingDelete = book) }

    fun confirmDelete() {
        val book = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            val message = when (val outcome = importer.delete(book.id)) {
                is AppResult.Failure -> LibraryMessage.DeleteFailed(outcome.error)
                is AppResult.Success -> LibraryMessage.Deleted(book.title)
            }
            _uiState.update {
                it.copy(pendingDelete = null, detailsBook = null, message = message)
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun ImportEvent.toMessage(): LibraryMessage = when (this) {
        is ImportEvent.Succeeded -> LibraryMessage.Imported(book.title)
        is ImportEvent.Duplicate -> LibraryMessage.Duplicate(existing.title)
        is ImportEvent.Failed -> LibraryMessage.ImportFailed(error)
    }

    private fun List<Book>.matching(query: String): List<Book> {
        if (query.isBlank()) return this
        return filter { book ->
            book.title.contains(query, ignoreCase = true) ||
                book.authors.any { it.contains(query, ignoreCase = true) }
        }
    }

    private fun LibrarySort.comparator(): Comparator<Book> = when (this) {
        LibrarySort.Recent -> compareByDescending { it.lastOpenedAt ?: it.addedAt }

        LibrarySort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }

        LibrarySort.Author -> compareBy(
            { it.authors.firstOrNull()?.lowercase().orEmpty() },
            { it.title.lowercase() }
        )
    }

    private data class Content(val books: List<Book>, val query: String, val sort: LibrarySort)
}
