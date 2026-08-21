package dev.readana.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.source.api.BookSource
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
    private val settingsRepository: SettingsRepository
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
     * Resolves the download location. Queuing the actual transfer (WorkManager + conversion)
     * is milestone M3; until then the resolved URL is surfaced to the user.
     */
    fun download(result: SearchResult) {
        viewModelScope.launch {
            val source: BookSource? = sources.byId(result.sourceId)
            val message = when (val outcome = source?.resolveDownload(result)) {
                null -> DiscoverMessage.Failed(
                    AppError.NotFound("Unknown source ${result.sourceId}")
                )

                is AppResult.Failure -> DiscoverMessage.Failed(outcome.error)

                is AppResult.Success ->
                    if (outcome.value.requiresBrowser) {
                        DiscoverMessage.OpenInBrowser(outcome.value.url)
                    } else {
                        DiscoverMessage.ReadyToDownload(outcome.value.url)
                    }
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

data class DiscoverUiState(
    val query: String = "",
    /** Display names of the sources that will be searched. */
    val sourceNames: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val sourceErrors: List<SourceError> = emptyList(),
    val message: DiscoverMessage? = null
)

data class SourceError(val sourceName: String, val error: AppError)

sealed interface DiscoverMessage {
    data class ReadyToDownload(val url: String) : DiscoverMessage

    data class OpenInBrowser(val url: String) : DiscoverMessage

    data class Failed(val error: AppError) : DiscoverMessage
}
