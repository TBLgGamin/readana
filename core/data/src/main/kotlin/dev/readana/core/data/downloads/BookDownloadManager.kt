package dev.readana.core.data.downloads

import dev.readana.core.common.coroutines.ApplicationScope
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.database.DownloadEntity
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.DownloadsRepository
import dev.readana.core.data.repository.LibraryRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BrowserDownload
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import dev.readana.source.api.BrowserLogin
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A download the in-app browser captured, ready to be streamed with its session cookies. */
data class CapturedDownload(
    val sourceId: String,
    val recordId: String,
    val title: String,
    val authors: List<String>,
    val format: BookFormat,
    val url: String,
    val headers: Map<String, String>,
    val expectedSizeBytes: Long? = null
)

/** What happened when the user asked for a book. */
sealed interface EnqueueOutcome {
    data class Enqueued(val job: DownloadJob, val fastDownloadsLeft: Int?) : EnqueueOutcome

    data class AlreadyInLibrary(val book: Book) : EnqueueOutcome

    data class AlreadyQueued(val job: DownloadJob) : EnqueueOutcome

    data class Failed(val error: AppError) : EnqueueOutcome
}

/** What a retry could do with a job. */
sealed interface RetryOutcome {
    data object Requeued : RetryOutcome

    /** Only the browser can finish this one; the row has been dropped. */
    data class NeedsBrowser(val download: BrowserDownload) : RetryOutcome

    data class Failed(val error: AppError) : RetryOutcome
}

/** The download queue: what the UI talks to. */
interface BookDownloadManager {
    val jobs: Flow<List<DownloadJob>>

    suspend fun enqueue(result: SearchResult): EnqueueOutcome

    suspend fun enqueueCaptured(capture: CapturedDownload): EnqueueOutcome

    /**
     * Queues the job again, re-asking the source first when the last attempt needed a browser —
     * a membership quota may have reset in the meantime.
     */
    suspend fun retry(jobId: String): RetryOutcome

    suspend fun cancel(jobId: String)

    /** Forgets a finished or failed row (and any partial file). */
    suspend fun dismiss(jobId: String)
}

@Singleton
class DefaultBookDownloadManager @Inject constructor(
    private val downloadsRepository: DownloadsRepository,
    private val libraryRepository: LibraryRepository,
    private val sources: BookSourceRegistry,
    private val settingsRepository: SettingsRepository,
    private val scheduler: DownloadScheduler,
    private val downloader: BookDownloader,
    @ApplicationScope private val scope: CoroutineScope
) : BookDownloadManager {
    override val jobs: Flow<List<DownloadJob>> = downloadsRepository.jobs

    init {
        // Partial files outlive their rows when the process dies mid-transfer.
        scope.launch {
            downloader.sweepOrphans(downloadsRepository.jobs.first().map { it.id }.toSet())
        }
    }

    override suspend fun enqueue(result: SearchResult): EnqueueOutcome {
        val sourceId = result.sourceId.value
        libraryRepository.findBySource(sourceId, result.recordId)?.let {
            return EnqueueOutcome.AlreadyInLibrary(it)
        }
        downloadsRepository.findActive(sourceId, result.recordId)?.let {
            return EnqueueOutcome.AlreadyQueued(it)
        }
        if (sources.byId(result.sourceId) == null) {
            return EnqueueOutcome.Failed(AppError.Unsupported("Unknown source $sourceId"))
        }

        // Working out where the file actually comes from can mean a browser check or a wait-list,
        // which is far too long to hold up a tap. The row goes in unresolved and the download
        // worker figures it out, under the foreground service that survives being backgrounded.
        val entity = newEntity(result)
        downloadsRepository.upsert(entity)
        scheduler.schedule(entity.id, uniqueName(sourceId, result.recordId))
        return EnqueueOutcome.Enqueued(entity.toJob(), fastDownloadsLeft = null)
    }

    override suspend fun enqueueCaptured(capture: CapturedDownload): EnqueueOutcome {
        libraryRepository.findBySource(capture.sourceId, capture.recordId)?.let {
            return EnqueueOutcome.AlreadyInLibrary(it)
        }
        downloadsRepository.findActive(capture.sourceId, capture.recordId)?.let {
            return EnqueueOutcome.AlreadyQueued(it)
        }
        val entity = DownloadEntity(
            id = UUID.randomUUID().toString(),
            sourceId = capture.sourceId,
            recordId = capture.recordId,
            title = capture.title,
            authors = capture.authors.joinToString("|"),
            format = capture.format.name,
            status = DownloadStatus.Queued.name,
            errorKind = null,
            errorMessage = null,
            bytesDownloaded = 0,
            totalBytes = capture.expectedSizeBytes,
            extrasJson = null,
            resolvedUrl = capture.url,
            // Present headers mark this as already resolved: the worker must not ask the source again.
            headersJson = capture.headers.encodeToJson() ?: "{}",
            browserUrl = null,
            bookId = null,
            createdAt = Instant.now().toEpochMilli(),
            updatedAt = Instant.now().toEpochMilli()
        )
        downloadsRepository.upsert(entity)
        scheduler.schedule(entity.id, uniqueName(capture.sourceId, capture.recordId))
        return EnqueueOutcome.Enqueued(entity.toJob(), null)
    }

    override suspend fun retry(jobId: String): RetryOutcome {
        val entity = downloadsRepository.getEntity(jobId)
            ?: return RetryOutcome.Failed(AppError.NotFound("No download $jobId"))
        val needsBrowser = entity.errorKind == DownloadErrorKind.RequiresBrowser.name ||
            entity.errorKind == DownloadErrorKind.QuotaExhausted.name
        // A browser-captured job has no source URL to re-resolve; its cookies are all it has.
        if (needsBrowser && entity.headersJson == null) {
            return retryThroughSource(entity)
        }
        requeue(entity.id, entity.sourceId, entity.recordId)
        return RetryOutcome.Requeued
    }

    private suspend fun retryThroughSource(entity: DownloadEntity): RetryOutcome {
        val source = sources.byId(SourceId(entity.sourceId))
            ?: return RetryOutcome.Failed(AppError.Unsupported("Unknown source ${entity.sourceId}"))
        val request = when (val resolved = source.resolveDownload(entity.toSearchResult())) {
            is AppResult.Failure -> return RetryOutcome.Failed(resolved.error)
            is AppResult.Success -> resolved.value
        }
        request.quota?.let { rememberQuota(it.remaining, it.perDay) }

        if (!request.requiresBrowser) {
            downloadsRepository.updateResolution(entity.id, request.url, total = request.expectedSizeBytes)
            requeue(entity.id, entity.sourceId, entity.recordId)
            return RetryOutcome.Requeued
        }
        // The browser takes it from here, so the failed row would only be noise.
        dismiss(entity.id)
        return RetryOutcome.NeedsBrowser(
            BrowserDownload(
                sourceId = entity.sourceId,
                recordId = entity.recordId,
                title = entity.title,
                authors = entity.authors.split('|').filter { it.isNotBlank() },
                format = BookFormat.valueOf(entity.format),
                url = request.url,
                loginUrl = request.browserLogin?.url,
                loginFormBody = request.browserLogin?.formBody
            )
        )
    }

    private suspend fun requeue(jobId: String, sourceId: String, recordId: String) {
        downloadsRepository.setStatus(jobId, DownloadStatus.Queued)
        scheduler.schedule(jobId, uniqueName(sourceId, recordId))
    }

    override suspend fun cancel(jobId: String) {
        val job = downloadsRepository.getJob(jobId) ?: return
        scheduler.cancel(uniqueName(job.sourceId, job.recordId))
        downloadsRepository.setStatus(jobId, DownloadStatus.Cancelled)
        downloader.discardPart(jobId)
    }

    override suspend fun dismiss(jobId: String) {
        val job = downloadsRepository.getJob(jobId) ?: return
        // Always, even for a job that already failed: it may still have work waiting on a retry
        // backoff, and leaving that behind would block the next attempt at the same book.
        scheduler.cancel(uniqueName(job.sourceId, job.recordId))
        downloader.discardPart(jobId)
        downloadsRepository.remove(jobId)
    }

    private suspend fun rememberQuota(remaining: Int?, perDay: Int?) {
        settingsRepository.updateSourceSettings {
            it.copy(annasDownloadsLeft = remaining, annasDownloadsPerDay = perDay)
        }
    }

    private fun newEntity(result: SearchResult) = DownloadEntity(
        id = UUID.randomUUID().toString(),
        sourceId = result.sourceId.value,
        recordId = result.recordId,
        title = result.title,
        authors = result.authors.joinToString("|"),
        format = (result.format ?: BookFormat.EPUB).name,
        status = DownloadStatus.Queued.name,
        errorKind = null,
        errorMessage = null,
        bytesDownloaded = 0,
        totalBytes = result.sizeBytes,
        extrasJson = result.extras.encodeToJson(),
        // No URL yet: a null one is what tells the worker to ask the source.
        resolvedUrl = null,
        headersJson = null,
        browserUrl = result.detailUrl,
        bookId = null,
        createdAt = Instant.now().toEpochMilli(),
        updatedAt = Instant.now().toEpochMilli()
    )

    private fun DownloadEntity.toJob(): DownloadJob = DownloadJob(
        id = id,
        sourceId = sourceId,
        recordId = recordId,
        title = title,
        format = BookFormat.valueOf(format),
        status = DownloadStatus.valueOf(status),
        totalBytes = totalBytes,
        errorKind = errorKind?.let(DownloadErrorKind::valueOf),
        browserUrl = browserUrl,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun uniqueName(sourceId: String, recordId: String) = "download:$sourceId:$recordId"
}
