package dev.readana.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.source.api.CatalogEntry
import dev.readana.source.api.CatalogSource
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One feed of one catalog. Opening a shelf pushes another [CatalogKey] rather than replacing this
 * screen's contents, so the system back button walks back out of the catalog for free.
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val sources: BookSourceRegistry,
    private val downloadManager: BookDownloadManager,
    private val messages: MessageBus
) : ViewModel() {
    private lateinit var key: CatalogKey

    private val _uiState = MutableStateFlow(CatalogUiState(title = ""))
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    /** Called once by the route with the feed this screen was opened for. */
    fun open(key: CatalogKey) {
        if (this::key.isInitialized && this.key == key) return
        this.key = key
        _uiState.update { it.copy(title = key.title) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val source = sources.byId(SourceId(key.sourceId)) as? CatalogSource
            if (source == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = AppError.NotFound("That catalog is gone"))
                }
                return@launch
            }
            when (val page = source.browse(key.feedUrl)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = page.error)
                }

                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        // The feed names itself better than the link that led here did.
                        title = page.value.title.ifEmpty { key.title },
                        entries = page.value.entries,
                        nextHref = page.value.nextHref
                    )
                }
            }
        }
    }

    /** Appends the next page of this same feed; shelves and books both simply extend the list. */
    fun loadMore() {
        val next = _uiState.value.nextHref ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val source = sources.byId(SourceId(key.sourceId)) as? CatalogSource
            val page = source?.browse(next)
            _uiState.update { state ->
                when (page) {
                    is AppResult.Success -> state.copy(
                        isLoadingMore = false,
                        entries = state.entries + page.value.entries,
                        nextHref = page.value.nextHref
                    )

                    // A page that will not load is not worth an error row: what is already on
                    // screen still reads fine, so the list simply stops growing.
                    else -> state.copy(isLoadingMore = false, nextHref = null)
                }
            }
        }
    }

    fun download(result: SearchResult) {
        viewModelScope.launch {
            when (val outcome = downloadManager.enqueue(result)) {
                is EnqueueOutcome.Enqueued ->
                    show(UiMessage.text(R.string.feature_discover_message_queued, result.title))

                is EnqueueOutcome.AlreadyInLibrary ->
                    show(UiMessage.text(R.string.feature_discover_message_in_library, outcome.book.title))

                is EnqueueOutcome.AlreadyQueued ->
                    show(UiMessage.text(R.string.feature_discover_message_already_queued, outcome.job.title))

                is EnqueueOutcome.Failed ->
                    show(UiMessage.text(R.string.feature_discover_message_failed, outcome.error.message))
            }
        }
    }

    private fun show(message: UiMessage) = messages.show(message)
}

data class CatalogUiState(
    val title: String,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val entries: List<CatalogEntry> = emptyList(),
    val nextHref: String? = null,
    val error: AppError? = null
) {
    val canLoadMore: Boolean get() = nextHref != null
}
