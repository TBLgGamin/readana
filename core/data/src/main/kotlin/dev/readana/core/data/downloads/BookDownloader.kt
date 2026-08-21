package dev.readana.core.data.downloads

import android.net.Uri
import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.data.conversion.ConversionManager
import dev.readana.core.data.database.DownloadEntity
import dev.readana.core.data.importing.BookImporter
import dev.readana.core.data.importing.ImportResult
import dev.readana.core.data.repository.BookSourceRegistry
import dev.readana.core.data.repository.DownloadsRepository
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadStatus
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Streams one queued job to disk and hands the file to the importer.
 *
 * Kept separate from [DownloadWorker] so the interesting behaviour — resuming, re-resolving expired
 * URLs, deciding what is worth retrying — is testable without WorkManager.
 */
@Singleton
class BookDownloader @Inject constructor(
    private val client: HttpClient,
    private val downloadsRepository: DownloadsRepository,
    private val sources: BookSourceRegistry,
    private val importer: BookImporter,
    private val conversionManager: ConversionManager,
    private val settingsRepository: SettingsRepository,
    @DownloadsDirectory private val partsDir: File,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    enum class Outcome {
        /** Imported into the library. */
        Success,

        /** Transient; the worker should back off and try again. */
        Retry,

        /** Terminal; the row already carries the reason. */
        Failure
    }

    suspend fun run(
        jobId: String,
        isRetryAttempt: Boolean,
        onProgress: suspend (bytes: Long, total: Long?) -> Unit
    ): Outcome = withContext(ioDispatcher) {
        val entity = downloadsRepository.getEntity(jobId) ?: return@withContext Outcome.Failure
        if (DownloadStatus.valueOf(entity.status) == DownloadStatus.Cancelled) return@withContext Outcome.Failure

        downloadsRepository.setStatus(jobId, DownloadStatus.Downloading)
        val part = partFile(jobId)

        // A browser-captured job carries its own cookies and cannot be resolved again; anything
        // else gets a fresh URL on a retry, since fast-download links are short-lived.
        val canReresolve = entity.headersJson == null
        val storedUrl = entity.resolvedUrl.takeUnless { isRetryAttempt && canReresolve }

        var result = storedUrl?.let { transfer(entity, it, part, onProgress) }
            ?: reresolveAndTransfer(entity, part, onProgress)

        // A stored URL that has expired is worth one re-resolution before giving up.
        if (result is Transfer.Expired && storedUrl != null && canReresolve) {
            result = reresolveAndTransfer(entity, part, onProgress)
        }

        when (val outcome = result) {
            is Transfer.Done -> finish(entity, part)
            is Transfer.Retryable -> Outcome.Retry
            is Transfer.Expired -> fail(jobId, DownloadErrorKind.Http, "The download link expired")
            is Transfer.Failed -> fail(jobId, outcome.kind, outcome.message)
        }
    }

    /** Deletes the partial file for a job that is no longer wanted. */
    fun discardPart(jobId: String) {
        partFile(jobId).delete()
    }

    /** Removes downloaded files with no matching row, e.g. after a crash. */
    fun sweepOrphans(liveJobIds: Set<String>) {
        // Both the ".part" being written and the finished file waiting to be imported are named
        // after their job, so either is rubbish once the job is gone.
        partsDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in liveJobIds) file.delete()
        }
    }

    /**
     * What the bytes say the file is, which is not always what the catalogue claimed. A download
     * that is neither — an error page served with a 200, most often — is worth catching here
     * rather than failing later with something about a corrupt publication.
     */
    private fun File.sniffFormat(): BookFormat? {
        val magic = ByteArray(MAGIC_BYTES)
        val read = inputStream().use { it.read(magic) }
        if (read < MAGIC_BYTES) return null
        return when {
            magic.decodeToString() == PDF_MAGIC -> BookFormat.PDF

            // Every EPUB is a zip; nothing else we accept is.
            magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() -> BookFormat.EPUB

            else -> null
        }
    }

    private fun partFile(jobId: String): File = File(partsDir.apply { mkdirs() }, "$jobId$PART_SUFFIX")

    private sealed interface Transfer {
        data object Done : Transfer

        data object Retryable : Transfer

        /** The server rejected the URL itself (404/410/403), so resolving again may help. */
        data object Expired : Transfer

        data class Failed(val kind: DownloadErrorKind, val message: String) : Transfer
    }

    private sealed interface Resolution {
        data class Ready(val url: String, val headers: Map<String, String>) : Resolution

        data class Failed(val kind: DownloadErrorKind, val message: String) : Resolution
    }

    private suspend fun reresolveAndTransfer(
        entity: DownloadEntity,
        part: File,
        onProgress: suspend (Long, Long?) -> Unit
    ): Transfer = when (val resolved = reresolve(entity)) {
        is Resolution.Failed -> Transfer.Failed(resolved.kind, resolved.message)
        is Resolution.Ready -> transfer(entity, resolved.url, part, onProgress, resolved.headers)
    }

    private suspend fun reresolve(entity: DownloadEntity): Resolution {
        val source = sources.byId(SourceId(entity.sourceId))
            ?: return Resolution.Failed(DownloadErrorKind.Unexpected, "Source ${entity.sourceId} is gone")
        return when (val result = source.resolveDownload(entity.toSearchResult())) {
            is AppResult.Failure -> Resolution.Failed(result.error.toErrorKind(), result.error.message)
            is AppResult.Success -> interpret(entity, result.value)
        }
    }

    private suspend fun interpret(entity: DownloadEntity, request: DownloadRequest): Resolution {
        // Resolving is the only moment the source reports a membership allowance, and Settings
        // shows it, so record it wherever the resolution happens to land.
        request.quota?.let { quota ->
            settingsRepository.updateSourceSettings {
                it.copy(annasDownloadsLeft = quota.remaining, annasDownloadsPerDay = quota.perDay)
            }
        }
        // A spent allowance still carries a URL when the source found a free route to the file;
        // only a request that insists on a browser has nowhere else to go. When that happens to a
        // member whose allowance is spent, say so — it is the more useful half of the story.
        if (request.requiresBrowser) {
            downloadsRepository.updateResolution(entity.id, url = null, browserUrl = request.url)
            return if (request.quota?.isExhausted == true) {
                Resolution.Failed(DownloadErrorKind.QuotaExhausted, "No fast downloads left today")
            } else {
                Resolution.Failed(
                    DownloadErrorKind.RequiresBrowser,
                    "This download needs the in-app browser"
                )
            }
        }
        downloadsRepository.updateResolution(entity.id, request.url, total = request.expectedSizeBytes)
        return Resolution.Ready(request.url, request.headers)
    }

    @Suppress("LongParameterList")
    private suspend fun transfer(
        entity: DownloadEntity,
        url: String,
        part: File,
        onProgress: suspend (Long, Long?) -> Unit,
        extraHeaders: Map<String, String> = emptyMap()
    ): Transfer {
        val headers = entity.storedHeaders() + extraHeaders
        val alreadyHave = part.takeIf { it.isFile }?.length() ?: 0L
        return suspendRunCatching {
            client.prepareGet(url) {
                // Books take as long as they take; the socket timeout is what catches dead streams
                // so a stalled transfer fails into a retry instead of burning the foreground-service
                // budget (docs/DEVICE.md).
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
                headers.forEach { (name, value) -> header(name, value) }
                if (alreadyHave > 0) header(HttpHeaders.Range, "bytes=$alreadyHave-")
            }.execute { response -> receive(response, entity, part, alreadyHave, onProgress) }
        }.getOrElse { Transfer.Retryable }
    }

    /** Whether the body is worth reading, and from which offset. */
    private sealed interface Reception {
        data class Read(val resumeFrom: Long) : Reception

        data class Stop(val transfer: Transfer) : Reception
    }

    private fun classifyResponse(response: HttpResponse, part: File, alreadyHave: Long): Reception = when {
        response.status == HttpStatusCode.PartialContent -> Reception.Read(alreadyHave)

        response.status.isSuccess() -> {
            // The server ignored the Range header, so whatever we had is useless.
            part.delete()
            Reception.Read(0)
        }

        response.status == HttpStatusCode.RequestedRangeNotSatisfiable -> {
            part.delete()
            Reception.Stop(Transfer.Expired)
        }

        response.status.value in EXPIRED_STATUSES -> Reception.Stop(Transfer.Expired)

        response.status.value >= HttpStatusCode.InternalServerError.value -> Reception.Stop(Transfer.Retryable)

        else -> Reception.Stop(
            Transfer.Failed(
                AppError.Http(response.status.value).toErrorKind(),
                "HTTP ${response.status.value}"
            )
        )
    }

    private suspend fun receive(
        response: HttpResponse,
        entity: DownloadEntity,
        part: File,
        alreadyHave: Long,
        onProgress: suspend (Long, Long?) -> Unit
    ): Transfer {
        val resumeFrom = when (val reception = classifyResponse(response, part, alreadyHave)) {
            is Reception.Stop -> return reception.transfer
            is Reception.Read -> reception.resumeFrom
        }
        val total = response.contentLength()?.let { it + resumeFrom } ?: entity.totalBytes
        val written = copyBody(response, part, resumeFrom, total, onProgress)

        onProgress(written, total)
        return if (written == 0L) {
            Transfer.Failed(DownloadErrorKind.Http, "The server sent an empty file")
        } else {
            Transfer.Done
        }
    }

    /** Appends the body to [part] from [resumeFrom], reporting progress every so often. */
    private suspend fun copyBody(
        response: HttpResponse,
        part: File,
        resumeFrom: Long,
        total: Long?,
        onProgress: suspend (Long, Long?) -> Unit
    ): Long {
        var written = resumeFrom
        var lastReported = resumeFrom
        RandomAccessFile(part, "rw").use { file ->
            file.setLength(written)
            file.seek(written)
            val buffer = ByteArray(CHUNK_BYTES)
            response.bodyAsChannel().toInputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    file.write(buffer, 0, read)
                    written += read
                    if (written - lastReported >= PROGRESS_STEP_BYTES) {
                        lastReported = written
                        onProgress(written, total)
                    }
                }
            }
        }
        return written
    }

    private suspend fun finish(entity: DownloadEntity, part: File): Outcome {
        val format = part.sniffFormat()
            ?: return fail(
                entity.id,
                DownloadErrorKind.Unexpected,
                "The download was not a book — the server sent something else"
            )
        // The importer works out the format from the file name, and ".part" tells it nothing, so
        // the finished file takes the extension its own first bytes just proved.
        val book = File(part.parentFile, "${entity.id}.${format.extension}")
        book.delete()
        if (!part.renameTo(book)) {
            return fail(entity.id, DownloadErrorKind.Storage, "Could not put the download in place")
        }

        val origin = BookOrigin(entity.sourceId, entity.recordId, format)
        val imported = importer.import(Uri.fromFile(book).toString(), origin)
        book.delete()
        return when (imported) {
            is AppResult.Failure -> fail(entity.id, DownloadErrorKind.Storage, imported.error.message)

            is AppResult.Success -> {
                val book = when (val outcome = imported.value) {
                    is ImportResult.Imported -> outcome.book
                    is ImportResult.Duplicate -> outcome.existing
                }
                convertIfPdf(entity.id, book)
                downloadsRepository.markCompleted(entity.id, book.id)
                Outcome.Success
            }
        }
    }

    /**
     * A downloaded PDF is converted before the job is marked done, so the queue reports
     * `Converting` instead of claiming the book is ready. A failed conversion still leaves a
     * readable PDF behind, so it does not fail the download.
     */
    private suspend fun convertIfPdf(jobId: String, book: Book) {
        if (book.format != BookFormat.PDF) return
        if (!settingsRepository.conversionSettings.first().autoConvertPdf) return
        downloadsRepository.setStatus(jobId, DownloadStatus.Converting)
        conversionManager.convertAndWait(book.id)
    }

    private suspend fun fail(jobId: String, kind: DownloadErrorKind, message: String): Outcome {
        downloadsRepository.setStatus(jobId, DownloadStatus.Failed, kind, message)
        return Outcome.Failure
    }

    private companion object {
        const val PART_SUFFIX = ".part"
        const val MAGIC_BYTES = 4
        const val PDF_MAGIC = "%PDF"
        const val CHUNK_BYTES = 64 * 1024
        const val PROGRESS_STEP_BYTES = 256L * 1024
        const val SOCKET_TIMEOUT_MS = 60_000L
        val EXPIRED_STATUSES = setOf(
            HttpStatusCode.Forbidden.value,
            HttpStatusCode.NotFound.value,
            HttpStatusCode.Gone.value
        )
    }
}

internal fun AppError.toErrorKind(): DownloadErrorKind = when (this) {
    is AppError.Network -> DownloadErrorKind.Network

    is AppError.Http -> DownloadErrorKind.Http

    is AppError.Unauthorized -> DownloadErrorKind.Unauthorized

    // Only a browser can clear the check, and the queue already offers to open one.
    is AppError.BrowserCheck -> DownloadErrorKind.RequiresBrowser

    is AppError.NotFound -> DownloadErrorKind.Http

    is AppError.Parse -> DownloadErrorKind.Unexpected

    is AppError.Unsupported -> DownloadErrorKind.Unexpected

    is AppError.Unexpected -> DownloadErrorKind.Unexpected
}

internal fun DownloadEntity.toSearchResult(): SearchResult = SearchResult(
    sourceId = SourceId(sourceId),
    recordId = recordId,
    title = title,
    authors = authors.split('|').map { it.trim() }.filter { it.isNotEmpty() },
    format = BookFormat.valueOf(format),
    sizeBytes = totalBytes,
    extras = extrasJson.decodeStringMap()
)

internal fun DownloadEntity.storedHeaders(): Map<String, String> = headersJson.decodeStringMap()
