package dev.readana.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.Book
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(libraryRepository: LibraryRepository) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = libraryRepository.books
        .map { books ->
            if (books.isEmpty()) LibraryUiState.Empty else LibraryUiState.Loaded(books)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LibraryUiState.Loading
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data object Empty : LibraryUiState

    data class Loaded(val books: List<Book>) : LibraryUiState
}
