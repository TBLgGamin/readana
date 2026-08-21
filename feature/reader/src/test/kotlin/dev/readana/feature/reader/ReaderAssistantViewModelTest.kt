package dev.readana.feature.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.ai.client.ReadingContext
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.ai.AiAssistant
import dev.readana.core.data.ai.AssistantEvent
import dev.readana.core.data.repository.AiThreadsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiRole
import dev.readana.core.model.AiSettings
import dev.readana.core.model.AiThread
import dev.readana.core.model.AiThreadKind
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReadingProgress
import dev.readana.core.model.SourceSettings
import dev.readana.core.testing.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReaderAssistantViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val assistant = StubAssistant()
    private val threads = AssistantThreadsRepository()
    private val settings = AssistantSettingsRepository()
    private val library = AssistantLibraryRepository()
    private val sessionStore = ReaderSessionStore()

    private fun viewModel() = ReaderAssistantViewModel(
        assistant = assistant,
        threadsRepository = threads,
        settingsRepository = settings,
        libraryRepository = library,
        sessionStore = sessionStore,
        ioDispatcher = mainDispatcherRule.testDispatcher
    )

    @Test
    fun `the assistant stays away when no provider is configured`() = runTest {
        settings.setAi(AiSettings())
        val viewModel = viewModel()

        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `a book whose text cannot be read is never offered a recap`() = runTest {
        library.book = testBook(lastOpened = Instant.now().minus(Duration.ofDays(5)), progress = 0.4f)
        val viewModel = viewModel()

        viewModel.attach(BOOK_ID, hasText = false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.recapOffered)
    }

    @Test
    fun `explaining a passage opens the sheet and streams an answer`() = runTest {
        val viewModel = viewModel()
        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        viewModel.explain(selection(), progression = 0.4f, chapterTitle = "Chapter 3")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.visible)
        assertEquals(AiThreadKind.Explain, state.kind)
        assertEquals("Call me Ishmael.", state.quote)
        assertEquals(listOf("An answer."), state.messages.map { it.text })
        assertEquals(0.4f, state.coveredFraction)
    }

    @Test
    fun `asking waits for the question before spending anything`() = runTest {
        val viewModel = viewModel()
        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        viewModel.ask(selection(), progression = 0.4f, chapterTitle = null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.visible)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(0, assistant.replies)

        viewModel.send("Who is he?")
        advanceUntilIdle()

        assertEquals(1, assistant.replies)
        assertEquals(listOf("Who is he?", "An answer."), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `a failure is reported and can be retried`() = runTest {
        assistant.failWith = AppError.Unauthorized("bad key")
        val viewModel = viewModel()
        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        viewModel.explain(selection(), progression = 0.4f, chapterTitle = null)
        advanceUntilIdle()
        assertIs<AppError.Unauthorized>(viewModel.uiState.value.error)

        assistant.failWith = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(listOf("An answer."), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `a recap is offered after a long enough absence`() = runTest {
        library.book = testBook(lastOpened = Instant.now().minus(Duration.ofDays(5)), progress = 0.4f)
        val viewModel = viewModel()

        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recapOffered)
        assertEquals(5, viewModel.uiState.value.daysAway)
    }

    @Test
    fun `no recap is offered after a short absence or at the very start`() = runTest {
        library.book = testBook(lastOpened = Instant.now().minus(Duration.ofHours(2)), progress = 0.4f)
        viewModel().also { it.attach(BOOK_ID, hasText = true) }
        advanceUntilIdle()

        library.book = testBook(lastOpened = Instant.now().minus(Duration.ofDays(9)), progress = 0.001f)
        val atStart = viewModel()
        atStart.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        assertFalse(atStart.uiState.value.recapOffered)
    }

    @Test
    fun `accepting the recap reuses a stored one rather than paying again`() = runTest {
        val stored = threads.add(
            AiThread(
                id = AiThread.UNSAVED_ID,
                bookId = BOOK_ID,
                kind = AiThreadKind.Recap,
                title = "Where was I?",
                quote = null,
                contextText = "…",
                chapterTitle = "Chapter 3",
                coveredFraction = 0.4f,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            )
        )
        threads.addMessage(
            AiMessage(
                id = AiMessage.UNSAVED_ID,
                threadId = stored.id,
                role = AiRole.Assistant,
                text = "So far…",
                createdAt = Instant.EPOCH
            )
        )
        assistant.reusable = stored
        val viewModel = viewModel()
        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()

        viewModel.requestRecap(anchor = { null }, progression = 0.4f, chapterTitle = "Chapter 3")
        advanceUntilIdle()

        assertEquals(0, assistant.replies)
        assertEquals(listOf("So far…"), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `deleting the open conversation closes the sheet`() = runTest {
        val viewModel = viewModel()
        viewModel.attach(BOOK_ID, hasText = true)
        advanceUntilIdle()
        viewModel.explain(selection(), progression = 0.4f, chapterTitle = null)
        advanceUntilIdle()
        val threadId = requireNotNull(viewModel.uiState.value.threadId)

        viewModel.deleteThread(threadId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.visible)
    }

    private fun selection() = ReaderSelection(
        locator = Locator(href = Url("chapter1.xhtml")!!, mediaType = MediaType.XHTML),
        text = "Call me Ishmael.",
        anchor = null
    )

    private companion object {
        const val BOOK_ID = "1"
    }
}

private fun testBook(lastOpened: Instant? = null, progress: Float? = null) = Book(
    id = "1",
    title = "Moby Dick",
    authors = listOf("Herman Melville"),
    language = "en",
    format = BookFormat.EPUB,
    fileUri = "file:///books/moby.epub",
    coverUri = null,
    sizeBytes = 1,
    addedAt = Instant.EPOCH,
    lastOpenedAt = lastOpened,
    progress = progress?.let { ReadingProgress(locatorJson = "{}", fraction = it) },
    origin = null
)

private class StubAssistant : AiAssistant {
    var failWith: AppError? = null
    var reusable: AiThread? = null
    var replies = 0
    private var nextId = 1L

    override suspend fun startThread(
        book: Book,
        kind: AiThreadKind,
        quote: String?,
        context: ReadingContext,
        chapterTitle: String?,
        coveredFraction: Float?
    ): AppResult<AiThread> = AiThread(
        id = nextId++,
        bookId = book.id,
        kind = kind,
        title = quote ?: "Where was I?",
        quote = quote,
        contextText = context.text,
        chapterTitle = chapterTitle,
        coveredFraction = coveredFraction,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    ).asSuccess()

    override fun reply(thread: AiThread, question: String?): Flow<AssistantEvent> {
        replies++
        val error = failWith
        return if (error != null) {
            flowOf(AssistantEvent.Failed(error))
        } else {
            flow {
                emit(AssistantEvent.Delta("An "))
                emit(AssistantEvent.Delta("An answer."))
                emit(AssistantEvent.Done("An answer."))
            }
        }
    }

    override suspend fun reusableRecap(book: Book, coveredFraction: Float?): AiThread? = reusable
}

private class AssistantThreadsRepository : AiThreadsRepository {
    private val threads = MutableStateFlow<List<AiThread>>(emptyList())
    private val messages = MutableStateFlow<List<AiMessage>>(emptyList())
    private var nextId = 100L

    override fun observeForBook(bookId: String): Flow<List<AiThread>> =
        threads.map { all -> all.filter { it.bookId == bookId } }

    override fun observeMessages(threadId: Long): Flow<List<AiMessage>> =
        messages.map { all -> all.filter { it.threadId == threadId } }

    override suspend fun messagesOf(threadId: Long): List<AiMessage> = messages.value.filter { it.threadId == threadId }

    override suspend fun find(threadId: Long): AiThread? = threads.value.firstOrNull { it.id == threadId }

    override suspend fun latestRecap(bookId: String): AiThread? =
        threads.value.lastOrNull { it.bookId == bookId && it.kind == AiThreadKind.Recap }

    override suspend fun add(thread: AiThread): AiThread {
        val saved = thread.copy(id = nextId++)
        threads.update { it + saved }
        return saved
    }

    override suspend fun addMessage(message: AiMessage): AiMessage {
        val saved = message.copy(id = nextId++)
        messages.update { it + saved }
        return saved
    }

    override suspend fun updateMessageText(id: Long, text: String) = Unit

    override suspend fun touch(thread: AiThread) = Unit

    override suspend fun remove(threadId: Long) {
        threads.update { all -> all.filterNot { it.id == threadId } }
    }

    override suspend fun removeForBook(bookId: String) {
        threads.update { all -> all.filterNot { it.bookId == bookId } }
    }
}

private class AssistantLibraryRepository : LibraryRepository {
    var book: Book = testBook(lastOpened = null, progress = 0.4f)

    override val books: Flow<List<Book>> = MutableStateFlow(emptyList())

    override fun observeBook(id: String): Flow<Book?> = MutableStateFlow(book)

    override suspend fun getBook(id: String): Book? = book.takeIf { it.id == id }

    override suspend fun findDuplicate(title: String, sizeBytes: Long): Book? = null

    override suspend fun findBySource(sourceId: String, recordId: String): Book? = null

    override suspend fun upsert(book: Book) = Unit

    override suspend fun remove(id: String) = Unit

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) = Unit
}

private class AssistantSettingsRepository : SettingsRepository {
    private val ai = MutableStateFlow(
        AiSettings(
            providers = listOf(
                AiProviderConfig(
                    id = AiProviderKind.Anthropic.name,
                    kind = AiProviderKind.Anthropic,
                    name = "Anthropic",
                    apiKey = "secret"
                )
            ),
            activeProviderId = AiProviderKind.Anthropic.name
        )
    )

    fun setAi(settings: AiSettings) {
        ai.value = settings
    }

    override val readerSettings: Flow<ReaderSettings> = MutableStateFlow(ReaderSettings())

    override suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) = Unit

    override val sourceSettings: Flow<SourceSettings> = MutableStateFlow(SourceSettings())

    override suspend fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) = Unit

    override val conversionSettings: Flow<ConversionSettings> = MutableStateFlow(ConversionSettings())

    override suspend fun updateConversionSettings(transform: (ConversionSettings) -> ConversionSettings) = Unit

    override val aiSettings: Flow<AiSettings> = ai

    override suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings) = ai.update(transform)

    override val libraryFolderUri: Flow<String?> = MutableStateFlow(null)

    override suspend fun setLibraryFolderUri(uri: String?) = Unit
}
