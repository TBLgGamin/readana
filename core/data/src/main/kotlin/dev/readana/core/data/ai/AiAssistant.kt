package dev.readana.core.data.ai

import dev.readana.ai.client.ChatMessage
import dev.readana.ai.client.ChatRole
import dev.readana.ai.client.ImageAttachment
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
import dev.readana.core.model.AiFigure
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

    /**
     * [partialText] is whatever had been written before it went wrong. Providers do fail
     * mid-answer — an overloaded model sends an error event after a sentence or two — and quietly
     * keeping the half-answer made that look like the assistant simply stopping for no reason.
     */
    data class Failed(val error: AppError, val partialText: String = "") : AssistantEvent
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
        coveredFraction: Float?,
        figures: List<AiFigure> = emptyList()
    ): AppResult<AiThread>

    /**
     * Sends [question] (null for the opening turn of an Explain or Recap) and streams the answer.
     * [images] are the book's own illustrations, loaded by the reader, that the model may point at.
     */
    fun reply(thread: AiThread, question: String?, images: List<ImageAttachment> = emptyList()): Flow<AssistantEvent>

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
        coveredFraction: Float?,
        figures: List<AiFigure>
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
            figures = figures,
            createdAt = now,
            updatedAt = now
        )
        return threadsRepository.add(thread).asSuccess()
    }

    override fun reply(thread: AiThread, question: String?, images: List<ImageAttachment>): Flow<AssistantEvent> =
        flow {
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

            var request = requestFor(thread, history, userText, images, settings.illustrateFromWeb)
            val answer = StringBuilder()
            val stop = StringBuilder()
            var failure = collectAnswer(client, request, answer, stop)

            // Plenty of models take text only, and which ones is not something the reader should
            // have to know. A refusal over the pictures means asking again without them: an answer
            // that cannot point at a figure beats no answer at all.
            if (failure.isImageRefusal() && answer.isEmpty() && request.images.isNotEmpty()) {
                Timber.d("Provider would not take %d image(s); asking again without them", request.images.size)
                stop.clear()
                request = requestFor(thread, history, userText, emptyList(), settings.illustrateFromWeb)
                failure = collectAnswer(client, request, answer, stop)
            }

            // A small or older model refuses a ceiling above what it can produce. Rather than make
            // every reader discover their model's limit by hand, ask again within a safe one.
            if (failure.isBudgetRefusal() && answer.isEmpty()) {
                Timber.d("Provider refused a %d-token ceiling; retrying smaller", request.maxOutputTokens)
                stop.clear()
                failure = collectAnswer(
                    client = client,
                    request = request.copy(maxOutputTokens = MODEST_MAX_TOKENS),
                    answer = answer,
                    stop = stop
                )
            }

            finish(thread, answer.toString(), failure)
            // An answer with nothing in it is a failure whatever the provider calls it: a model
            // with adaptive thinking can spend the entire output budget before writing a word.
            val outcome = failure ?: emptyAnswerError(answer, stop.toString())
            emit(
                if (outcome != null) {
                    AssistantEvent.Failed(outcome, answer.toString())
                } else {
                    AssistantEvent.Done(answer.toString())
                }
            )
        }

    /**
     * The rules come first and the book second, and both are byte-identical across a thread, so the
     * provider can serve the expensive part from its prompt cache on every follow-up.
     */
    private fun requestFor(
        thread: AiThread,
        history: List<ChatMessage>,
        userText: String,
        images: List<ImageAttachment>,
        mayIllustrate: Boolean
    ): LlmRequest {
        // Only the figures whose bytes are actually going out get described. Listing one the model
        // was never handed invites a marker pointing at a picture that is not there.
        val attached = images.filter { it.isSendable }
        val described = thread.figures.filter { figure -> attached.any { it.id == figure.id } }
        return LlmRequest(
            system = listOfNotNull(
                PromptBlock(AiPrompts.rules(thread.kind)),
                PromptBlock(thread.contextText, cacheable = true),
                AiPrompts.selection(thread.quote)?.let { PromptBlock(it) },
                AiPrompts.pictures(described, mayIllustrate)?.let { PromptBlock(it) },
                AiPrompts.position(thread.chapterTitle)?.let { PromptBlock(it) }
            ),
            messages = history + ChatMessage(ChatRole.User, userText),
            maxOutputTokens = maxTokensFor(thread.kind),
            images = attached
        )
    }

    /** Streams into [answer] and returns what went wrong, or null. */
    private suspend fun FlowCollector<AssistantEvent>.collectAnswer(
        client: LlmClient,
        request: LlmRequest,
        answer: StringBuilder,
        stop: StringBuilder
    ): AppError? = try {
        client.stream(request).collect { event ->
            when (event) {
                is LlmEvent.Delta -> {
                    answer.append(event.text)
                    emit(AssistantEvent.Delta(answer.toString()))
                }

                is LlmEvent.Done -> {
                    event.stopReason?.let { stop.append(it) }
                    Timber.d(
                        "AI answer: %d in (%d cached), %d out, stop=%s",
                        event.inputTokens ?: 0,
                        event.cachedInputTokens ?: 0,
                        event.outputTokens ?: 0,
                        event.stopReason ?: "?"
                    )
                }
            }
        }
        null
    } catch (llm: LlmException) {
        llm.error
    } catch (io: IOException) {
        AppError.Network(io.message ?: "The connection dropped.", io)
    }

    /**
     * Whether the provider baulked at the pictures rather than at the question.
     *
     * Every dialect words it differently — "does not support image input", "image_url is not
     * supported", "no endpoints support image input" — so the check is the shape of the failure
     * plus a mention of pictures. A body that is simply too large counts on its own: the images
     * are the only part of the request big enough to be the cause.
     */
    private fun AppError?.isImageRefusal(): Boolean {
        val http = this as? AppError.Http ?: return false
        if (http.statusCode == PAYLOAD_TOO_LARGE) return true
        if (http.statusCode !in IMAGE_REFUSAL_STATUSES) return false
        return IMAGE_COMPLAINTS.any { http.message.contains(it, ignoreCase = true) }
    }

    /** A 400 that names the output ceiling; every dialect words it differently. */
    private fun AppError?.isBudgetRefusal(): Boolean {
        val http = this as? AppError.Http ?: return false
        if (http.statusCode != BAD_REQUEST) return false
        return BUDGET_COMPLAINTS.any { http.message.contains(it, ignoreCase = true) }
    }

    private fun emptyAnswerError(answer: StringBuilder, stopReason: String): AppError? {
        if (answer.isNotBlank()) return null
        Timber.w("The provider answered with nothing; stop reason %s", stopReason.ifEmpty { "?" })
        return if (stopReason in BUDGET_STOPS) {
            AppError.Unexpected(OUT_OF_BUDGET)
        } else {
            AppError.Unexpected(NOTHING_WRITTEN)
        }
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
        const val OUT_OF_BUDGET =
            "The model used up its whole answer budget before writing anything. Ask again, " +
                "or put the question more plainly."
        const val NOTHING_WRITTEN = "The provider sent an empty answer."
        val BUDGET_STOPS = setOf("max_tokens", "length")
        const val RECAP_TITLE = "Where was I?"
        const val TITLE_CHARS = 80

        // Deliberately far above what any answer needs. Unused budget costs nothing — providers
        // bill what is written — while too small a ceiling is charged for in full and returns
        // nothing, because a model with adaptive thinking spends the same budget on thinking.
        const val ANSWER_MAX_TOKENS = 32_000
        const val RECAP_MAX_TOKENS = 32_000

        /** Within the reach of an older model, or one running on the reader's own machine. */
        const val MODEST_MAX_TOKENS = 4_096
        const val BAD_REQUEST = 400
        const val PAYLOAD_TOO_LARGE = 413
        val BUDGET_COMPLAINTS = listOf("max_tokens", "max_completion_tokens", "max output")

        /** Bad request, unsupported media type, and unprocessable content. */
        val IMAGE_REFUSAL_STATUSES = setOf(400, 415, 422)
        val IMAGE_COMPLAINTS = listOf("image", "vision", "multimodal", "multi-modal")
    }
}
