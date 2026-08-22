package dev.readana.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.conversion.ConversionEvent
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportEvent
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import dev.readana.core.data.notes.NotesExporter
import dev.readana.core.data.reading.ReadingSpeed
import dev.readana.core.data.repository.CollectionsRepository
import dev.readana.core.data.repository.HighlightsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.ReadingSessionsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookCollection
import dev.readana.core.model.Highlight
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the grid is ordered. */
enum class LibrarySort { Recent, Title, Author }

/** Which books the grid shows. Derived from progress, so nothing needs maintaining. */
enum class LibraryFilter { All, Reading, Unread, Finished }

/**
 * A shelf the grid can be narrowed to: one the reader made, or one the publisher did.
 *
 * The two are shown side by side because they answer the same question — "show me only these" —
 * and the reader does not care which of them a shelf came from.
 */
sealed interface Shelf {
    val label: String

    data class Collection(val id: String, override val label: String) : Shelf

    data class Series(override val label: String) : Shelf
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    /** Books after search and sorting; not the whole library. */
    val books: List<Book> = emptyList(),
    /** True when the library itself holds nothing, as opposed to a search matching nothing. */
    val libraryIsEmpty: Boolean = false,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Recent,
    val filter: LibraryFilter = LibraryFilter.All,
    /** Every shelf the library has; absent from the UI entirely when there are none. */
    val shelves: List<Shelf> = emptyList(),
    val shelf: Shelf? = null,
    /** The book to offer to pick back up, when nothing is being searched or filtered. */
    val continueReading: Book? = null,
    val continueTimeLeftMillis: Long? = null,
    val collections: List<BookCollection> = emptyList(),
    /** Which collections the book in the details sheet is on. */
    val detailsCollections: Set<String> = emptySet(),
    val detailsBook: Book? = null,
    /** The open book's marked passages; only collected while its details sheet is up. */
    val detailsNotes: List<Highlight> = emptyList(),
    /** A written-out notes file waiting to be handed to another app; one shot. */
    val notesExportUri: String? = null,
    val pendingDelete: Book? = null,
    val activeImports: Int = 0,
    /** Books being turned into EPUB right now; the queue screen shows how far along. */
    val converting: Set<String> = emptySet(),
    /** Set while the grid is being used to fill a shelf rather than to open a book. */
    val shelfSelection: ShelfSelection? = null
)

/**
 * Filling a shelf straight from the grid.
 *
 * Long-pressing a shelf turns the whole library into a picker for it, which is the only sensible
 * way to put twenty books on a shelf; doing it a book at a time through each one's details sheet
 * is the same twenty decisions plus forty taps.
 */
data class ShelfSelection(
    val collectionId: String,
    val label: String,
    /** Books that will be on the shelf when this is confirmed, members included. */
    val bookIds: Set<String> = emptySet()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val importManager: BookImportManager,
    private val importer: BookImporter,
    private val conversionManager: ConversionManager,
    private val highlightsRepository: HighlightsRepository,
    private val collectionsRepository: CollectionsRepository,
    private val readingSessionsRepository: ReadingSessionsRepository,
    private val notesExporter: NotesExporter,
    private val messages: MessageBus
) : ViewModel() {
    private var notesJob: Job? = null
    private var timeLeftJob: Job? = null

    /** The latest shelf memberships, so a selection can be diffed without re-reading the tables. */
    private var memberships: Map<String, Set<String>> = emptyMap()
    private val criteria = MutableStateFlow(LibraryCriteria())
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                libraryRepository.books,
                collectionsRepository.collections,
                collectionsRepository.memberships,
                criteria,
                ::Content
            ).collect { content -> apply(content) }
        }
        viewModelScope.launch { importManager.events.collect { messages.show(it.toMessage()) } }
        viewModelScope.launch {
            importManager.activeImports.collect { count ->
                _uiState.update { it.copy(activeImports = count) }
            }
        }
        viewModelScope.launch {
            conversionManager.active.collect { active ->
                _uiState.update { it.copy(converting = active.keys) }
            }
        }
        viewModelScope.launch { conversionManager.events.collect { messages.show(it.toMessage()) } }
    }

    fun onQueryChange(value: String) = criteria.update { it.copy(query = value) }

    fun onSortChange(value: LibrarySort) = criteria.update { it.copy(sort = value) }

    fun onFilterChange(value: LibraryFilter) = criteria.update { it.copy(filter = value) }

    /** Narrows the grid to one shelf, or shows everything again when passed the selected one. */
    fun onShelfChange(value: Shelf?) = criteria.update {
        it.copy(shelf = if (value == it.shelf) null else value)
    }

    /** Puts the book in the details sheet on a shelf, or takes it off. */
    fun setCollectionMembership(collectionId: String, member: Boolean) {
        val bookId = _uiState.value.detailsBook?.id ?: return
        viewModelScope.launch { collectionsRepository.setMembership(collectionId, bookId, member) }
    }

    /**
     * Makes a shelf.
     *
     * When a book's details sheet is open the shelf is made for that book and it goes straight on;
     * from the library's own button there is no book yet, so the shelf is simply made and the
     * reader is offered the grid to fill it from.
     */
    fun createCollection(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val bookId = _uiState.value.detailsBook?.id
        viewModelScope.launch {
            val created = collectionsRepository.create(trimmed)
            if (bookId != null) {
                collectionsRepository.setMembership(created.id, bookId, member = true)
            } else {
                messages.show(UiMessage.text(R.string.feature_library_collection_created, created.name))
                _uiState.update {
                    it.copy(shelfSelection = ShelfSelection(created.id, created.name))
                }
            }
        }
    }

    /** Turns the grid into a picker for [shelf], starting from the books already on it. */
    fun startShelfSelection(shelf: Shelf.Collection) = _uiState.update {
        it.copy(
            shelfSelection = ShelfSelection(
                collectionId = shelf.id,
                label = shelf.label,
                bookIds = memberships[shelf.id].orEmpty()
            )
        )
    }

    fun toggleShelfSelection(bookId: String) = _uiState.update { state ->
        val selection = state.shelfSelection ?: return@update state
        val next = if (bookId in selection.bookIds) {
            selection.bookIds - bookId
        } else {
            selection.bookIds + bookId
        }
        state.copy(shelfSelection = selection.copy(bookIds = next))
    }

    fun cancelShelfSelection() = _uiState.update { it.copy(shelfSelection = null) }

    /** Writes only what changed, so a shelf of fifty keeps forty-nine untouched rows. */
    fun confirmShelfSelection() {
        val selection = _uiState.value.shelfSelection ?: return
        val before = memberships[selection.collectionId].orEmpty()
        viewModelScope.launch {
            (selection.bookIds - before).forEach {
                collectionsRepository.setMembership(selection.collectionId, it, member = true)
            }
            (before - selection.bookIds).forEach {
                collectionsRepository.setMembership(selection.collectionId, it, member = false)
            }
            messages.show(
                UiMessage.quantity(
                    R.plurals.feature_library_shelf_updated,
                    selection.bookIds.size,
                    selection.label,
                    selection.bookIds.size
                )
            )
            _uiState.update { it.copy(shelfSelection = null) }
        }
    }

    /** Records the reader's own verdict on a book, which outranks how far the bar has got. */
    fun setFinished(bookId: String, finished: Boolean) {
        viewModelScope.launch { libraryRepository.setFinished(bookId, finished) }
    }

    fun importFiles(uris: List<String>) = importManager.enqueue(uris)

    /**
     * Opens the details sheet for [book], or closes it when passed null.
     *
     * The book's notes are collected only while the sheet is open — the grid has no use for them,
     * and a library of hundreds of books should not observe hundreds of tables to draw covers.
     */
    fun setDetails(book: Book?) {
        _uiState.update { it.copy(detailsBook = book, detailsNotes = emptyList()) }
        notesJob?.cancel()
        notesJob = book?.let { opened ->
            viewModelScope.launch {
                highlightsRepository.observeForBook(opened.id).collect { notes ->
                    _uiState.update { it.copy(detailsNotes = notes) }
                }
            }
        }
    }

    /** Writes the open book's notes out as Markdown and offers the file to another app. */
    fun shareNotes() {
        val state = _uiState.value
        val book = state.detailsBook ?: return
        viewModelScope.launch {
            val uri = notesExporter.export(book, state.detailsNotes) ?: return@launch
            _uiState.update { it.copy(notesExportUri = uri) }
        }
    }

    /** Clears the export once the share sheet has been shown. */
    fun consumeNotesExport() = _uiState.update { it.copy(notesExportUri = null) }

    /** Arms the delete confirmation for [book], or dismisses it when passed null. */
    fun setPendingDelete(book: Book?) = _uiState.update { it.copy(pendingDelete = book) }

    fun confirmDelete() {
        val book = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            messages.show(
                when (val outcome = importer.delete(book.id)) {
                    is AppResult.Failure ->
                        UiMessage.text(R.string.feature_library_delete_failed, outcome.error.message)

                    is AppResult.Success -> UiMessage.text(R.string.feature_library_deleted, book.title)
                }
            )
            notesJob?.cancel()
            _uiState.update {
                it.copy(pendingDelete = null, detailsBook = null, detailsNotes = emptyList())
            }
        }
    }

    /** Converts a PDF, or runs a converted book through the engine again with current settings. */
    fun convert(bookId: String) {
        conversionManager.enqueue(bookId)
        setDetails(null)
    }

    /**
     * Folds everything the library knows into one state.
     *
     * The shortcut at the top and the shelves along it both come out of the same pass, so a book
     * finished in the reader leaves "Reading" and the shortcut together.
     */
    private fun apply(content: Content) {
        memberships = content.memberships
        val criteria = content.criteria
        val shown = content.books.forLibrary(criteria, content.memberships)
        val continueWith = if (criteria.isNarrowed) null else content.books.continueReading()
        observeTimeLeft(continueWith)
        _uiState.update { state ->
            // The open sheet is re-read from the fresh list rather than kept as the snapshot it was
            // opened with. Without this, marking a book finished wrote to the database and changed
            // nothing on screen, because the sheet was still holding the book as it used to be.
            val details = state.detailsBook?.let { open -> content.books.firstOrNull { it.id == open.id } }
            state.copy(
                isLoading = false,
                books = shown,
                libraryIsEmpty = content.books.isEmpty(),
                query = criteria.query,
                sort = criteria.sort,
                filter = criteria.filter,
                shelf = criteria.shelf,
                shelves = content.books.shelves(content.collections),
                collections = content.collections,
                continueReading = continueWith,
                detailsBook = details,
                detailsCollections = details?.let { book ->
                    content.memberships.filterValues { book.id in it }.keys
                }.orEmpty()
            )
        }
    }

    /** How long the shortcut's book has to run, once the reader's pace is known. */
    private fun observeTimeLeft(book: Book?) {
        timeLeftJob?.cancel()
        if (book == null) {
            _uiState.update { it.copy(continueTimeLeftMillis = null) }
            return
        }
        timeLeftJob = viewModelScope.launch {
            readingSessionsRepository.observeRate(book.id).collect { rate ->
                val left = book.progress?.let { progress ->
                    ReadingSpeed.timeLeftMillis(
                        positions = ((1f - progress.fraction) * ESTIMATED_POSITIONS).toInt(),
                        rate = rate
                    )
                }
                _uiState.update { it.copy(continueTimeLeftMillis = left) }
            }
        }
    }

    private fun ConversionEvent.toMessage(): UiMessage = when (this) {
        is ConversionEvent.Succeeded -> UiMessage.text(R.string.feature_library_converted, book.title)

        is ConversionEvent.Failed ->
            UiMessage.text(R.string.feature_library_conversion_failed, error.message)
    }

    private fun ImportEvent.toMessage(): UiMessage = when (this) {
        is ImportEvent.Succeeded -> UiMessage.text(R.string.feature_library_import_success, book.title)
        is ImportEvent.Duplicate -> UiMessage.text(R.string.feature_library_import_duplicate, existing.title)
        is ImportEvent.Failed -> UiMessage.text(R.string.feature_library_import_failed, error.message)
    }

    private data class Content(
        val books: List<Book>,
        val collections: List<BookCollection>,
        val memberships: Map<String, Set<String>>,
        val criteria: LibraryCriteria
    )

    private companion object {
        /**
         * A stand-in for the book's real length, which only an open publication knows.
         *
         * The library never opens one - that is the reader's job and it is expensive - so the
         * shortcut's estimate works from a typical novel's worth of positions. It is close enough
         * for "about three hours left", and the exact figure per chapter appears once the book is
         * open.
         */
        const val ESTIMATED_POSITIONS = 600
    }
}
