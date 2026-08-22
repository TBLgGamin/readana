package dev.readana.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.downloads.BookDownloadManager
import dev.readana.core.data.downloads.RetryOutcome
import dev.readana.core.data.messages.MessageBus
import dev.readana.core.data.messages.UiMessage
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.model.BrowserDownload
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which slice of the queue the list shows.
 *
 * There is no "done" slice: a finished download drops out of the queue the moment its book reaches
 * the library, which is where a reader would look for it anyway.
 */
enum class DownloadsFilter { All, Active, Failed }

/** How many rows fall into each slice, for the filter chips. */
data class DownloadsTally(val all: Int = 0, val active: Int = 0, val failed: Int = 0)

/**
 * The one-line answer to how the queue as a whole is doing.
 *
 * Deliberately numbers only. The bars belong to the rows; a second bar totalling them would say
 * nothing the rows have not already said, in the place the eye looks first.
 */
data class QueueSummary(
    val activeCount: Int,
    val bytesDownloaded: Long,
    /** Null when any active job has not said how big it is, which makes the total meaningless. */
    val totalBytes: Long?,
    val bytesPerSecond: Long?,
    val etaMillis: Long?
)

data class DownloadsUiState(
    val isLoading: Boolean = true,
    /** Already filtered and ordered; the list draws exactly this. */
    val items: List<QueueItem> = emptyList(),
    val tally: DownloadsTally = DownloadsTally(),
    val filter: DownloadsFilter = DownloadsFilter.All,
    val summary: QueueSummary? = null,
    /** True when there is nothing at all, as opposed to a filter matching nothing. */
    val queueIsEmpty: Boolean = false,
    val effect: DownloadsEffect? = null
) {
    /** Whether the bulk actions in the app bar have anything to act on. */
    val hasFailed: Boolean get() = tally.failed > 0
}

/** A one-shot navigation request. */
sealed interface DownloadsEffect {
    data class OpenBrowser(val download: BrowserDownload) : DownloadsEffect
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: BookDownloadManager,
    private val messages: MessageBus,
    conversionManager: ConversionManager,
    libraryRepository: LibraryRepository
) : ViewModel() {
    private val rates = DownloadRates()
    private val jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    private val conversions = MutableStateFlow<List<ActiveConversion>>(emptyList())
    private val filter = MutableStateFlow(DownloadsFilter.All)
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(conversionManager.active, libraryRepository.books) { active, books ->
                active.map { (bookId, progress) ->
                    val book = books.firstOrNull { it.id == bookId }
                    ActiveConversion(
                        bookId = bookId,
                        title = book?.title.orEmpty(),
                        coverUri = book?.coverUri,
                        phase = progress.phase,
                        currentPage = progress.currentPage,
                        totalPages = progress.totalPages
                    )
                }
            }.collect { conversions.value = it }
        }
        viewModelScope.launch {
            combine(downloadManager.jobs, conversions, filter, ::Queue).collect { apply(it) }
        }
    }

    fun onFilterChange(value: DownloadsFilter) = filter.update { value }

    fun pause(jobId: String) {
        viewModelScope.launch { downloadManager.pause(jobId) }
    }

    fun resume(jobId: String) {
        viewModelScope.launch { downloadManager.resume(jobId) }
    }

    fun cancel(jobId: String) {
        viewModelScope.launch { downloadManager.cancel(jobId) }
    }

    fun dismiss(jobId: String) {
        viewModelScope.launch { downloadManager.dismiss(jobId) }
    }

    fun retry(jobId: String) {
        viewModelScope.launch { retryOne(jobId) }
    }

    /**
     * Retries everything that stopped short.
     *
     * A job that turns out to need the browser ends the sweep there: the browser can only be handed
     * one page at a time, and queueing several would leave the rest waiting behind a screen the
     * user did not ask for.
     */
    fun retryAll() {
        viewModelScope.launch {
            val stalled = jobs.value.filter { it.isStalled }
            var requeued = 0
            for (job in stalled) {
                if (!retryOne(job.id)) break
                requeued++
            }
            if (requeued > 0) {
                messages.show(
                    UiMessage.quantity(R.plurals.feature_downloads_message_retrying, requeued, requeued)
                )
            }
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            val count = jobs.value.count { it.isStalled }
            downloadManager.dismissFinished()
            messages.show(UiMessage.quantity(R.plurals.feature_downloads_message_cleared, count, count))
        }
    }

    fun consumeEffect() = _uiState.update { it.copy(effect = null) }

    /** Returns whether a sweep can carry on: a browser hand-off or a failure stops it. */
    private suspend fun retryOne(jobId: String): Boolean = when (
        val outcome = downloadManager.retry(jobId)
    ) {
        RetryOutcome.Requeued -> true

        is RetryOutcome.NeedsBrowser -> {
            _uiState.update { it.copy(effect = DownloadsEffect.OpenBrowser(outcome.download)) }
            false
        }

        is RetryOutcome.Failed -> {
            messages.show(
                UiMessage.text(R.string.feature_downloads_message_retry_failed, outcome.error.message)
            )
            false
        }
    }

    private fun apply(queue: Queue) {
        jobs.value = queue.jobs
        val ordered = queue.jobs.sortedWith(QueueOrder)
        val measured = rates.update(ordered)
        val active = ordered.filter { it.isActive }
        // A conversion that came out of a download belongs to that download's card, as a later
        // step of the same book. Only one the reader started from their library stands alone.
        val byBook = queue.conversions.associateBy { it.bookId }
        val downloads = ordered.map { job ->
            QueueItem.Download(
                job = job,
                rate = measured[job.id],
                conversion = job.bookId?.let(byBook::get)
            )
        }
        val attached = downloads.mapNotNullTo(mutableSetOf()) { it.conversion?.bookId }
        val standalone = queue.conversions.filterNot { it.bookId in attached }
        // Standalone conversions lead: they are the only work here that cannot be paused, retried
        // or dropped, so putting them anywhere but the top buries the row the reader can do least
        // about.
        val shownConversions = if (queue.filter.acceptsConversions) standalone else emptyList()
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                filter = queue.filter,
                items = shownConversions.map { QueueItem.Conversion(it) } +
                    downloads.filter { queue.filter.accepts(it.job) },
                tally = DownloadsTally(
                    all = ordered.size + standalone.size,
                    active = ordered.count { it.isPending } + standalone.size,
                    failed = ordered.count { it.isStalled }
                ),
                summary = summarise(active, measured),
                queueIsEmpty = ordered.isEmpty() && standalone.isEmpty()
            )
        }
    }

    private fun summarise(active: List<DownloadJob>, measured: Map<String, DownloadRate>): QueueSummary? {
        if (active.isEmpty()) return null
        // One job of unknown size poisons the total, so the line just says how much has arrived.
        val total = active.map { it.totalBytes }
            .takeIf { sizes -> sizes.all { it != null } }
            ?.filterNotNull()
            ?.sum()
        val speed = active.mapNotNull { measured[it.id]?.bytesPerSecond }
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val downloaded = active.sumOf { it.bytesDownloaded }
        val remaining = total?.let { (it - downloaded).coerceAtLeast(0) }?.takeIf { it > 0 }
        val movingAt = speed?.takeIf { it > 0 }
        return QueueSummary(
            activeCount = active.size,
            bytesDownloaded = downloaded,
            totalBytes = total,
            bytesPerSecond = speed,
            // Across the whole queue rather than per job: the bandwidth is shared, so the last file
            // finishing is what "everything is done" actually means.
            etaMillis = if (remaining != null && movingAt != null) {
                remaining * MILLIS_PER_SECOND / movingAt
            } else {
                null
            }
        )
    }

    private data class Queue(
        val jobs: List<DownloadJob>,
        val conversions: List<ActiveConversion>,
        val filter: DownloadsFilter
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1000L

        /** A conversion is work in flight, so it belongs to every slice that shows work in flight. */
        val DownloadsFilter.acceptsConversions: Boolean
            get() = this == DownloadsFilter.All || this == DownloadsFilter.Active

        fun DownloadsFilter.accepts(job: DownloadJob): Boolean = when (this) {
            DownloadsFilter.All -> true
            DownloadsFilter.Active -> job.isPending
            DownloadsFilter.Failed -> job.isStalled
        }

        /**
         * What is happening now, then what needs a decision, then what is done - and inside each
         * group the oldest first, because that is the order the queue works through them.
         */
        val QueueOrder = compareBy<DownloadJob>({ rank(it.status) }, { it.createdAt })

        @Suppress("MagicNumber")
        fun rank(status: DownloadStatus): Int = when (status) {
            DownloadStatus.Resolving, DownloadStatus.Downloading, DownloadStatus.Converting -> 0
            DownloadStatus.Queued -> 1
            DownloadStatus.Paused -> 2
            DownloadStatus.Failed -> 3
            DownloadStatus.Cancelled -> 4
            DownloadStatus.Completed -> 5
        }
    }
}
