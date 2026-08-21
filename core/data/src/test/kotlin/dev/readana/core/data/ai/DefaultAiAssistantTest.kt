package dev.readana.core.data.ai

import dev.readana.ai.client.LlmClient
import dev.readana.ai.client.LlmClientFactory
import dev.readana.ai.client.LlmEvent
import dev.readana.ai.client.LlmException
import dev.readana.ai.client.LlmModel
import dev.readana.ai.client.LlmRequest
import dev.readana.ai.client.ReadingContext
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.FakeSettingsRepository
import dev.readana.core.data.repository.AiThreadsRepository
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiRole
import dev.readana.core.model.AiSettings
import dev.readana.core.model.AiThread
import dev.readana.core.model.AiThreadKind
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class DefaultAiAssistantTest {
    private val threads = FakeAiThreadsRepository()
    private val settings = FakeSettingsRepository(ai = configuredSettings())
    private var client: LlmClient = FakeLlmClient(listOf("Ishmael ", "narrates."))

    @Test
    fun `a thread stores the context it was started with`() = runTest {
        val thread = startThread()

        assertTrue(thread.contextText.contains("Call me Ishmael"))
        assertTrue(thread.contextText.contains("Moby Dick"))
        assertEquals("Chapter 3", thread.chapterTitle)
    }

    @Test
    fun `starting a thread with nothing read yet fails`() = runTest {
        val result = assistant().startThread(
            book = book,
            kind = AiThreadKind.Explain,
            quote = "x",
            context = ReadingContext(text = "", truncatedAtStart = false),
            chapterTitle = null,
            coveredFraction = 0f
        )

        assertIs<AppResult.Failure>(result)
    }

    @Test
    fun `the answer streams and both turns are stored`() = runTest {
        val thread = startThread()

        val events = assistant().reply(thread, question = "Who narrates?").toList()

        assertEquals("Ishmael narrates.", assertIs<AssistantEvent.Done>(events.last()).text)
        assertEquals(
            listOf(AiRole.User, AiRole.Assistant),
            threads.messagesOf(thread.id).map { it.role }
        )
        assertTrue(threads.messagesOf(thread.id).first().text.contains("Who narrates?"))
    }

    @Test
    fun `saved questions are plain, with the passage kept in the prompt frame`() = runTest {
        val recording = RecordingLlmClient()
        client = recording
        val thread = startThread()
        val assistant = assistant()

        assistant.reply(thread, question = null).toList()
        assistant.reply(thread, question = "And then?").toList()

        val userTurns = threads.messagesOf(thread.id).filter { it.role == AiRole.User }
        assertEquals(listOf("Explain this passage.", "And then?"), userTurns.map { it.text })
        // Both turns still carry the passage, because it lives in the system blocks.
        assertTrue(recording.requests.all { request -> request.system.any { it.text.contains("Call me Ishmael") } })
    }

    @Test
    fun `the book context is sent as one cacheable block, after the rules`() = runTest {
        val recording = RecordingLlmClient()
        client = recording
        val thread = startThread()

        assistant().reply(thread, question = "Why?").toList()

        val system = recording.requests.single().system
        assertTrue(system[0].text.contains("reading companion"))
        assertTrue(system[1].cacheable)
        assertTrue(system[1].text.contains("<book_so_far>"))
        assertTrue(system[2].text.contains("selected this passage"))
        assertTrue(system.last().text.contains("Chapter 3"))
        assertEquals(1, system.count { it.cacheable })
    }

    @Test
    fun `an answer with nothing in it is reported rather than shown as silence`() = runTest {
        client = FakeLlmClient(chunks = emptyList(), stopReason = "max_tokens")
        val thread = startThread()

        val events = assistant().reply(thread, question = "Why?").toList()

        val failure = assertIs<AssistantEvent.Failed>(events.last())
        assertTrue(failure.error.message.contains("budget"), failure.error.message)
        assertTrue(threads.messagesOf(thread.id).none { it.role == AiRole.Assistant })
    }

    @Test
    fun `a stream that breaks off keeps what was written and says what went wrong`() = runTest {
        client = HalfwayFailingLlmClient(AppError.Http(529, "overloaded"))
        val thread = startThread()

        val events = assistant().reply(thread, question = "Why?").toList()

        val failure = assertIs<AssistantEvent.Failed>(events.last())
        assertEquals("Ishmael ", failure.partialText)
        assertIs<AppError.Http>(failure.error)
        // The half answer is kept, so a follow-up still has the thread's history.
        assertEquals("Ishmael ", threads.messagesOf(thread.id).last().text)
    }

    @Test
    fun `a model that refuses the ceiling is asked again within a smaller one`() = runTest {
        val fussy = FussyCeilingLlmClient()
        client = fussy
        val thread = startThread()

        val events = assistant().reply(thread, question = "Why?").toList()

        assertEquals("An answer.", assertIs<AssistantEvent.Done>(events.last()).text)
        assertEquals(2, fussy.ceilings.size)
        assertTrue(fussy.ceilings.first() > fussy.ceilings.last())
    }

    @Test
    fun `a failed request reports the provider's error`() = runTest {
        client = FailingLlmClient(AppError.Unauthorized("bad key"))
        val thread = startThread()

        val events = assistant().reply(thread, question = "Why?").toList()

        assertIs<AppError.Unauthorized>(assertIs<AssistantEvent.Failed>(events.last()).error)
    }

    @Test
    fun `with no provider configured the reply fails rather than throwing`() = runTest {
        val thread = startThread()
        settings.updateAiSettings { AiSettings() }

        val events = assistant().reply(thread, question = "Why?").toList()

        assertIs<AssistantEvent.Failed>(events.last())
    }

    @Test
    fun `a recap is reused while the reader has not moved on`() = runTest {
        val recap = assistant().startThread(
            book = book,
            kind = AiThreadKind.Recap,
            quote = null,
            context = ReadingContext("Call me Ishmael.", truncatedAtStart = false),
            chapterTitle = "Chapter 3",
            coveredFraction = 0.4f
        )
        assertIs<AppResult.Success<AiThread>>(recap)

        assertEquals(recap.value.id, assistant().reusableRecap(book, 0.4f)?.id)
        assertEquals(null, assistant().reusableRecap(book, 0.9f))
    }

    private suspend fun startThread(): AiThread {
        val result = assistant().startThread(
            book = book,
            kind = AiThreadKind.Ask,
            quote = "Call me Ishmael.",
            context = ReadingContext("Call me Ishmael.", truncatedAtStart = false),
            chapterTitle = "Chapter 3",
            coveredFraction = 0.4f
        )
        return assertIs<AppResult.Success<AiThread>>(result).value
    }

    private fun assistant() = DefaultAiAssistant(
        settingsRepository = settings,
        threadsRepository = threads,
        clientFactory = FixedClientFactory(client)
    )

    private val book = Book(
        id = "1",
        title = "Moby Dick",
        authors = listOf("Herman Melville"),
        language = "en",
        format = BookFormat.EPUB,
        fileUri = "file:///books/moby.epub",
        coverUri = null,
        sizeBytes = 1,
        addedAt = Instant.EPOCH,
        lastOpenedAt = null,
        progress = null,
        origin = null
    )

    private fun configuredSettings() = AiSettings(
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
}

private class FixedClientFactory(private val client: LlmClient) : LlmClientFactory {
    override fun create(config: AiProviderConfig): LlmClient? = client.takeIf { config.isUsable }
}

private class FakeLlmClient(private val chunks: List<String>, private val stopReason: String? = "end_turn") :
    LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmEvent> = flowOf(
        *chunks.map { LlmEvent.Delta(it) }.toTypedArray(),
        LlmEvent.Done(stopReason = stopReason)
    )

    override suspend fun listModels(): AppResult<List<LlmModel>> = emptyList<LlmModel>().asSuccess()

    override suspend fun ping(): AppResult<Long> = 1L.asSuccess()
}

private class RecordingLlmClient : LlmClient {
    val requests = mutableListOf<LlmRequest>()

    override fun stream(request: LlmRequest): Flow<LlmEvent> {
        requests += request
        return flowOf(LlmEvent.Delta("ok"), LlmEvent.Done())
    }

    override suspend fun listModels(): AppResult<List<LlmModel>> = emptyList<LlmModel>().asSuccess()

    override suspend fun ping(): AppResult<Long> = 1L.asSuccess()
}

/** Rejects a large output ceiling the way a small or older model does. */
private class FussyCeilingLlmClient : LlmClient {
    val ceilings = mutableListOf<Int>()

    override fun stream(request: LlmRequest): Flow<LlmEvent> {
        ceilings += request.maxOutputTokens
        return if (ceilings.size == 1) {
            flow { throw LlmException(AppError.Http(400, "max_tokens: 32000 > 8192, the maximum")) }
        } else {
            flowOf(LlmEvent.Delta("An answer."), LlmEvent.Done(stopReason = "end_turn"))
        }
    }

    override suspend fun listModels(): AppResult<List<LlmModel>> = emptyList<LlmModel>().asSuccess()

    override suspend fun ping(): AppResult<Long> = 1L.asSuccess()
}

/** Writes a little, then breaks off — an overloaded provider's usual shape. */
private class HalfwayFailingLlmClient(private val error: AppError) : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        emit(LlmEvent.Delta("Ishmael "))
        throw LlmException(error)
    }

    override suspend fun listModels(): AppResult<List<LlmModel>> = emptyList<LlmModel>().asSuccess()

    override suspend fun ping(): AppResult<Long> = 1L.asSuccess()
}

private class FailingLlmClient(private val error: AppError) : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow { throw LlmException(error) }

    override suspend fun listModels(): AppResult<List<LlmModel>> = emptyList<LlmModel>().asSuccess()

    override suspend fun ping(): AppResult<Long> = 1L.asSuccess()
}

private class FakeAiThreadsRepository : AiThreadsRepository {
    private val threads = MutableStateFlow<List<AiThread>>(emptyList())
    private val messages = MutableStateFlow<List<AiMessage>>(emptyList())
    private var nextId = 1L

    override fun observeForBook(bookId: String): Flow<List<AiThread>> =
        threads.map { all -> all.filter { it.bookId == bookId } }

    override fun observeMessages(threadId: Long): Flow<List<AiMessage>> =
        messages.map { all -> all.filter { it.threadId == threadId } }

    override suspend fun messagesOf(threadId: Long): List<AiMessage> = messages.value.filter { it.threadId == threadId }

    override suspend fun find(threadId: Long): AiThread? = threads.value.firstOrNull { it.id == threadId }

    override suspend fun latestRecap(bookId: String): AiThread? = threads.value
        .filter { it.bookId == bookId && it.kind == AiThreadKind.Recap }
        .maxByOrNull { it.updatedAt }

    override suspend fun add(thread: AiThread): AiThread {
        val saved = thread.copy(id = nextId++)
        threads.value = threads.value + saved
        return saved
    }

    override suspend fun addMessage(message: AiMessage): AiMessage {
        val saved = message.copy(id = nextId++)
        messages.value = messages.value + saved
        return saved
    }

    override suspend fun updateMessageText(id: Long, text: String) {
        messages.value = messages.value.map { if (it.id == id) it.copy(text = text) else it }
    }

    override suspend fun touch(thread: AiThread) {
        threads.value = threads.value.map { if (it.id == thread.id) thread else it }
    }

    override suspend fun remove(threadId: Long) {
        threads.value = threads.value.filterNot { it.id == threadId }
        messages.value = messages.value.filterNot { it.threadId == threadId }
    }

    override suspend fun removeForBook(bookId: String) {
        val ids = threads.value.filter { it.bookId == bookId }.map { it.id }.toSet()
        threads.value = threads.value.filterNot { it.bookId == bookId }
        messages.value = messages.value.filterNot { it.threadId in ids }
    }
}
