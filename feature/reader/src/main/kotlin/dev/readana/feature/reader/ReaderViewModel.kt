package dev.readana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val publicationOpener: ReadiumPublicationOpener
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var publication: Publication? = null
    private var openedBookId: String? = null

    fun open(bookId: String) {
        if (openedBookId == bookId) return
        openedBookId = bookId
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            val book = libraryRepository.getBook(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState.NotFound
                return@launch
            }
            when (val outcome = publicationOpener.open(book.fileUri)) {
                is AppResult.Failure -> _uiState.value = ReaderUiState.Failed(outcome.error)

                is AppResult.Success -> {
                    publication?.close()
                    publication = outcome.value
                    _uiState.value = ReaderUiState.Ready(
                        book = book,
                        title = outcome.value.metadata.title ?: book.title,
                        tableOfContents = outcome.value.tableOfContents.mapNotNull { it.title }
                    )
                }
            }
        }
    }

    override fun onCleared() {
        publication?.close()
        publication = null
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data object NotFound : ReaderUiState

    data class Failed(val error: AppError) : ReaderUiState

    data class Ready(val book: Book, val title: String, val tableOfContents: List<String>) : ReaderUiState
}
