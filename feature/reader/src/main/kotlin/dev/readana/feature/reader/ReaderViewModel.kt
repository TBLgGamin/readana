package dev.readana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.coroutines.ApplicationScope
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReadingProgress
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
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

/** Which sheet, if any, is covering the page. */
internal enum class ReaderSheet { None, Contents, Settings }

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
        val currentPosition: Int = 0
    ) : ReaderUiState
}

/** A table-of-contents row, addressed by index so the UI never touches Readium types. */
internal data class TocItem(val index: Int, val title: String, val depth: Int)

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionFactory: ReaderSessionFactory,
    private val sessionStore: ReaderSessionStore,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var openedBookId: String? = null
    private var session: ReaderSession? = null
    private var lastLocator: Locator? = null
    private var progressJob: Job? = null

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

    fun showSheet(sheet: ReaderSheet) = updateReady { it.copy(sheet = sheet) }

    fun dismissSheet() = updateReady { it.copy(sheet = ReaderSheet.None) }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsRepository.updateReaderSettings(transform) }
    }

    /** The link the table-of-contents row at [index] points at. */
    fun tocLink(index: Int): Link? = session?.toc?.getOrNull(index)?.link

    /** Locator closest to [fraction] of the way through the book, for the progress slider. */
    fun positionAt(fraction: Float): Locator? {
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
                    ?: (progression * positions.size).toInt()
            )
        }
        scheduleProgressSave(locator)
    }

    override fun onCleared() = release()

    /** Writes the final position and closes the publication. Called when the reader goes away. */
    fun release() {
        progressJob?.cancel()
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
        _uiState.value = ReaderUiState.Ready(
            book = book,
            title = newSession.title,
            settings = settings,
            sessionId = SESSION_IDS.incrementAndGet(),
            toc = newSession.toc.mapIndexed { index, entry ->
                TocItem(index = index, title = entry.title, depth = entry.depth)
            },
            positionCount = newSession.positions.size,
            progression = book.progress?.fraction ?: 0f
        )
    }

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
        val SESSION_IDS = AtomicLong()
    }
}
