package dev.readana.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.browser.BrowserVerificationStatus
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.EnqueueOutcome
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.trending.TrendingBook
import dev.readana.source.trending.TrendingBooks
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
    private val downloadManager: BookDownloadManager,
    private val trendingBooks: TrendingBooks,
    verification: BrowserVerificationStatus
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var awaitingVerification = false

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
        viewModelScope.launch {
            // A source may have to answer a bot check before it can answer us; saying so beats a
            // progress bar that looks stuck.
            verification.verifying.collect { verifying ->
                _uiState.update { it.copy(isVerifying = verifying) }
            }
        }
        loadTrending()
    }

    /**
     * Fills the empty Discover screen with something to browse. Failing is not worth reporting:
     * the search box works regardless, so the shelf just stays hidden.
     */
    private fun loadTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrending = true) }
            val books = (trendingBooks.trending() as? AppResult.Success)?.value.orEmpty()
            _uiState.update { it.copy(trending = books, isLoadingTrending = false) }
        }
    }

    /** Tapping a suggestion searches the book sources for it. */
    fun searchFor(book: TrendingBook) {
        _uiState.update { it.copy(query = book.searchQuery) }
        search()
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
                    // Shadow libraries list the same work many times over, so a page of hits is
                    // mostly repeats; the grid shows one entry per book with the rest behind it.
                    results = ResultGrouping.group(
                        outcomes.flatMap { (_, outcome) ->
                            (outcome as? AppResult.Success)?.value?.results.orEmpty()
                        }
                    ),
                    sourceErrors = outcomes.mapNotNull { (source, outcome) ->
                        (outcome as? AppResult.Failure)?.let {
                            SourceError(
                                sourceName = source.displayName,
                                error = it.error,
                                verifyUrl = (it.error as? AppError.BrowserCheck)?.url
                            )
                        }
                    }
                )
            }
        }
    }

    /**
     * Downloads a book from the popular shelf, which carries no file of its own: the sources are
     * searched for it first and the best match is queued. The message names what was queued, so a
     * wrong edition is obvious rather than silent.
     */
    fun download(book: TrendingBook) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val match = firstMatch(book.searchQuery)
            _uiState.update { it.copy(isSearching = false) }
            if (match == null) {
                show(DiscoverMessage.NothingFound(book.title))
                return@launch
            }
            enqueue(match)
        }
    }

    private suspend fun firstMatch(query: String): SearchResult? {
        val enabledSources = sources.enabled(settingsRepository.sourceSettings.first())
        val outcomes = coroutineScope {
            enabledSources.map { source -> async { source.search(SearchQuery(query)) } }.awaitAll()
        }
        return outcomes.firstNotNullOfOrNull { outcome ->
            (outcome as? AppResult.Success)?.value?.results?.firstOrNull()
        }
    }

    /** Queues a search hit; what it takes to actually fetch it is the download job's problem. */
    fun download(result: SearchResult) {
        viewModelScope.launch { enqueue(result) }
    }

    private suspend fun enqueue(result: SearchResult) {
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

            is EnqueueOutcome.Failed -> show(DiscoverMessage.Failed(outcome.error))
        }
    }

    /** Hands the check to a browser the user can see, and re-runs the search when they come back. */
    fun verifyInBrowser(error: SourceError) {
        val url = error.verifyUrl ?: return
        awaitingVerification = true
        _uiState.update { it.copy(effect = DiscoverEffect.Verify(url, error.sourceName)) }
    }

    /** Called when the screen comes back to the foreground, which is where the browser returns to. */
    fun onResumed() {
        if (!awaitingVerification) return
        awaitingVerification = false
        if (_uiState.value.hasSearched) search()
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
}

data class DiscoverUiState(
    val query: String = "",
    /** Display names of the sources that will be searched. */
    val sourceNames: List<String> = emptyList(),
    val isSearching: Boolean = false,
    /** A source is answering a "checking your browser" page right now. */
    val isVerifying: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<ResultGroup> = emptyList(),
    /** Suggestions shown until the user searches for something. */
    val trending: List<TrendingBook> = emptyList(),
    /** Starts true so the shelf opens on skeletons rather than flashing the empty state. */
    val isLoadingTrending: Boolean = true,
    val sourceErrors: List<SourceError> = emptyList(),
    val message: DiscoverMessage? = null,
    val effect: DiscoverEffect? = null
)

/** [verifyUrl] is set when the source could not get past a bot check on its own. */
data class SourceError(val sourceName: String, val error: AppError, val verifyUrl: String? = null)

sealed interface DiscoverMessage {
    /** [fastDownloadsLeft] is the source's remaining allowance, when it reports one. */
    data class Queued(val title: String, val fastDownloadsLeft: Int?) : DiscoverMessage

    data class AlreadyInLibrary(val title: String) : DiscoverMessage

    data class AlreadyQueued(val title: String) : DiscoverMessage

    /** A popular-shelf book no enabled source could find. */
    data class NothingFound(val title: String) : DiscoverMessage

    data class Failed(val error: AppError) : DiscoverMessage
}

/** A one-shot navigation request. */
sealed interface DiscoverEffect {
    /** Open [url] in a browser the user can see, so they can answer the site's check themselves. */
    data class Verify(val url: String, val sourceName: String) : DiscoverEffect
}
