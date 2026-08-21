package dev.readana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.ai.client.ReadingContext
import dev.readana.ai.client.ReadingContextBuilder
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.ai.AiAssistant
import dev.readana.core.data.ai.AssistantEvent
import dev.readana.core.data.repository.AiThreadsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiRole
import dev.readana.core.model.AiSettings
import dev.readana.core.model.AiThread
import dev.readana.core.model.AiThreadKind
import dev.readana.core.model.Book
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

/** One turn of a conversation, as the sheet shows it. */
internal data class AssistantMessage(val fromReader: Boolean, val text: String)

/** Where a conversation currently is. */
internal enum class AssistantPhase {
    Idle,

    /** Reading back through the book to gather what the reader has already been through. */
    Gathering,

    Streaming
}

internal data class ThreadItem(val id: Long, val kind: AiThreadKind, val title: String, val updatedAt: Instant)

internal data class AssistantUiState(
    val visible: Boolean = false,
    val kind: AiThreadKind = AiThreadKind.Ask,
    val quote: String? = null,
    val threadId: Long? = null,
    val messages: List<AssistantMessage> = emptyList(),
    /** The answer as it arrives, before it is stored as a message. */
    val streaming: String? = null,
    val phase: AssistantPhase = AssistantPhase.Idle,
    val error: AppError? = null,
    /** How much of the book the conversation was allowed to see. */
    val coveredFraction: Float? = null,
    val threads: List<ThreadItem> = emptyList(),
    /** Whether the "catch me up" offer is showing over the page. */
    val recapOffered: Boolean = false,
    /** How long the reader has been away, for the wording of that offer. */
    val daysAway: Int = 0,
    /** Whether the assistant can be used here at all. */
    val enabled: Boolean = false
)

/**
 * Drives the reading assistant: Ask, Explain and the recap.
 *
 * Separate from [ReaderViewModel], which is already at the point where adding more would make it
 * hard to follow; the two share only the session store and the book id.
 */
@HiltViewModel
internal class ReaderAssistantViewModel @Inject constructor(
    private val assistant: AiAssistant,
    private val threadsRepository: AiThreadsRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val sessionStore: ReaderSessionStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var book: Book? = null
    private var thread: AiThread? = null
    private var replyJob: Job? = null
    private var threadsJob: Job? = null
    private var lastQuestion: String? = null
    private var hasText: Boolean = false
    private var providerReady: Boolean = false
    private var availabilityJob: Job? = null

    /**
     * Called by the reader once it knows which book is open, and again when it learns whether the
     * book's text can be read — which is only true after the publication has finished opening.
     */
    fun attach(bookId: String, hasText: Boolean) {
        val becameReadable = hasText && !this.hasText
        this.hasText = hasText
        refreshAvailability()
        if (book?.id == bookId) {
            // The book was already known; its text has only just become readable.
            if (becameReadable) viewModelScope.launch { book?.let { offerRecapIfDue(it) } }
            return
        }
        viewModelScope.launch {
            val opened = libraryRepository.getBook(bookId) ?: return@launch
            book = opened
            observeThreads(bookId)
            observeAvailability()
            offerRecapIfDue(opened)
        }
    }

    /** Opens the assistant on a passage and waits for the reader's question. */
    fun ask(selection: ReaderSelection, progression: Float?, chapterTitle: String?) {
        start(AiThreadKind.Ask, selection, progression, chapterTitle, askImmediately = false)
    }

    /** Opens the assistant on a passage and asks for an explanation right away. */
    fun explain(selection: ReaderSelection, progression: Float?, chapterTitle: String?) {
        start(AiThreadKind.Explain, selection, progression, chapterTitle, askImmediately = true)
    }

    /** Answers the "catch me up" offer: reuses a recent recap, or writes a new one. */
    fun requestRecap(anchor: suspend () -> Locator?, progression: Float?, chapterTitle: String?) {
        val current = book ?: return
        replyJob?.cancel()
        _uiState.update {
            it.copy(
                visible = true,
                kind = AiThreadKind.Recap,
                quote = null,
                threadId = null,
                messages = emptyList(),
                streaming = null,
                phase = AssistantPhase.Gathering,
                error = null,
                recapOffered = false,
                coveredFraction = progression
            )
        }
        replyJob = viewModelScope.launch {
            val existing = assistant.reusableRecap(current, progression)
            if (existing != null) {
                open(existing)
                return@launch
            }
            val context = gather(anchor(), selectionText = null)
            startAndReply(current, AiThreadKind.Recap, null, context, chapterTitle, progression, null)
        }
    }

    fun dismissRecapOffer() = _uiState.update { it.copy(recapOffered = false) }

    /** Sends a follow-up (or the first question of an Ask thread). */
    fun send(question: String) {
        val open = thread ?: return
        val text = question.trim().ifEmpty { return }
        lastQuestion = text
        replyJob?.cancel()
        replyJob = viewModelScope.launch { stream(open, text) }
    }

    fun retry() {
        val open = thread ?: return
        replyJob?.cancel()
        replyJob = viewModelScope.launch { stream(open, lastQuestion) }
    }

    fun openThread(threadId: Long) {
        replyJob?.cancel()
        viewModelScope.launch { threadsRepository.find(threadId)?.let { open(it) } }
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            threadsRepository.remove(threadId)
            if (thread?.id == threadId) dismiss()
        }
    }

    fun dismiss() {
        replyJob?.cancel()
        thread = null
        lastQuestion = null
        _uiState.update {
            it.copy(
                visible = false,
                threadId = null,
                messages = emptyList(),
                streaming = null,
                phase = AssistantPhase.Idle,
                error = null
            )
        }
    }

    private fun start(
        kind: AiThreadKind,
        selection: ReaderSelection,
        progression: Float?,
        chapterTitle: String?,
        askImmediately: Boolean
    ) {
        val current = book ?: return
        replyJob?.cancel()
        _uiState.update {
            it.copy(
                visible = true,
                kind = kind,
                quote = selection.text,
                threadId = null,
                messages = emptyList(),
                streaming = null,
                phase = AssistantPhase.Gathering,
                error = null,
                coveredFraction = progression
            )
        }
        replyJob = viewModelScope.launch {
            val context = gather(selection.anchor ?: selection.locator, selection.text)
            startAndReply(
                book = current,
                kind = kind,
                quote = selection.text,
                context = context,
                chapterTitle = chapterTitle,
                progression = progression,
                question = null,
                replyNow = askImmediately
            )
        }
    }

    @Suppress("LongParameterList")
    private suspend fun startAndReply(
        book: Book,
        kind: AiThreadKind,
        quote: String?,
        context: ReadingContext,
        chapterTitle: String?,
        progression: Float?,
        question: String?,
        replyNow: Boolean = true
    ) {
        when (
            val created = assistant.startThread(
                book = book,
                kind = kind,
                quote = quote,
                context = context,
                chapterTitle = chapterTitle,
                coveredFraction = progression
            )
        ) {
            is AppResult.Failure -> _uiState.update {
                it.copy(phase = AssistantPhase.Idle, error = created.error)
            }

            is AppResult.Success -> {
                thread = created.value
                _uiState.update {
                    it.copy(threadId = created.value.id, phase = AssistantPhase.Idle)
                }
                if (replyNow) stream(created.value, question)
            }
        }
    }

    /** Reads the book backwards from the anchor, never past the selected passage. */
    private suspend fun gather(anchor: Locator?, selectionText: String?): ReadingContext {
        val walker = sessionStore.session?.textWalker?.invoke(anchor)
            ?: return ReadingContext(text = "", truncatedAtStart = false)
        val budget = settingsRepository.aiSettings.first().contextSize.maxChars
        return ReadingContextBuilder(budget).build(walker, selectionText)
    }

    private suspend fun stream(thread: AiThread, question: String?) {
        _uiState.update {
            it.copy(phase = AssistantPhase.Streaming, streaming = "", error = null)
        }
        if (!question.isNullOrBlank()) {
            _uiState.update {
                it.copy(messages = it.messages + AssistantMessage(fromReader = true, text = question))
            }
        }
        assistant.reply(thread, question).collect { event ->
            when (event) {
                is AssistantEvent.Delta -> _uiState.update { it.copy(streaming = event.text) }

                is AssistantEvent.Done -> _uiState.update {
                    it.copy(
                        messages = it.messages + AssistantMessage(fromReader = false, text = event.text),
                        streaming = null,
                        phase = AssistantPhase.Idle
                    )
                }

                is AssistantEvent.Failed -> _uiState.update {
                    it.copy(streaming = null, phase = AssistantPhase.Idle, error = event.error)
                }
            }
        }
    }

    private suspend fun open(existing: AiThread) {
        thread = existing
        val stored = threadsRepository.messagesOf(existing.id)
        _uiState.update {
            it.copy(
                visible = true,
                kind = existing.kind,
                quote = existing.quote,
                threadId = existing.id,
                messages = stored.map { message -> message.toItem() },
                streaming = null,
                phase = AssistantPhase.Idle,
                error = null,
                coveredFraction = existing.coveredFraction,
                recapOffered = false
            )
        }
    }

    /** A key pasted (or cleared) in Settings takes effect without reopening the book. */
    private fun observeAvailability() {
        availabilityJob?.cancel()
        availabilityJob = viewModelScope.launch {
            settingsRepository.aiSettings.collect { settings ->
                providerReady = settings.isConfigured
                refreshAvailability()
            }
        }
    }

    /**
     * The menu items follow the provider alone. Gating them on the text service as well made the
     * assistant invisible whenever that check was wrong, with nothing on screen to say why; a book
     * whose text cannot be read now says so in the sheet instead.
     */
    private fun refreshAvailability() {
        _uiState.update { it.copy(enabled = providerReady) }
    }

    private fun observeThreads(bookId: String) {
        threadsJob?.cancel()
        threadsJob = viewModelScope.launch {
            threadsRepository.observeForBook(bookId).collect { threads ->
                _uiState.update { state ->
                    state.copy(
                        threads = threads.map {
                            ThreadItem(it.id, it.kind, it.title, it.updatedAt)
                        }
                    )
                }
            }
        }
    }

    /**
     * The book's stored `lastOpenedAt` is still the *previous* session at this point: the reader
     * only rewrites it once the navigator reports a locator.
     */
    private suspend fun offerRecapIfDue(book: Book) {
        val settings = settingsRepository.aiSettings.first()
        if (!settings.isConfigured || !settings.recapEnabled || !hasText) return
        val lastOpened = book.lastOpenedAt ?: return
        val progress = book.progress?.fraction ?: return
        if (progress < MIN_PROGRESS_FOR_RECAP) return
        val daysAway = Duration.between(lastOpened, Instant.now()).toDays()
        if (daysAway < settings.recapAfterDays) return
        _uiState.update { it.copy(recapOffered = true, daysAway = daysAway.toInt()) }
    }

    private fun AiMessage.toItem() = AssistantMessage(
        fromReader = role == AiRole.User,
        text = text
    )

    private companion object {
        /** Nothing to recap in the first pages. */
        const val MIN_PROGRESS_FOR_RECAP = 0.02f
    }
}
