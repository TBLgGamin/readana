package dev.readana.feature.library

import app.cash.turbine.test
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.conversion.ConversionEvent
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.importing.BookImportManager
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportEvent
import dev.readana.core.data.importing.ImportResult
import dev.readana.core.data.messages.DefaultMessageBus
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import dev.readana.core.data.notes.NotesExporter
import dev.readana.core.data.repository.CollectionsRepository
import dev.readana.core.data.repository.HighlightsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.ReadingSessionsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookCollection
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.Highlight
import dev.readana.core.model.ReadingProgress
import dev.readana.core.testing.MainDispatcherRule
import dev.readana.engine.pdf2epub.ConversionPhase
import dev.readana.engine.pdf2epub.ConversionProgress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val importManager = FakeBookImportManager()
    private val importer = FakeBookImporter()
    private val conversionManager = FakeConversionManager()

    private val highlights = FakeHighlightsRepository()

    private val collections = FakeCollectionsRepository()
    private val messageBus = DefaultMessageBus()

    private fun viewModel() = LibraryViewModel(
        libraryRepository = repository,
        importManager = importManager,
        importer = importer,
        conversionManager = conversionManager,
        highlightsRepository = highlights,
        collectionsRepository = collections,
        readingSessionsRepository = FakeReadingSessionsRepository(),
        notesExporter = FakeNotesExporter,
        messages = messageBus
    )

    @Test
    fun `the open details sheet follows the book it is showing`() = runTest {
        repository.upsert(book("1"))
        val viewModel = viewModel()
        runCurrent()
        viewModel.setDetails(viewModel.uiState.value.books.single())
        assertNull(viewModel.uiState.value.detailsBook?.finishedAt)

        viewModel.setFinished("1", finished = true)
        runCurrent()

        // Without this the sheet kept the book as it was when opened, so marking it finished
        // wrote to the database and changed nothing the reader could see.
        assertNotNull(viewModel.uiState.value.detailsBook?.finishedAt)
    }

    @Test
    fun `a shelf made from the library offers the grid to fill it`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.createCollection("Winter")
        runCurrent()

        val selection = assertNotNull(viewModel.uiState.value.shelfSelection)
        assertEquals("Winter", selection.label)
        assertTrue(selection.bookIds.isEmpty())
    }

    @Test
    fun `a shelf made from a book's sheet puts that book on it and asks nothing more`() = runTest {
        repository.upsert(book("1"))
        val viewModel = viewModel()
        runCurrent()
        viewModel.setDetails(viewModel.uiState.value.books.single())

        viewModel.createCollection("Winter")
        runCurrent()

        assertNull(viewModel.uiState.value.shelfSelection)
        assertEquals(setOf("1"), collections.memberships.value.values.single())
    }

    @Test
    fun `picking books for a shelf starts from what is already on it`() = runTest {
        repository.upsert(book("1"))
        repository.upsert(book("2"))
        val viewModel = viewModel()
        val shelf = collections.create("Winter")
        collections.setMembership(shelf.id, "1", member = true)
        runCurrent()

        viewModel.startShelfSelection(Shelf.Collection(shelf.id, shelf.name))

        assertEquals(setOf("1"), viewModel.uiState.value.shelfSelection?.bookIds)
    }

    @Test
    fun `confirming a selection writes both the additions and the removals`() = runTest {
        repository.upsert(book("1"))
        repository.upsert(book("2"))
        val viewModel = viewModel()
        val shelf = collections.create("Winter")
        collections.setMembership(shelf.id, "1", member = true)
        runCurrent()

        val sent = record(messageBus)
        viewModel.startShelfSelection(Shelf.Collection(shelf.id, shelf.name))
        viewModel.toggleShelfSelection("1")
        viewModel.toggleShelfSelection("2")
        viewModel.confirmShelfSelection()
        runCurrent()

        assertEquals(setOf("2"), collections.memberships.value.getValue(shelf.id))
        assertNull(viewModel.uiState.value.shelfSelection)
        assertEquals(
            UiMessage.Quantity(R.plurals.feature_library_shelf_updated, 1, listOf("Winter", 1)),
            sent.single()
        )
    }

    @Test
    fun `backing out of a selection writes nothing`() = runTest {
        repository.upsert(book("1"))
        val viewModel = viewModel()
        val shelf = collections.create("Winter")
        runCurrent()

        viewModel.startShelfSelection(Shelf.Collection(shelf.id, shelf.name))
        viewModel.toggleShelfSelection("1")
        viewModel.cancelShelfSelection()
        runCurrent()

        assertNull(viewModel.uiState.value.shelfSelection)
        assertTrue(collections.memberships.value[shelf.id].isNullOrEmpty())
    }

    @Test
    fun `shows a loading state until the first books arrive`() {
        assertTrue(LibraryUiState().isLoading)
    }

    @Test
    fun `reflects the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val empty = awaitItem()
            assertTrue(empty.libraryIsEmpty)
            assertTrue(empty.books.isEmpty())

            repository.upsert(book("1"))
            assertEquals(listOf("1"), awaitItem().books.map { it.id })

            repository.remove("1")
            assertTrue(awaitItem().libraryIsEmpty)
        }
    }

    @Test
    fun `orders by most recently read by default`() = runTest {
        repository.upsert(book("old", title = "Aardvark", addedAt = 1_000))
        repository.upsert(book("recent", title = "Zebra", addedAt = 500, lastOpenedAt = 9_000))
        val viewModel = viewModel()

        assertEquals(listOf("recent", "old"), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `sorts by title and by author on request`() = runTest {
        repository.upsert(book("1", title = "Zebra", author = "Ann Author"))
        repository.upsert(book("2", title = "aardvark", author = "Zoe Writer"))
        val viewModel = viewModel()

        viewModel.onSortChange(LibrarySort.Title)
        assertEquals(listOf("2", "1"), viewModel.uiState.value.books.map { it.id })

        viewModel.onSortChange(LibrarySort.Author)
        assertEquals(listOf("1", "2"), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `searches title and author, ignoring case`() = runTest {
        repository.upsert(book("1", title = "Moby Dick", author = "Herman Melville"))
        repository.upsert(book("2", title = "Frankenstein", author = "Mary Shelley"))
        val viewModel = viewModel()

        viewModel.onQueryChange("moby")
        assertEquals(listOf("1"), viewModel.uiState.value.books.map { it.id })

        viewModel.onQueryChange("SHELLEY")
        assertEquals(listOf("2"), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `tells an empty library apart from an empty search`() = runTest {
        repository.upsert(book("1", title = "Moby Dick"))
        val viewModel = viewModel()

        viewModel.onQueryChange("nothing matches this")

        val state = viewModel.uiState.value
        assertTrue(state.books.isEmpty())
        assertEquals(false, state.libraryIsEmpty)
    }

    @Test
    fun `hands picked files to the import manager`() = runTest {
        val viewModel = viewModel()

        viewModel.importFiles(listOf("content://a", "content://b"))

        assertEquals(listOf("content://a", "content://b"), importManager.enqueued)
    }

    @Test
    fun `import outcomes go to the bus rather than to this screen`() = runTest {
        val viewModel = viewModel()
        val sent = record(messageBus)

        importManager.emit(ImportEvent.Succeeded(book("1", title = "Moby Dick")))
        runCurrent()
        importManager.emit(ImportEvent.Failed(AppError.Parse("broken")))
        runCurrent()

        assertEquals(
            listOf(R.string.feature_library_import_success, R.string.feature_library_import_failed),
            sent.map { assertIs<UiMessage.Text>(it).id }
        )
    }

    @Test
    fun `mirrors how many imports are running`() = runTest {
        val viewModel = viewModel()

        importManager.setActive(2)

        assertEquals(2, viewModel.uiState.value.activeImports)
    }

    @Test
    fun `deletes a book only after confirmation`() = runTest {
        val target = book("1", title = "Moby Dick")
        repository.upsert(target)
        val viewModel = viewModel()

        viewModel.setDetails(target)
        viewModel.setPendingDelete(target)
        assertEquals(target, viewModel.uiState.value.pendingDelete)
        assertTrue(importer.deleted.isEmpty())

        val sent = record(messageBus)
        viewModel.confirmDelete()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("1"), importer.deleted)
        assertNull(state.pendingDelete)
        assertNull(state.detailsBook)
        assertEquals(
            UiMessage.Text(R.string.feature_library_deleted, listOf("Moby Dick")),
            sent.single()
        )
    }

    @Test
    fun `reports a failed delete`() = runTest {
        val target = book("1")
        repository.upsert(target)
        importer.failure = AppError.Unexpected("disk on fire")
        val viewModel = viewModel()

        val sent = record(messageBus)
        viewModel.setPendingDelete(target)
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(R.string.feature_library_delete_failed, assertIs<UiMessage.Text>(sent.single()).id)
    }

    @Test
    fun `opens and dismisses the details sheet`() = runTest {
        val target = book("1")
        val viewModel = viewModel()

        viewModel.setDetails(target)
        assertEquals(target, viewModel.uiState.value.detailsBook)

        viewModel.setDetails(null)
        assertNull(viewModel.uiState.value.detailsBook)
    }

    @Test
    fun `a converting book is marked as busy`() = runTest {
        repository.upsert(book("1", title = "Moby Dick"))
        val viewModel = viewModel()

        conversionManager.setActive(
            mapOf("1" to ConversionProgress(ConversionPhase.Extracting, currentPage = 4, totalPages = 20))
        )
        runCurrent()

        // How far along is the download queue's business now; the library only needs to know
        // that the book is busy, so its details sheet can say so.
        assertEquals(setOf("1"), viewModel.uiState.value.converting)
    }

    @Test
    fun `converting a book reaches the manager and closes the sheet`() = runTest {
        val target = book("1")
        repository.upsert(target)
        val viewModel = viewModel()
        viewModel.setDetails(target)

        viewModel.convert("1")

        assertEquals(listOf("1"), conversionManager.enqueued)
        assertNull(viewModel.uiState.value.detailsBook)
    }

    @Test
    fun `conversion outcomes go to the bus`() = runTest {
        val viewModel = viewModel()
        val sent = record(messageBus)

        conversionManager.emit(ConversionEvent.Succeeded(book("1", title = "Moby Dick"), null))
        runCurrent()
        conversionManager.emit(ConversionEvent.Failed("1", AppError.Parse("broken")))
        runCurrent()

        assertEquals(
            listOf(R.string.feature_library_converted, R.string.feature_library_conversion_failed),
            sent.map { assertIs<UiMessage.Text>(it).id }
        )
    }

    private fun book(
        id: String,
        title: String = "Book $id",
        author: String = "Author",
        addedAt: Long = 0,
        lastOpenedAt: Long? = null
    ) = Book(
        id = id,
        title = title,
        authors = listOf(author),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "content://test/$id",
        coverUri = null,
        sizeBytes = null,
        addedAt = Instant.ofEpochMilli(addedAt),
        lastOpenedAt = lastOpenedAt?.let(Instant::ofEpochMilli),
        progress = null,
        origin = null
    )
}

private class FakeLibraryRepository : LibraryRepository {
    private val state = MutableStateFlow<Map<String, Book>>(emptyMap())

    override val books: Flow<List<Book>> = state.map { it.values.toList() }

    override fun observeBook(id: String): Flow<Book?> = state.map { it[id] }

    override suspend fun getBook(id: String): Book? = state.value[id]

    override suspend fun findDuplicate(title: String, sizeBytes: Long): Book? =
        state.value.values.firstOrNull { it.title == title && it.sizeBytes == sizeBytes }

    override suspend fun findBySource(sourceId: String, recordId: String): Book? = state.value.values.firstOrNull {
        it.origin?.sourceId == sourceId && it.origin?.recordId == recordId
    }

    override suspend fun upsert(book: Book) = state.update { it + (book.id to book) }

    override suspend fun setFinished(id: String, finished: Boolean) = state.update { books ->
        val book = books[id] ?: return@update books
        books + (id to book.copy(finishedAt = if (finished) Instant.EPOCH else null))
    }

    override suspend fun remove(id: String) = state.update { it - id }

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) =
        state.update { books ->
            val book = books[id] ?: return@update books
            books + (id to book.copy(progress = progress, lastOpenedAt = openedAt))
        }
}

private class FakeBookImportManager : BookImportManager {
    val enqueued = mutableListOf<String>()
    private val active = MutableStateFlow(0)
    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 8)

    override val activeImports: StateFlow<Int> = active

    override val events: SharedFlow<ImportEvent> = _events

    override fun enqueue(uris: List<String>) {
        enqueued += uris
    }

    suspend fun emit(event: ImportEvent) = _events.emit(event)

    fun setActive(count: Int) {
        active.value = count
    }
}

private class FakeBookImporter : BookImporter {
    val deleted = mutableListOf<String>()
    var failure: AppError? = null

    override suspend fun import(uri: String, origin: BookOrigin?): AppResult<ImportResult> =
        failure?.asFailure() ?: error("not used in these tests")

    override suspend fun delete(bookId: String): AppResult<Unit> {
        failure?.let { return it.asFailure() }
        deleted += bookId
        return Unit.asSuccess()
    }
}

private class FakeConversionManager : ConversionManager {
    val enqueued = mutableListOf<String>()
    private val _active = MutableStateFlow<Map<String, ConversionProgress>>(emptyMap())
    private val _events = MutableSharedFlow<ConversionEvent>(extraBufferCapacity = 8)

    override val active: StateFlow<Map<String, ConversionProgress>> = _active

    override val events: SharedFlow<ConversionEvent> = _events

    fun setActive(value: Map<String, ConversionProgress>) {
        _active.value = value
    }

    suspend fun emit(event: ConversionEvent) = _events.emit(event)

    override fun enqueue(bookId: String) {
        enqueued += bookId
    }

    override suspend fun convertAndWait(bookId: String): AppResult<Book> = AppError.Unexpected("not used").asFailure()
}

private class FakeHighlightsRepository : HighlightsRepository {
    private val state = MutableStateFlow<List<Highlight>>(emptyList())
    private var nextId = 1L

    override fun observeForBook(bookId: String): Flow<List<Highlight>> =
        state.map { saved -> saved.filter { it.bookId == bookId } }

    override suspend fun add(highlight: Highlight): Highlight {
        val stored = highlight.copy(id = nextId++)
        state.update { it + stored }
        return stored
    }

    override suspend fun update(highlight: Highlight) = state.update { saved ->
        saved.map { if (it.id == highlight.id) highlight else it }
    }

    override suspend fun remove(id: Long) = state.update { saved -> saved.filterNot { it.id == id } }
}

private object FakeNotesExporter : NotesExporter {
    override suspend fun export(book: Book, highlights: List<Highlight>): String? =
        if (highlights.isEmpty()) null else "content://exported/${book.id}"
}

private class FakeCollectionsRepository : CollectionsRepository {
    private val made = MutableStateFlow<List<BookCollection>>(emptyList())

    override val collections: Flow<List<BookCollection>> = made

    /** Exposed as the state flow it is, so a test can read the shelves back without collecting. */
    override val memberships = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override suspend fun create(name: String): BookCollection {
        val collection = BookCollection(id = name, name = name, createdAt = Instant.EPOCH)
        made.update { it + collection }
        return collection
    }

    override suspend fun rename(id: String, name: String) = made.update { saved ->
        saved.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun delete(id: String) {
        made.update { saved -> saved.filterNot { it.id == id } }
        memberships.update { it - id }
    }

    override suspend fun setMembership(collectionId: String, bookId: String, member: Boolean) =
        memberships.update { shelves ->
            val on = shelves[collectionId].orEmpty()
            shelves + (collectionId to if (member) on + bookId else on - bookId)
        }

    override suspend fun removeBook(bookId: String) = memberships.update { shelves ->
        shelves.mapValues { (_, books) -> books - bookId }
    }
}

private class FakeReadingSessionsRepository : ReadingSessionsRepository {
    override suspend fun record(bookId: String, position: Int) = Unit

    override fun observeRate(bookId: String): Flow<Double?> = flowOf(null)

    override suspend fun removeForBook(bookId: String) = Unit
}

/**
 * The real bus, recording what went through it.
 *
 * It has no dependencies of its own, so there is nothing to fake: collecting from it is exactly
 * what the app shell does.
 */
private fun TestScope.record(bus: MessageBus): List<UiMessage> {
    val seen = mutableListOf<UiMessage>()
    // Unconfined so the collector is subscribed the moment this returns: the bus buffers rather
    // than replays, and a message sent before anyone is listening is simply gone.
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        bus.messages.collect { seen += it }
    }
    return seen
}
