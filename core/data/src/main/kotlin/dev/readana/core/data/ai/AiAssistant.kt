package dev.readana.core.data.ai

import dev.readana.ai.client.ChatMessage
import dev.readana.ai.client.ChatRole
import dev.readana.ai.client.LlmClient
import dev.readana.ai.client.LlmClientFactory
import dev.readana.ai.client.LlmEvent
import dev.readana.ai.client.LlmException
import dev.readana.ai.client.LlmRequest
import dev.readana.ai.client.PromptBlock
import dev.readana.ai.client.ReadingContext
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.repository.AiThreadsRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiRole
import dev.readana.core.model.AiThread
import dev.readana.core.model.AiThreadKind
import dev.readana.core.model.Book
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/** What the assistant reports while an answer is being written. */
sealed interface AssistantEvent {
    /** More of the answer arrived; [text] is everything written so far. */
    data class Delta(val text: String) : AssistantEvent

    data class Done(val text: String) : AssistantEvent

    data class Failed(val error: AppError) : AssistantEvent
}

/**
 * Runs the reading assistant: turns a piece of already-read text plus a question into a streamed
 * answer, and keeps the conversation.
 */
interface AiAssistant {
    /**
     * Starts a conversation anchored to what the reader had read at this moment. The rendered
     * [context] is stored, so follow-ups never see more of the book than this.
     */
    suspend fun startThread(
        book: Book,
        kind: AiThreadKind,
        quote: String?,
        context: ReadingContext,
        chapterTitle: String?,
        coveredFraction: Float?
    ): AppResult<AiThread>

    /** Sends [question] (null for the opening turn of an Explain or Recap) and streams the answer. */
    fun reply(thread: AiThread, question: String?): Flow<AssistantEvent>

    /** A stored recap still close enough to the reader's position to reuse, or null. */
    suspend fun reusableRecap(book: Book, coveredFraction: Float?): AiThread?
}

@Singleton
class DefaultAiAssistant @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val threadsRepository: AiThreadsRepository,
    private val clientFactory: LlmClientFactory
) : AiAssistant {
    override suspend fun startThread(
        book: Book,
        kind: AiThreadKind,
        quote: String?,
        context: ReadingContext,
        chapterTitle: String?,
        coveredFraction: Float?
    ): AppResult<AiThread> {
        if (context.isEmpty) {
            return AppError.Unsupported("There is nothing read yet to ask about.").asFailure()
        }
        val now = Instant.now()
        val thread = AiThread(
            id = AiThread.UNSAVED_ID,
            bookId = book.id,
            kind = kind,
            title = titleFor(kind, quote),
            quote = quote,
            // Stored rendered, so every turn resends the same bytes and prompt caches keep hitting.
            contextText = AiPrompts.context(book, context.text),
            chapterTitle = chapterTitle,
            coveredFraction = coveredFraction,
            createdAt = now,
            updatedAt = now
        )
        return threadsRepository.add(thread).asSuccess()
    }

    override fun reply(thread: AiThread, question: String?): Flow<AssistantEvent> = flow {
        val settings = settingsRepository.aiSettings.first()
        val provider = settings.active
        val client = provider?.let { clientFactory.create(it) }
        if (client == null) {
            emit(AssistantEvent.Failed(AppError.Unauthorized(NO_PROVIDER)))
            return@flow
        }

        val history = threadsRepository.messagesOf(thread.id).map { it.toChatMessage() }
        val userText = AiPrompts.question(question)
        threadsRepository.addMessage(userMessage(thread, userText))

        val request = requestFor(thread, history, userText)
        val answer = StringBuilder()
        val failure = collectAnswer(client, request, answer)

        finish(thread, answer.toString(), failure)
        emit(
            if (failure != null && answer.isEmpty()) {
                AssistantEvent.Failed(failure)
            } else {
                AssistantEvent.Done(answer.toString())
            }
        )
    }

    /**
     * The rules come first and the book second, and both are byte-identical across a thread, so the
     * provider can serve the expensive part from its prompt cache on every follow-up.
     */
    private fun requestFor(thread: AiThread, history: List<ChatMessage>, userText: String) = LlmRequest(
        system = listOfNotNull(
            PromptBlock(AiPrompts.rules(thread.kind)),
            PromptBlock(thread.contextText, cacheable = true),
            AiPrompts.selection(thread.quote)?.let { PromptBlock(it) },
            AiPrompts.position(thread.chapterTitle)?.let { PromptBlock(it) }
        ),
        messages = history + ChatMessage(ChatRole.User, userText),
        maxOutputTokens = maxTokensFor(thread.kind)
    )

    /** Streams into [answer] and returns what went wrong, or null. */
    private suspend fun FlowCollector<AssistantEvent>.collectAnswer(
        client: LlmClient,
        request: LlmRequest,
        answer: StringBuilder
    ): AppError? = try {
        client.stream(request).collect { event ->
            when (event) {
                is LlmEvent.Delta -> {
                    answer.append(event.text)
                    emit(AssistantEvent.Delta(answer.toString()))
                }

                is LlmEvent.Done -> Timber.d(
                    "AI answer: %d in (%d cached), %d out",
                    event.inputTokens ?: 0,
                    event.cachedInputTokens ?: 0,
                    event.outputTokens ?: 0
                )
            }
        }
        null
    } catch (llm: LlmException) {
        llm.error
    } catch (io: IOException) {
        AppError.Network(io.message ?: "The connection dropped.", io)
    }

    override suspend fun reusableRecap(book: Book, coveredFraction: Float?): AiThread? {
        val recap = threadsRepository.latestRecap(book.id) ?: return null
        val stored = recap.coveredFraction ?: return null
        val current = coveredFraction ?: return null
        val unchanged = kotlin.math.abs(stored - current) <= AiThread.RECAP_REUSE_EPSILON
        return recap.takeIf { unchanged }
    }

    /** Keeps a partial answer so the reader can see what arrived before a failure. */
    private suspend fun finish(thread: AiThread, answer: String, failure: AppError?) {
        if (answer.isNotEmpty()) {
            threadsRepository.addMessage(
                AiMessage(
                    id = AiMessage.UNSAVED_ID,
                    threadId = thread.id,
                    role = AiRole.Assistant,
                    text = answer,
                    createdAt = Instant.now()
                )
            )
        }
        if (failure != null) Timber.w("AI request failed: %s", failure.message)
        threadsRepository.touch(thread.copy(updatedAt = Instant.now()))
    }

    private fun userMessage(thread: AiThread, text: String) = AiMessage(
        id = AiMessage.UNSAVED_ID,
        threadId = thread.id,
        role = AiRole.User,
        text = text,
        createdAt = Instant.now()
    )

    private fun AiMessage.toChatMessage() = ChatMessage(
        role = if (role == AiRole.User) ChatRole.User else ChatRole.Assistant,
        text = text
    )

    private fun titleFor(kind: AiThreadKind, quote: String?): String = when (kind) {
        AiThreadKind.Recap -> RECAP_TITLE
        else -> quote?.trim()?.take(TITLE_CHARS)?.ifEmpty { null } ?: kind.name
    }

    private fun maxTokensFor(kind: AiThreadKind): Int = when (kind) {
        AiThreadKind.Recap -> RECAP_MAX_TOKENS
        else -> ANSWER_MAX_TOKENS
    }

    private companion object {
        const val NO_PROVIDER = "No AI provider is set up yet. Add one in Settings."
        const val RECAP_TITLE = "Where was I?"
        const val TITLE_CHARS = 80
        const val ANSWER_MAX_TOKENS = 700
        const val RECAP_MAX_TOKENS = 600
    }
}
