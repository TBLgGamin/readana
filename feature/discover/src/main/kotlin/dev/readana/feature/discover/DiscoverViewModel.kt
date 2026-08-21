package dev.readana.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val sources: BookSourceRegistry,
    private val settingsRepository: SettingsRepository,
    private val downloadManager: BookDownloadManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.sourceSettings.collect { settings ->
                _uiState.update {
                    it.copy(
                        sourceNames = sources.enabled(settings).map { source ->
                            source.displayName
                        }
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Fans the query out to every enabled source concurrently and merges what comes back. */
    fun search() {
        val text = _uiState.value.query.trim()
        if (text.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    results = emptyList(),
                    sourceErrors = emptyList(),
                    hasSearched = true
                )
            }
            val enabledSources = sources.enabled(settingsRepository.sourceSettings.first())
            val outcomes = coroutineScope {
                enabledSources.map { source ->
                    async { source to source.search(SearchQuery(text)) }
                }.awaitAll()
            }
            _uiState.update { state ->
                state.copy(
                    isSearching = false,
                    results = outcomes.flatMap { (_, outcome) ->
                        (outcome as? AppResult.Success)?.value?.results.orEmpty()
                    },
                    sourceErrors = outcomes.mapNotNull { (source, outcome) ->
                        (outcome as? AppResult.Failure)?.let {
                            SourceError(source.displayName, it.error)
                        }
                    }
                )
            }
        }
    }

    /**
     * Queues the transfer. Sources that need a browser step (a spent membership quota, a wait-list)
     * report a [DiscoverEffect.OpenBrowser] instead of a queue entry.
     */
    fun download(result: SearchResult) {
        viewModelScope.launch {
            when (val outcome = downloadManager.enqueue(result)) {
                is EnqueueOutcome.Enqueued -> show(
                    DiscoverMessage.Queued(result.title, outcome.fastDownloadsLeft)
                )

                is EnqueueOutcome.AlreadyInLibrary -> show(
                    DiscoverMessage.AlreadyInLibrary(outcome.book.title)
                )

                is EnqueueOutcome.AlreadyQueued -> show(
                    DiscoverMessage.AlreadyQueued(outcome.job.title)
                )

                is EnqueueOutcome.RequiresBrowser -> _uiState.update {
                    it.copy(effect = DiscoverEffect.OpenBrowser(outcome.toBrowserDownload()))
                }

                is EnqueueOutcome.Failed -> show(DiscoverMessage.Failed(outcome.error))
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeEffect() {
        _uiState.update { it.copy(effect = null) }
    }

    private fun show(message: DiscoverMessage) {
        _uiState.update { it.copy(message = message) }
    }

    private fun EnqueueOutcome.RequiresBrowser.toBrowserDownload() = BrowserDownload(
        sourceId = result.sourceId.value,
        recordId = result.recordId,
        title = result.title,
        authors = result.authors,
        format = result.format ?: BookFormat.EPUB,
        url = url,
        loginUrl = login?.url,
        loginFormBody = login?.formBody
    )
}

data class DiscoverUiState(
    val query: String = "",
    /** Display names of the sources that will be searched. */
    val sourceNames: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val sourceErrors: List<SourceError> = emptyList(),
    val message: DiscoverMessage? = null,
    val effect: DiscoverEffect? = null
)

data class SourceError(val sourceName: String, val error: AppError)

sealed interface DiscoverMessage {
    /** [fastDownloadsLeft] is the source's remaining allowance, when it reports one. */
    data class Queued(val title: String, val fastDownloadsLeft: Int?) : DiscoverMessage

    data class AlreadyInLibrary(val title: String) : DiscoverMessage

    data class AlreadyQueued(val title: String) : DiscoverMessage

    data class Failed(val error: AppError) : DiscoverMessage
}

/** A one-shot navigation request. */
sealed interface DiscoverEffect {
    data class OpenBrowser(val download: BrowserDownload) : DiscoverEffect
}
