package dev.readana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.coroutines.ApplicationScope
import dev.readana.core.common.input.VolumeKeyRelay
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.repository.BookmarksRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.Bookmark
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReadingProgress
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.SearchIterator

/** Which sheet, if any, is covering the page. */
internal enum class ReaderSheet { None, Contents, Settings, Search }

/** A saved place, as the sheet shows it. */
internal data class BookmarkItem(val id: Long, val title: String?, val progression: Double?)

/** One search hit, addressed by index so the UI never touches Readium types. */
internal data class SearchHit(
    val index: Int,
    val before: String,
    val highlight: String,
    val after: String,
    val chapter: String?
)

internal data class SearchState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val hasMore: Boolean = false
)

internal sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data object NotFound : ReaderUiState

    data class Failed(val error: AppError) : ReaderUiState

    data class Ready(
        val book: Book,
        val title: String,
        val settings: ReaderSettings,
        /** Bumped for every freshly opened publication so the navigator host is recomposed. */
        val sessionId: Long,
        val chromeVisible: Boolean = false,
        val sheet: ReaderSheet = ReaderSheet.None,
        val toc: List<TocItem> = emptyList(),
        val chapterTitle: String? = null,
        val progression: Float = 0f,
        val positionCount: Int = 0,
        val currentPosition: Int = 0,
        val bookmarks: List<BookmarkItem> = emptyList(),
        val currentIsBookmarked: Boolean = false,
        val isSearchable: Boolean = false,
        val search: SearchState = SearchState()
    ) : ReaderUiState
}

/** A table-of-contents row, addressed by index so the UI never touches Readium types. */
internal data class TocItem(val index: Int, val title: String, val depth: Int)

/** Somewhere the reader can jump to, named without Readium types. */
internal sealed interface ReaderTarget {
    data class Bookmark(val id: Long) : ReaderTarget

    data class Hit(val index: Int) : ReaderTarget

    /** A fraction of the way through the book, as the progress slider reports it. */
    data class Progress(val fraction: Float) : ReaderTarget
}

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionFactory: ReaderSessionFactory,
    private val sessionStore: ReaderSessionStore,
    /** Exposed so the reading surface can claim the volume keys while it is on screen. */
    val volumeKeyRelay: VolumeKeyRelay,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var openedBookId: String? = null
    private var session: ReaderSession? = null
    private var lastLocator: Locator? = null
    private var progressJob: Job? = null
    private var bookmarksJob: Job? = null
    private var bookmarks: List<Bookmark> = emptyList()
    private var searchJob: Job? = null
    private var searchIterator: SearchIterator? = null
    private var searchHits: List<Locator> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepository.readerSettings.collect { settings ->
                _uiState.update { state ->
                    if (state is ReaderUiState.Ready) state.copy(settings = settings) else state
                }
            }
        }
    }

    /** Opens [bookId] once; later calls for the same book are ignored. */
    fun open(bookId: String, systemInDarkTheme: Boolean) {
        if (openedBookId == bookId) return
        openedBookId = bookId
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            val book = libraryRepository.getBook(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState.NotFound
                return@launch
            }
            val settings = settingsRepository.readerSettings.first()
            val created = sessionFactory.create(
                book = book,
                initialLocator = book.progress?.let { parseLocator(it.locatorJson) },
                settings = settings,
                systemInDarkTheme = systemInDarkTheme
            )
            when (created) {
                is AppResult.Failure -> _uiState.value = ReaderUiState.Failed(created.error)
                is AppResult.Success -> start(created.value, book, settings)
            }
        }
    }

    fun toggleChrome() = updateReady {
        it.copy(chromeVisible = !it.chromeVisible)
    }

    /** Shows a sheet, or dismisses whatever is showing when passed [ReaderSheet.None]. */
    fun showSheet(sheet: ReaderSheet) = updateReady { it.copy(sheet = sheet) }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsRepository.updateReaderSettings(transform) }
    }

    /** Bookmarks the current page, or removes the bookmark that is already on it. */
    fun toggleBookmark() {
        val bookId = openedBookId ?: return
        val locator = lastLocator ?: return
        val existing = bookmarks.firstOrNull { it.matches(locator) }
        viewModelScope.launch {
            if (existing != null) {
                bookmarksRepository.remove(existing.id)
            } else {
                bookmarksRepository.add(
                    Bookmark(
                        id = Bookmark.UNSAVED_ID,
                        bookId = bookId,
                        locatorJson = locator.toJSON().toString(),
                        progression = locator.locations.totalProgression,
                        title = locator.title,
                        createdAt = Instant.now()
                    )
                )
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarksRepository.remove(id) }
    }

    fun onSearchQueryChange(query: String) = updateReady {
        it.copy(search = it.search.copy(query = query))
    }

    /** Runs the query in the state, replacing any previous results. */
    fun runSearch() {
        val query = readyState()?.search?.query?.trim().orEmpty()
        searchJob?.cancel()
        searchHits = emptyList()
        searchIterator = null
        if (query.isEmpty()) {
            updateReady { it.copy(search = it.search.copy(hits = emptyList(), hasSearched = false)) }
            return
        }
        updateReady {
            it.copy(search = it.search.copy(hits = emptyList(), isSearching = true, hasSearched = true))
        }
        searchJob = viewModelScope.launch {
            searchIterator = session?.search?.invoke(query)
            loadNextHits()
        }
    }

    fun loadMoreResults() {
        if (searchJob?.isActive == true || searchIterator == null) return
        updateReady { it.copy(search = it.search.copy(isSearching = true)) }
        searchJob = viewModelScope.launch { loadNextHits() }
    }

    /** The link the table-of-contents row at [index] points at. */
    fun tocLink(index: Int): Link? = session?.toc?.getOrNull(index)?.link

    /** Resolves where the reader should jump for [target], or null when it cannot be found. */
    fun locatorFor(target: ReaderTarget): Locator? = when (target) {
        is ReaderTarget.Bookmark -> bookmarks.firstOrNull { it.id == target.id }
            ?.let { parseLocator(it.locatorJson) }

        is ReaderTarget.Hit -> searchHits.getOrNull(target.index)

        is ReaderTarget.Progress -> positionAt(target.fraction)
    }

    private fun positionAt(fraction: Float): Locator? {
        val positions = session?.positions?.takeIf { it.isNotEmpty() } ?: return null
        val index = ((positions.size - 1) * fraction).toInt().coerceIn(0, positions.size - 1)
        return positions[index]
    }

    fun onLocatorChanged(locator: Locator) {
        lastLocator = locator
        val positions = session?.positions.orEmpty()
        val progression = locator.locations.totalProgression?.toFloat() ?: 0f
        updateReady { state ->
            state.copy(
                progression = progression.coerceIn(0f, 1f),
                chapterTitle = locator.title ?: state.chapterTitle,
                currentPosition = locator.locations.position
                    ?: (progression * positions.size).toInt(),
                currentIsBookmarked = bookmarks.any { it.matches(locator) }
            )
        }
        scheduleProgressSave(locator)
    }

    override fun onCleared() = release()

    /** Writes the final position and closes the publication. Called when the reader goes away. */
    fun release() {
        progressJob?.cancel()
        bookmarksJob?.cancel()
        searchJob?.cancel()
        searchIterator = null
        val bookId = openedBookId
        val locator = lastLocator
        val closing = session
        // The final position matters more than this ViewModel's lifetime, so it outlives it.
        if (bookId != null && locator != null) {
            applicationScope.launch { persist(bookId, locator) }
        }
        sessionStore.session = null
        session = null
        closing?.close()
    }

    private fun start(newSession: ReaderSession, book: Book, settings: ReaderSettings) {
        session?.close()
        session = newSession
        sessionStore.session = newSession
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            bookmarksRepository.observeForBook(book.id).collect { saved ->
                bookmarks = saved
                updateReady { state ->
                    state.copy(
                        bookmarks = saved.map { BookmarkItem(it.id, it.title, it.progression) },
                        currentIsBookmarked = lastLocator?.let { locator ->
                            saved.any { it.matches(locator) }
                        } == true
                    )
                }
            }
        }
        _uiState.value = ReaderUiState.Ready(
            book = book,
            title = newSession.title,
            settings = settings,
            sessionId = SESSION_IDS.incrementAndGet(),
            toc = newSession.toc.mapIndexed { index, entry ->
                TocItem(index = index, title = entry.title, depth = entry.depth)
            },
            positionCount = newSession.positions.size,
            progression = book.progress?.fraction ?: 0f,
            isSearchable = newSession.isSearchable
        )
    }

    /** Pulls one page of hits out of the running search. */
    private suspend fun loadNextHits() {
        val iterator = searchIterator
        val page = iterator?.next()?.getOrNull()
        val locators = page?.locators.orEmpty()
        searchHits = searchHits + locators
        updateReady { state ->
            state.copy(
                search = state.search.copy(
                    hits = searchHits.mapIndexed { index, locator -> locator.toHit(index) },
                    isSearching = false,
                    hasMore = page != null
                )
            )
        }
    }

    private fun Locator.toHit(index: Int) = SearchHit(
        index = index,
        before = text.before.orEmpty(),
        highlight = text.highlight.orEmpty(),
        after = text.after.orEmpty(),
        chapter = title
    )

    /** Two locators point at the same page when the resource and progression line up. */
    private fun Bookmark.matches(locator: Locator): Boolean {
        val saved = parseLocator(locatorJson) ?: return false
        if (saved.href != locator.href) return false
        val savedProgression = saved.locations.progression ?: return true
        val currentProgression = locator.locations.progression ?: return true
        return abs(savedProgression - currentProgression) < PROGRESSION_EPSILON
    }

    private fun readyState(): ReaderUiState.Ready? = _uiState.value as? ReaderUiState.Ready

    private fun scheduleProgressSave(locator: Locator) {
        val bookId = openedBookId ?: return
        // Debounced: the navigator reports a locator for every page turn.
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_DELAY_MS)
            persist(bookId, locator)
        }
    }

    private suspend fun persist(bookId: String, locator: Locator) {
        val fraction = locator.locations.totalProgression?.toFloat()?.coerceIn(0f, 1f) ?: 0f
        libraryRepository.updateProgress(
            id = bookId,
            progress = ReadingProgress(locatorJson = locator.toJSON().toString(), fraction = fraction)
        )
    }

    private fun updateReady(transform: (ReaderUiState.Ready) -> ReaderUiState.Ready) {
        _uiState.update { state ->
            if (state is ReaderUiState.Ready) transform(state) else state
        }
    }

    // A locator we cannot parse simply means the book opens at the beginning.
    @Suppress("SwallowedException")
    private fun parseLocator(json: String): Locator? = try {
        Locator.fromJSON(JSONObject(json))
    } catch (error: JSONException) {
        null
    }

    private companion object {
        const val PROGRESS_SAVE_DELAY_MS = 1_000L
        const val PROGRESSION_EPSILON = 0.001
        val SESSION_IDS = AtomicLong()
    }
}
