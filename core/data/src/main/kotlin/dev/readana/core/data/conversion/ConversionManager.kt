package dev.readana.core.data.conversion

import dev.readana.core.common.coroutines.ApplicationScope
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.model.Book
import dev.readana.core.model.ConversionReport
import dev.readana.engine.pdf2epub.ConversionProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happened to one conversion, for the UI to report. */
sealed interface ConversionEvent {
    data class Succeeded(val book: Book, val report: ConversionReport?) : ConversionEvent

    data class Failed(val bookId: String, val error: AppError) : ConversionEvent
}

/**
 * Runs conversions outside any screen's lifetime, one at a time. PDFBox plus bitmap decoding is
 * memory-hungry enough that two books at once is a real risk on the tablet.
 */
interface ConversionManager {
    /** Progress per book id; a book disappears from the map when its conversion ends. */
    val active: StateFlow<Map<String, ConversionProgress>>

    val events: SharedFlow<ConversionEvent>

    fun enqueue(bookId: String)

    /** For the download worker, which reports `Converting` until this returns. */
    suspend fun convertAndWait(bookId: String): AppResult<Book>
}

@Singleton
class DefaultConversionManager @Inject constructor(
    private val converter: BookConverter,
    @ApplicationScope private val scope: CoroutineScope
) : ConversionManager {
    private val _active = MutableStateFlow<Map<String, ConversionProgress>>(emptyMap())
    override val active: StateFlow<Map<String, ConversionProgress>> = _active.asStateFlow()

    private val _events = MutableSharedFlow<ConversionEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<ConversionEvent> = _events.asSharedFlow()

    private val queue = Channel<Job>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in queue) {
                runJob(job)
            }
        }
    }

    override fun enqueue(bookId: String) {
        queue.trySend(Job(bookId, result = null))
    }

    override suspend fun convertAndWait(bookId: String): AppResult<Book> {
        val result = CompletableDeferred<AppResult<Book>>()
        queue.send(Job(bookId, result))
        return result.await()
    }

    private suspend fun runJob(job: Job) {
        _active.update { it + (job.bookId to ConversionProgress(STARTING_PHASE)) }
        val outcome = try {
            converter.convert(job.bookId) { progress ->
                _active.update { it + (job.bookId to progress) }
            }
        } finally {
            _active.update { it - job.bookId }
        }
        _events.emit(outcome.toEvent(job.bookId))
        job.result?.complete(outcome)
    }

    private fun AppResult<Book>.toEvent(bookId: String): ConversionEvent = when (this) {
        is AppResult.Failure -> ConversionEvent.Failed(bookId, error)
        is AppResult.Success -> ConversionEvent.Succeeded(value, value.conversion?.report)
    }

    private class Job(val bookId: String, val result: CompletableDeferred<AppResult<Book>>?)

    private companion object {
        const val EVENT_BUFFER = 16
        val STARTING_PHASE = dev.readana.engine.pdf2epub.ConversionPhase.Extracting
    }
}
