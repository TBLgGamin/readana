package dev.readana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.ai.client.ImageAttachment
import dev.readana.ai.client.ReadingContext
import dev.readana.ai.client.ReadingContextBuilder
import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.ai.AiAssistant
import dev.readana.core.data.ai.AssistantEvent
import dev.readana.core.data.ai.PictureSearch
import dev.readana.core.data.repository.AiThreadsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.AiFigure
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiRole
import dev.readana.core.model.AiSettings
import dev.readana.core.model.AiThread
import dev.readana.core.model.AiThreadKind
import dev.readana.core.model.Book
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val enabled: Boolean = false,
    /** Pictures an answer may point at, by the marker it writes: [figure:1], [image: …]. */
    val pictures: Map<String, AssistantPicture> = emptyMap()
)

/** A picture the chat can show: either a plate from the book or one looked up for a subject. */
internal data class AssistantPicture(
    val bytes: ByteArray? = null,
    val url: String? = null,
    val caption: String? = null,
    val sourceUrl: String? = null
) {
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

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
    private val sessionStore: ReaderSessionStore,
    private val pictures: PictureSearch,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var book: Book? = null
    private var thread: AiThread? = null
    private var replyJob: Job? = null
    private var threadsJob: Job? = null
    private var lastQuestion: String? = null
    private var attachments: List<ImageAttachment> = emptyList()
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
                coveredFraction = progression,
                figures = context.figures.mapIndexed { index, figure ->
                    AiFigure(
                        id = FIGURE_PREFIX + (index + 1),
                        href = figure.href,
                        caption = figure.caption,
                        mediaType = figure.mediaType
                    )
                }
            )
        ) {
            is AppResult.Failure -> _uiState.update {
                it.copy(phase = AssistantPhase.Idle, error = created.error)
            }

            is AppResult.Success -> {
                thread = created.value
                attachments = loadFigures(created.value)
                _uiState.update { state ->
                    state.copy(
                        threadId = created.value.id,
                        phase = AssistantPhase.Idle,
                        pictures = created.value.figurePictures(attachments)
                    )
                }
                if (replyNow) stream(created.value, question)
            }
        }
    }

    /**
     * Reads the book backwards from the anchor, never past the selected passage.
     *
     * Walking the publication parses XHTML resources off disk, which must not happen on the main
     * thread: Explain streams the moment the walk finishes, so a blocked main thread showed a
     * spinner that never went away. (Ask hid the same stall behind the time spent typing.)
     */
    private suspend fun gather(anchor: Locator?, selectionText: String?): ReadingContext = withContext(ioDispatcher) {
        val walker = sessionStore.session?.textWalker?.invoke(anchor)
            ?: return@withContext ReadingContext(text = "", truncatedAtStart = false)
        val budget = settingsRepository.aiSettings.first().contextSize.maxChars
        ReadingContextBuilder(budget).build(walker, selectionText)
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
        assistant.reply(thread, question, attachments).collect { event ->
            when (event) {
                is AssistantEvent.Delta -> _uiState.update { it.copy(streaming = event.text) }

                is AssistantEvent.Done -> {
                    _uiState.update {
                        it.copy(
                            messages = it.messages + AssistantMessage(fromReader = false, text = event.text),
                            streaming = null,
                            phase = AssistantPhase.Idle
                        )
                    }
                    illustrate(event.text)
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
        attachments = loadFigures(existing)
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
                recapOffered = false,
                pictures = existing.figurePictures(attachments)
            )
        }
        stored.filterNot { it.role == AiRole.User }.forEach { illustrate(it.text) }
    }

    /**
     * Reads the conversation's illustrations out of the book. Only their whereabouts are stored,
     * so a chat reopened weeks later still shows the plates its answers point at.
     */
    private suspend fun loadFigures(thread: AiThread): List<ImageAttachment> {
        if (thread.figures.isEmpty()) return emptyList()
        val session = sessionStore.session ?: return emptyList()
        return withContext(ioDispatcher) {
            thread.figures.mapNotNull { figure ->
                val bytes = session.loadFigure(figure.href) ?: return@mapNotNull null
                ImageAttachment(
                    id = figure.id,
                    mediaType = figure.mediaType ?: DEFAULT_IMAGE_TYPE,
                    bytes = bytes
                )
            }
        }
    }

    private fun AiThread.figurePictures(loaded: List<ImageAttachment>): Map<String, AssistantPicture> =
        figures.mapNotNull { figure ->
            val bytes = loaded.firstOrNull { it.id == figure.id }?.bytes ?: return@mapNotNull null
            figure.id to AssistantPicture(bytes = bytes, caption = figure.caption)
        }.toMap()

    /**
     * Looks up the subjects an answer asked to illustrate. The results are shown to the reader and
     * never sent back, so nothing the web knows about this book can reach the conversation.
     */
    private suspend fun illustrate(answer: String) {
        val settings = settingsRepository.aiSettings.first()
        if (!settings.illustrateFromWeb) return
        val wanted = IMAGE_MARKER.findAll(answer)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_LOOKED_UP)
            .toList()
        wanted.forEach { subject ->
            val marker = imageMarkerFor(subject)
            if (_uiState.value.pictures.containsKey(marker)) return@forEach
            val found = pictures.find(subject) ?: return@forEach
            _uiState.update { state ->
                state.copy(
                    pictures = state.pictures + (
                        marker to AssistantPicture(
                            url = found.imageUrl,
                            caption = found.title,
                            sourceUrl = found.pageUrl
                        )
                        )
                )
            }
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

        const val FIGURE_PREFIX = "figure:"
        const val DEFAULT_IMAGE_TYPE = "image/jpeg"

        /** One answer may ask for a couple of pictures; it may not ask for a gallery. */
        const val MAX_LOOKED_UP = 2

        val IMAGE_MARKER = Regex("""\[image:([^\]]{1,120})]""")
    }

    /** The key an [image: …] marker is stored under, matching what the sheet looks for. */
    private fun imageMarkerFor(subject: String) = "image:" + subject.lowercase()
}
