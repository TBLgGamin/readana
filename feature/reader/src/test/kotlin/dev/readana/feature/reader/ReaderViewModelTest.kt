package dev.readana.feature.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.common.input.VolumeKeyRelay
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.repository.BookmarksRepository
import dev.readana.core.data.repository.HighlightsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.Bookmark
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.Highlight
import dev.readana.core.model.HighlightTint
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import dev.readana.core.model.ReadingProgress
import dev.readana.core.model.SourceSettings
import dev.readana.core.testing.MainDispatcherRule
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.LocatorCollection
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val bookmarks = FakeBookmarksRepository()
    private val highlights = FakeHighlightsRepository()
    private val settings = FakeSettingsRepository()
    private val sessionStore = ReaderSessionStore()
    private val sessionFactory = FakeReaderSessionFactory()

    private fun viewModel(applicationScope: CoroutineScope) = ReaderViewModel(
        libraryRepository = library,
        bookmarksRepository = bookmarks,
        highlightsRepository = highlights,
        settingsRepository = settings,
        sessionFactory = sessionFactory,
        sessionStore = sessionStore,
        volumeKeyRelay = VolumeKeyRelay(),
        selectionRelay = ReaderSelectionRelay(),
        applicationScope = applicationScope
    )

    @Test
    fun `opens a book and reports it ready`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)

        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        val ready = assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
        assertEquals("Moby Dick", ready.title)
        assertEquals(2, ready.positionCount)
        assertNotNull(sessionStore.session)
    }

    @Test
    fun `reports a book that is no longer in the library`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel(backgroundScope)

        viewModel.open("missing", systemInDarkTheme = false)
        advanceUntilIdle()

        assertEquals(ReaderUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `reports a book that cannot be opened`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        sessionFactory.failure = AppError.Parse("not an EPUB")
        val viewModel = viewModel(backgroundScope)

        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        assertIs<AppError.Parse>(assertIs<ReaderUiState.Failed>(viewModel.uiState.value).error)
    }

    @Test
    fun `resumes from the saved reading position`() = runTest(mainDispatcherRule.testDispatcher) {
        val locator = locator(progression = 0.5)
        library.upsert(book(progress = ReadingProgress(locator.toJSON().toString(), 0.5f)))
        val viewModel = viewModel(backgroundScope)

        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        assertEquals(locator.href, assertNotNull(sessionFactory.requestedLocator).href)
    }

    @Test
    fun `an unreadable saved position just starts at the beginning`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book(progress = ReadingProgress("not json", 0.5f)))
        val viewModel = viewModel(backgroundScope)

        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
        assertNull(sessionFactory.requestedLocator)
    }

    @Test
    fun `saves progress once per burst of page turns`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        viewModel.onLocatorChanged(locator(progression = 0.1))
        viewModel.onLocatorChanged(locator(progression = 0.2))
        viewModel.onLocatorChanged(locator(progression = 0.3))
        advanceTimeBy(PROGRESS_DELAY_MS + 1)
        advanceUntilIdle()

        assertEquals(1, library.progressWrites.size)
        assertEquals(0.3f, library.progressWrites.single().fraction)
    }

    @Test
    fun `writes the last position when the reader closes`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()
        viewModel.onLocatorChanged(locator(progression = 0.75))

        viewModel.release()
        advanceUntilIdle()

        assertEquals(0.75f, library.progressWrites.last().fraction)
        assertNull(sessionStore.session)
        assertTrue(sessionFactory.closed)
    }

    @Test
    fun `toggles the chrome and the sheets`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        viewModel.toggleChrome()
        assertTrue(assertIs<ReaderUiState.Ready>(viewModel.uiState.value).chromeVisible)

        viewModel.showSheet(ReaderSheet.Contents)
        assertEquals(
            ReaderSheet.Contents,
            assertIs<ReaderUiState.Ready>(viewModel.uiState.value).sheet
        )

        viewModel.showSheet(ReaderSheet.None)
        assertEquals(ReaderSheet.None, assertIs<ReaderUiState.Ready>(viewModel.uiState.value).sheet)
    }

    @Test
    fun `picks the position nearest the slider`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        assertEquals(sessionFactory.positions.first(), viewModel.locatorFor(ReaderTarget.Progress(0f)))
        assertEquals(sessionFactory.positions.last(), viewModel.locatorFor(ReaderTarget.Progress(1f)))
    }

    @Test
    fun `live settings changes reach the open book`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        viewModel.updateSettings { it.copy(theme = ReaderTheme.Sepia) }
        advanceUntilIdle()

        val ready = assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
        assertEquals(ReaderTheme.Sepia, ready.settings.theme)
    }

    @Test
    fun `bookmarks the current page and removes it again`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()
        viewModel.onLocatorChanged(locator(progression = 0.5))

        viewModel.toggleBookmark()
        advanceUntilIdle()

        val added = assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
        assertEquals(1, added.bookmarks.size)
        assertTrue(added.currentIsBookmarked)

        viewModel.toggleBookmark()
        advanceUntilIdle()

        val removed = assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
        assertTrue(removed.bookmarks.isEmpty())
        assertFalse(removed.currentIsBookmarked)
    }

    @Test
    fun `deletes a bookmark by id`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()
        viewModel.onLocatorChanged(locator(progression = 0.5))
        viewModel.toggleBookmark()
        advanceUntilIdle()
        val stored = assertIs<ReaderUiState.Ready>(viewModel.uiState.value).bookmarks.single()

        viewModel.delete(ReaderTarget.Bookmark(stored.id))
        advanceUntilIdle()

        assertTrue(assertIs<ReaderUiState.Ready>(viewModel.uiState.value).bookmarks.isEmpty())
    }

    @Test
    fun `searching collects hits and can load another page`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        sessionFactory.searchPages = listOf(
            listOf(locator(progression = 0.1)),
            listOf(locator(progression = 0.2))
        )
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()

        viewModel.runSearch("whale")
        advanceUntilIdle()

        val first = assertIs<ReaderUiState.Ready>(viewModel.uiState.value).search
        assertEquals(1, first.hits.size)
        assertTrue(first.hasSearched)
        assertTrue(first.hasMore)

        viewModel.loadMoreResults()
        advanceUntilIdle()

        assertEquals(2, assertIs<ReaderUiState.Ready>(viewModel.uiState.value).search.hits.size)
    }

    @Test
    fun `an empty query clears the results`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        sessionFactory.searchPages = listOf(listOf(locator(progression = 0.1)))
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()
        viewModel.runSearch("whale")
        advanceUntilIdle()

        viewModel.runSearch("   ")
        advanceUntilIdle()

        val search = assertIs<ReaderUiState.Ready>(viewModel.uiState.value).search
        assertTrue(search.hits.isEmpty())
        assertFalse(search.hasSearched)
    }

    @Test
    fun `highlights the selection and can annotate, recolour and delete it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.upsert(book())
            val viewModel = viewModel(backgroundScope)
            viewModel.open("1", systemInDarkTheme = false)
            advanceUntilIdle()

            viewModel.addHighlight(locator(progression = 0.3), "call me Ishmael", openNote = true)
            advanceUntilIdle()

            val added = assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
            val stored = added.highlights.single()
            assertEquals("call me Ishmael", stored.text)
            assertEquals(HighlightTint.Yellow, stored.tint)
            assertEquals(stored.id, assertNotNull(added.editingHighlight).id)

            viewModel.updateHighlight(stored.id, HighlightTint.Green, "why this matters")
            advanceUntilIdle()

            val edited = assertIs<ReaderUiState.Ready>(viewModel.uiState.value)
            assertEquals("why this matters", edited.highlights.single().note)
            assertEquals(HighlightTint.Green, edited.highlights.single().tint)
            assertNull(edited.editingHighlight)

            viewModel.delete(ReaderTarget.Highlight(stored.id))
            advanceUntilIdle()

            assertTrue(assertIs<ReaderUiState.Ready>(viewModel.uiState.value).highlights.isEmpty())
        }

    @Test
    fun `resolves where a highlight sits`() = runTest(mainDispatcherRule.testDispatcher) {
        library.upsert(book())
        val viewModel = viewModel(backgroundScope)
        viewModel.open("1", systemInDarkTheme = false)
        advanceUntilIdle()
        viewModel.addHighlight(locator(progression = 0.3), "text", openNote = false)
        advanceUntilIdle()
        val stored = assertIs<ReaderUiState.Ready>(viewModel.uiState.value).highlights.single()

        val locator = viewModel.locatorFor(ReaderTarget.Highlight(stored.id))

        assertEquals("chapter1.xhtml", assertNotNull(locator).href.toString())
    }

    private fun book(progress: ReadingProgress? = null) = Book(
        id = "1",
        title = "Moby Dick",
        authors = listOf("Herman Melville"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/moby.epub",
        coverUri = null,
        sizeBytes = 10,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = progress,
        origin = null
    )

    private companion object {
        const val PROGRESS_DELAY_MS = 1_000L
    }
}

internal fun locator(progression: Double): Locator = Locator(
    href = Url("chapter1.xhtml")!!,
    mediaType = MediaType.XHTML,
    locations = Locator.Locations(totalProgression = progression, position = 1)
)

private class FakeLibraryRepository : LibraryRepository {
    val progressWrites = mutableListOf<ReadingProgress>()
    private val state = MutableStateFlow<Map<String, Book>>(emptyMap())

    override val books: Flow<List<Book>> = state.map { it.values.toList() }

    override fun observeBook(id: String): Flow<Book?> = state.map { it[id] }

    override suspend fun getBook(id: String): Book? = state.value[id]

    override suspend fun findDuplicate(title: String, sizeBytes: Long): Book? = null

    override suspend fun findBySource(sourceId: String, recordId: String): Book? = null

    override suspend fun upsert(book: Book) = state.update { it + (book.id to book) }

    override suspend fun remove(id: String) = state.update { it - id }

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) {
        progressWrites += progress
    }
}

private class FakeBookmarksRepository : BookmarksRepository {
    private val state = MutableStateFlow<List<Bookmark>>(emptyList())
    private var nextId = 1L

    override fun observeForBook(bookId: String): Flow<List<Bookmark>> =
        state.map { saved -> saved.filter { it.bookId == bookId } }

    override suspend fun add(bookmark: Bookmark): Bookmark {
        val stored = bookmark.copy(id = nextId++)
        state.update { it + stored }
        return stored
    }

    override suspend fun remove(id: Long) = state.update { saved -> saved.filterNot { it.id == id } }
}

private class FakeHighlightsRepository : HighlightsRepository {
    private val state = MutableStateFlow<List<Highlight>>(emptyList())
    private var nextId = 1L

    override fun observeForBook(bookId: String): Flow<List<Highlight>> =
        state.map { saved -> saved.filter { it.bookId == bookId } }

    override suspend fun add(highlight: Highlight, progression: Double?): Highlight {
        val stored = highlight.copy(id = nextId++)
        state.update { it + stored }
        return stored
    }

    override suspend fun update(highlight: Highlight) = state.update { saved ->
        saved.map { if (it.id == highlight.id) highlight else it }
    }

    override suspend fun remove(id: Long) = state.update { saved -> saved.filterNot { it.id == id } }
}

private class FakeSettingsRepository : SettingsRepository {
    private val reader = MutableStateFlow(ReaderSettings())
    private val sources = MutableStateFlow(SourceSettings())
    private val folder = MutableStateFlow<String?>(null)

    override val readerSettings: Flow<ReaderSettings> = reader

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = reader.update(transform)

    override val sourceSettings: Flow<SourceSettings> = sources

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) = sources.update(transform)

    override val conversionSettings: Flow<ConversionSettings> = MutableStateFlow(ConversionSettings())

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) = Unit

    override val libraryFolderUri: Flow<String?> = folder

    override suspend fun setLibraryFolderUri(uri: String?) {
        folder.value = uri
    }
}

private class FakeReaderSessionFactory : ReaderSessionFactory {
    val positions = listOf(locator(progression = 0.0), locator(progression = 1.0))
    var failure: AppError? = null
    var requestedLocator: Locator? = null
    var closed = false
    var searchPages: List<List<Locator>> = emptyList()

    override suspend fun create(
        book: Book,
        initialLocator: Locator?,
        settings: ReaderSettings,
        systemInDarkTheme: Boolean
    ): AppResult<ReaderSession> {
        requestedLocator = initialLocator
        failure?.let { return it.asFailure() }
        return ReaderSession(
            bookId = book.id,
            title = book.title,
            navigatorClass = androidx.fragment.app.Fragment::class.java,
            fragmentFactory = androidx.fragment.app.FragmentFactory(),
            toc = emptyList(),
            positions = positions,
            isSearchable = true,
            search = { FakeSearchIterator(searchPages) },
            onClose = { closed = true }
        ).asSuccess()
    }
}

/** Hands out one canned page of hits per call, then reports the end of the results. */
private class FakeSearchIterator(private val pages: List<List<Locator>>) : SearchIterator {
    private var page = 0

    override suspend fun next(): Try<LocatorCollection?, Nothing> {
        if (page >= pages.size) return Try.success(null)
        return Try.success(LocatorCollection(locators = pages[page++]))
    }

    override fun close() = Unit
}
