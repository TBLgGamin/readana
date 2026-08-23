package dev.readana.feature.reader

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.coroutines.ApplicationScope
import dev.readana.core.common.input.VolumeKeyRelay
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.dictionary.DictionaryLookup
import dev.readana.core.data.notes.NotesExporter
import dev.readana.core.data.repository.BookSettingsRepository
import dev.readana.core.data.repository.BookmarksRepository
import dev.readana.core.data.repository.HighlightsRepository
import dev.readana.core.data.repository.InkRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.ReadingSessionsRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.Bookmark
import dev.readana.core.model.DictionaryEntry
import dev.readana.core.model.Highlight
import dev.readana.core.model.HighlightStyle
import dev.readana.core.model.HighlightTint
import dev.readana.core.model.InkPoint
import dev.readana.core.model.InkStroke
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReadingProgress
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.SearchIterator

/** Which sheet, if any, is covering the page. */
internal enum class ReaderSheet { None, Contents, Settings, Search, Conversations, Definition }

/** A word being looked up, and what came back. */
internal data class DefinitionState(val word: String, val isLoading: Boolean = true, val entry: DictionaryEntry? = null)

/** A marked passage as the reader shows it. */
internal data class HighlightItem(
    val id: Long,
    val text: String,
    val note: String?,
    val tint: HighlightTint,
    val style: HighlightStyle,
    val progression: Double?
)

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
        /** False for books whose text cannot be read, which is where the assistant stays away. */
        val hasText: Boolean = false,
        /** True for comics and other fixed-layout EPUBs that always turn one page at a time. */
        val isFixedLayout: Boolean = false,
        val search: SearchState = SearchState(),
        val highlights: List<HighlightItem> = emptyList(),
        /** The highlight whose note is being edited, if any. */
        val editingHighlight: HighlightItem? = null,
        /** A written-out notes file waiting to be handed to another app; one shot. */
        val notesExportUri: String? = null,
        /** True once this book has settings of its own, which is what offers the way back. */
        val hasOwnSettings: Boolean = false,
        /** Reading positions between here and the end of the chapter, and of the book. */
        val positionsLeftInChapter: Int = 0,
        val positionsLeftInBook: Int = 0,
        /** Positions per minute, once enough reading has been done to know. Null until then. */
        val readingRate: Double? = null,
        /** The word the reader asked about, while the definition sheet is up. */
        val definition: DefinitionState? = null,
        /** Ink drawn on this book, with whether the page has moved under each stroke. */
        val ink: List<ReaderInk> = emptyList()
    ) : ReaderUiState
}

/** Reading settings for one book, and whether they are the book's own or the app's defaults. */
private data class BookReaderSettings(val settings: ReaderSettings, val isOwn: Boolean)

/** A table-of-contents row, addressed by index so the UI never touches Readium types. */
internal data class TocItem(val index: Int, val title: String, val depth: Int)

/** Somewhere the reader can jump to, named without Readium types. */
internal sealed interface ReaderTarget {
    data class Bookmark(val id: Long) : ReaderTarget

    /** A table-of-contents row, by its index in [ReaderUiState.Ready.toc]. */
    data class Chapter(val index: Int) : ReaderTarget

    data class Highlight(val id: Long) : ReaderTarget

    data class Hit(val index: Int) : ReaderTarget

    /** A fraction of the way through the book, as the progress slider reports it. */
    data class Progress(val fraction: Float) : ReaderTarget
}

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val highlightsRepository: HighlightsRepository,
    private val inkRepository: InkRepository,
    private val settingsRepository: SettingsRepository,
    private val bookSettingsRepository: BookSettingsRepository,
    private val readingSessionsRepository: ReadingSessionsRepository,
    private val dictionary: DictionaryLookup,
    private val notesExporter: NotesExporter,
    private val sessionFactory: ReaderSessionFactory,
    private val sessionStore: ReaderSessionStore,
    /** Exposed so the reading surface can claim the volume keys while it is on screen. */
    val volumeKeyRelay: VolumeKeyRelay,
    /** Exposed so the reading surface can fill in what the text-selection menu does. */
    val selectionRelay: ReaderSelectionRelay,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var openedBookId: String? = null
    private var session: ReaderSession? = null
    private var lastLocator: Locator? = null
    private var progressJob: Job? = null
    private var bookmarksJob: Job? = null
    private var inkJob: Job? = null
    private var bookmarks: List<Bookmark> = emptyList()
    private var searchJob: Job? = null
    private var searchIterator: SearchIterator? = null
    private var searchHits: List<Locator> = emptyList()
    private var highlightsJob: Job? = null
    private var settingsJob: Job? = null
    private var rateJob: Job? = null
    private var definitionJob: Job? = null
    private var highlights: List<Highlight> = emptyList()

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
            val settings = settingsFor(bookId).first().settings
            observeSettings(bookId)
            observeReadingRate(bookId)
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

    /**
     * Adjusts how the open book reads. The change is kept against that book, not against the app:
     * a comic set to two full-width pages should not turn the next novel into a broadsheet. The
     * global defaults are edited from the Settings screen instead.
     */
    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val bookId = openedBookId ?: return
        val current = (_uiState.value as? ReaderUiState.Ready)?.settings ?: return
        viewModelScope.launch { bookSettingsRepository.set(bookId, transform(current)) }
    }

    /** Hands the open book back to the global defaults. */
    fun resetSettings() {
        val bookId = openedBookId ?: return
        viewModelScope.launch { bookSettingsRepository.clear(bookId) }
    }

    /** The book's own settings when it has any, and the app's defaults until then. */
    private fun settingsFor(bookId: String): Flow<BookReaderSettings> = combine(
        settingsRepository.readerSettings,
        bookSettingsRepository.observeFor(bookId)
    ) { defaults, own -> BookReaderSettings(own ?: defaults, isOwn = own != null) }

    private fun observeReadingRate(bookId: String) {
        rateJob?.cancel()
        rateJob = viewModelScope.launch {
            readingSessionsRepository.observeRate(bookId).collect { rate ->
                updateReady { it.copy(readingRate = rate) }
            }
        }
    }

    private fun observeSettings(bookId: String) {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsFor(bookId).collect { resolved ->
                updateReady {
                    it.copy(
                        settings = resolved.settings.forLayout(it.isFixedLayout),
                        hasOwnSettings = resolved.isOwn
                    )
                }
            }
        }
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

    /**
     * Stores a line drawn on the page.
     *
     * The points are normalised to the surface they were drawn on, so the stroke survives a change
     * of window size, and stamped with the reading settings in force. On a reflowable book those
     * settings are what decides whether the ink is still over the right words later — the stroke is
     * anchored to the current locator, but text moves when the type does (see [InkStroke]).
     */
    fun addInkStroke(points: List<PointF>, surfaceWidth: Int, surfaceHeight: Int) {
        val bookId = openedBookId ?: return
        val locator = lastLocator ?: return
        if (points.isEmpty() || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val settings = (_uiState.value as? ReaderUiState.Ready)?.settings ?: return
        viewModelScope.launch {
            inkRepository.add(
                InkStroke(
                    id = InkStroke.UNSAVED_ID,
                    bookId = bookId,
                    resourceHref = locator.href.toString(),
                    anchorLocatorJson = locator.toJSON().toString(),
                    points = points.map { point ->
                        InkPoint(point.x / surfaceWidth, point.y / surfaceHeight)
                    },
                    tint = HighlightTint.Yellow,
                    layoutFingerprint = InkStroke.fingerprintOf(settings),
                    progression = locator.locations.totalProgression,
                    createdAt = Instant.now()
                )
            )
        }
    }

    /** Wipes the ink on the page being read, which is what an eraser sweep amounts to. */
    fun clearInkOnPage() {
        val bookId = openedBookId ?: return
        val href = lastLocator?.href?.toString() ?: return
        viewModelScope.launch { inkRepository.clearResource(bookId, href) }
    }

    /**
     * Marks [locator] with the text the reader selected. Colour, a note and deletion live in the
     * editor, which opens by tapping the highlight itself — or straight away when [thenEdit] is
     * set, which is what separates "Note" from "Highlight" in the selection menu.
     */
    fun addHighlight(locator: Locator, text: String, thenEdit: Boolean = false) {
        val bookId = openedBookId ?: return
        viewModelScope.launch {
            val saved = highlightsRepository.add(
                Highlight(
                    id = Highlight.UNSAVED_ID,
                    bookId = bookId,
                    locatorJson = locator.toJSON().toString(),
                    text = text,
                    tint = HighlightTint.Yellow,
                    style = HighlightStyle.Highlight,
                    note = null,
                    progression = locator.locations.totalProgression,
                    createdAt = Instant.now()
                )
            )
            if (thenEdit) updateReady { it.copy(editingHighlight = saved.toItem()) }
        }
    }

    /**
     * Looks [word] up and shows what the dictionary says.
     *
     * The sheet opens before the answer arrives: a lookup that takes a moment should not leave the
     * reader wondering whether they hit the button.
     */
    fun define(word: String) {
        val trimmed = word.trim().ifEmpty { return }
        definitionJob?.cancel()
        updateReady { it.copy(sheet = ReaderSheet.Definition, definition = DefinitionState(trimmed)) }
        definitionJob = viewModelScope.launch {
            val entry = dictionary.define(trimmed)
            updateReady { state ->
                // Ignore an answer for a word the reader has already moved on from.
                if (state.definition?.word != trimmed) {
                    state
                } else {
                    state.copy(definition = state.definition.copy(isLoading = false, entry = entry))
                }
            }
        }
    }

    /**
     * Writes the book's notes out as Markdown and offers the file to whatever the reader wants to
     * send it to. Does nothing when there is nothing marked yet.
     */
    fun shareNotes() {
        val book = (_uiState.value as? ReaderUiState.Ready)?.book ?: return
        viewModelScope.launch {
            val uri = notesExporter.export(book, highlights) ?: return@launch
            updateReady { it.copy(notesExportUri = uri) }
        }
    }

    /** Clears the export once the share sheet has been shown. */
    fun consumeNotesExport() = updateReady { it.copy(notesExportUri = null) }

    /** Opens the editor for the highlight with [id], or closes it when passed null. */
    fun editHighlight(id: Long?) = updateReady { state ->
        state.copy(editingHighlight = id?.let { highlights.firstOrNull { it.id == id }?.toItem() })
    }

    fun updateHighlight(id: Long, tint: HighlightTint, note: String?) {
        val existing = highlights.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            highlightsRepository.update(
                existing.copy(tint = tint, note = note?.trim()?.ifEmpty { null })
            )
            updateReady { it.copy(editingHighlight = null) }
        }
    }

    /** Removes a bookmark or a highlight. Other targets are not deletable. */
    fun delete(target: ReaderTarget) {
        viewModelScope.launch {
            when (target) {
                is ReaderTarget.Bookmark -> bookmarksRepository.remove(target.id)

                is ReaderTarget.Highlight -> {
                    highlightsRepository.remove(target.id)
                    updateReady { it.copy(editingHighlight = null) }
                }

                is ReaderTarget.Chapter, is ReaderTarget.Hit, is ReaderTarget.Progress -> Unit
            }
        }
    }

    /** Runs [rawQuery] against the open book, replacing any previous results. */
    fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        searchHits = emptyList()
        searchIterator = null
        if (query.isEmpty()) {
            updateReady {
                it.copy(search = SearchState())
            }
            return
        }
        updateReady {
            it.copy(
                search = SearchState(query = query, isSearching = true, hasSearched = true)
            )
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

    /** Resolves where the reader should jump for [target], or null when it cannot be found. */
    fun locatorFor(target: ReaderTarget): Locator? = when (target) {
        is ReaderTarget.Bookmark -> bookmarks.firstOrNull { it.id == target.id }
            ?.let { parseLocator(it.locatorJson) }

        is ReaderTarget.Highlight -> highlights.firstOrNull { it.id == target.id }
            ?.let { parseLocator(it.locatorJson) }

        is ReaderTarget.Chapter -> session?.toc?.getOrNull(target.index)?.locator

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
            val current = locator.locations.position ?: (progression * positions.size).toInt()
            state.copy(
                progression = progression.coerceIn(0f, 1f),
                chapterTitle = locator.title ?: state.chapterTitle,
                currentPosition = current,
                positionsLeftInChapter = positionsLeftInChapter(current, positions.size),
                positionsLeftInBook = (positions.size - current).coerceAtLeast(0),
                currentIsBookmarked = bookmarks.any { it.matches(locator) }
            )
        }
        scheduleProgressSave(locator)
    }

    /**
     * How far it is to the next chapter that the position list could place. Falls back to the end
     * of the book, which is the right answer for a book with no usable table of contents.
     */
    private fun positionsLeftInChapter(current: Int, total: Int): Int {
        val next = session?.tocPositions
            ?.filter { it != UNPLACED && it > current }
            ?.minOrNull()
            ?: total
        return (next - current).coerceAtLeast(0)
    }

    override fun onCleared() = release()

    /** Writes the final position and closes the publication. Called when the reader goes away. */
    fun release() {
        progressJob?.cancel()
        bookmarksJob?.cancel()
        highlightsJob?.cancel()
        settingsJob?.cancel()
        rateJob?.cancel()
        definitionJob?.cancel()
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
        highlightsJob?.cancel()
        highlightsJob = viewModelScope.launch {
            highlightsRepository.observeForBook(book.id).collect { saved ->
                highlights = saved
                updateReady { state -> state.copy(highlights = saved.map { it.toItem() }) }
            }
        }
        inkJob?.cancel()
        inkJob = viewModelScope.launch {
            inkRepository.observeForBook(book.id).collect { saved ->
                updateReady { state ->
                    val fingerprint = InkStroke.fingerprintOf(state.settings)
                    state.copy(
                        ink = saved.map { stroke ->
                            // A stroke drawn under different settings sits over text that has
                            // since moved; it is still shown, but not as if it were still right.
                            ReaderInk(stroke, isMisplaced = stroke.layoutFingerprint != fingerprint)
                        }
                    )
                }
            }
        }
        _uiState.value = ReaderUiState.Ready(
            book = book,
            title = newSession.title,
            settings = settings.forLayout(newSession.isFixedLayout),
            sessionId = SESSION_IDS.incrementAndGet(),
            toc = newSession.toc.mapIndexed { index, entry ->
                TocItem(index = index, title = entry.title, depth = entry.depth)
            },
            positionCount = newSession.positions.size,
            progression = book.progress?.fraction ?: 0f,
            isSearchable = newSession.isSearchable,
            hasText = newSession.hasText,
            isFixedLayout = newSession.isFixedLayout
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

    private fun Highlight.toItem() = HighlightItem(
        id = id,
        text = text,
        note = note,
        tint = tint,
        style = style,
        progression = progression
    )

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
        // The same settled beat is what a reading session is measured in: written down as it
        // happens, so a sitting survives HyperOS deciding the app has had enough.
        val positions = session?.positions?.size ?: 0
        val position = locator.locations.position ?: (fraction * positions).toInt()
        readingSessionsRepository.record(bookId, position)
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
