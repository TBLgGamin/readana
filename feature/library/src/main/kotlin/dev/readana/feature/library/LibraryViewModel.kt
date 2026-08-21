package dev.readana.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.conversion.ConversionEvent
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.RetryOutcome
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportEvent
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BrowserDownload
import dev.readana.core.model.DownloadJob
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
    /** Newest first; finished rows stay until the user dismisses them. */
    val downloads: List<DownloadJob> = emptyList(),
    val conversions: List<ActiveConversion> = emptyList(),
    val message: LibraryMessage? = null,
    val effect: LibraryEffect? = null
)

/** A one-shot navigation request. */
sealed interface LibraryEffect {
    data class OpenBrowser(val download: BrowserDownload) : LibraryEffect
}

/** A conversion in flight, for the progress strip. */
data class ActiveConversion(val bookId: String, val title: String, val currentPage: Int, val totalPages: Int)

/** One-shot outcomes worth a snackbar. */
sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage

    data class Duplicate(val title: String) : LibraryMessage

    data class ImportFailed(val error: AppError) : LibraryMessage

    data class Deleted(val title: String) : LibraryMessage

    data class DeleteFailed(val error: AppError) : LibraryMessage

    data class DownloadFailed(val error: AppError) : LibraryMessage

    data class Converted(val title: String) : LibraryMessage

    data class ConversionFailed(val error: AppError) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val importManager: BookImportManager,
    private val importer: BookImporter,
    private val downloadManager: BookDownloadManager,
    private val conversionManager: ConversionManager
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
        viewModelScope.launch {
            downloadManager.jobs.collect { jobs ->
                _uiState.update { it.copy(downloads = jobs) }
            }
        }
        viewModelScope.launch {
            combine(conversionManager.active, libraryRepository.books) { active, books ->
                active.map { (bookId, progress) ->
                    ActiveConversion(
                        bookId = bookId,
                        title = books.firstOrNull { it.id == bookId }?.title.orEmpty(),
                        currentPage = progress.currentPage,
                        totalPages = progress.totalPages
                    )
                }
            }.collect { conversions ->
                _uiState.update { it.copy(conversions = conversions) }
            }
        }
        viewModelScope.launch {
            conversionManager.events.collect { event ->
                _uiState.update { it.copy(message = event.toMessage()) }
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

    fun retryDownload(jobId: String) {
        viewModelScope.launch {
            when (val outcome = downloadManager.retry(jobId)) {
                RetryOutcome.Requeued -> Unit

                is RetryOutcome.NeedsBrowser -> _uiState.update {
                    it.copy(effect = LibraryEffect.OpenBrowser(outcome.download))
                }

                is RetryOutcome.Failed -> _uiState.update {
                    it.copy(message = LibraryMessage.DownloadFailed(outcome.error))
                }
            }
        }
    }

    fun cancelDownload(jobId: String) {
        viewModelScope.launch { downloadManager.cancel(jobId) }
    }

    fun dismissDownload(jobId: String) {
        viewModelScope.launch { downloadManager.dismiss(jobId) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun consumeEffect() = _uiState.update { it.copy(effect = null) }

    /** Converts a PDF, or runs a converted book through the engine again with current settings. */
    fun convert(bookId: String) {
        conversionManager.enqueue(bookId)
        _uiState.update { it.copy(detailsBook = null) }
    }

    private fun ConversionEvent.toMessage(): LibraryMessage = when (this) {
        is ConversionEvent.Succeeded -> LibraryMessage.Converted(book.title)
        is ConversionEvent.Failed -> LibraryMessage.ConversionFailed(error)
    }

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
