package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.DownloadDao
import dev.readana.core.data.database.DownloadEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The download queue. Rows are the source of truth for the queue UI: the worker writes progress
 * here so it survives process death, and features observe it without depending on WorkManager.
 */
interface DownloadsRepository {
    val jobs: Flow<List<DownloadJob>>

    suspend fun getEntity(id: String): DownloadEntity?

    suspend fun getJob(id: String): DownloadJob?

    suspend fun findActive(sourceId: String, recordId: String): DownloadJob?

    suspend fun upsert(entity: DownloadEntity)

    suspend fun updateProgress(id: String, bytes: Long, total: Long?)

    suspend fun updateResolution(id: String, url: String?, browserUrl: String? = null, total: Long? = null)

    suspend fun setStatus(
        id: String,
        status: DownloadStatus,
        errorKind: DownloadErrorKind? = null,
        errorMessage: String? = null
    )

    /** Ties the row to the library book it produced. */
    suspend fun attachBook(id: String, bookId: String)

    suspend fun remove(id: String)

    /** Drops every row that has stopped for good, and reports which ones went. */
    suspend fun removeFinished(): List<String>
}

@Singleton
class RoomDownloadsRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : DownloadsRepository {
    override val jobs: Flow<List<DownloadJob>> = downloadDao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    override suspend fun getEntity(id: String): DownloadEntity? = withContext(ioDispatcher) {
        downloadDao.getById(id)
    }

    override suspend fun getJob(id: String): DownloadJob? = withContext(ioDispatcher) {
        downloadDao.getById(id)?.toModel()
    }

    override suspend fun findActive(sourceId: String, recordId: String): DownloadJob? =
        withContext(ioDispatcher) { downloadDao.findActive(sourceId, recordId)?.toModel() }

    override suspend fun upsert(entity: DownloadEntity) = withContext(ioDispatcher) {
        downloadDao.upsert(entity)
    }

    override suspend fun updateProgress(id: String, bytes: Long, total: Long?) = withContext(ioDispatcher) {
        downloadDao.updateProgress(id, bytes, total, now())
    }

    override suspend fun updateResolution(id: String, url: String?, browserUrl: String?, total: Long?) =
        withContext(ioDispatcher) { downloadDao.updateResolution(id, url, browserUrl, total, now()) }

    override suspend fun setStatus(
        id: String,
        status: DownloadStatus,
        errorKind: DownloadErrorKind?,
        errorMessage: String?
    ) = withContext(ioDispatcher) {
        downloadDao.updateStatus(id, status.name, errorKind?.name, errorMessage, now())
    }

    override suspend fun attachBook(id: String, bookId: String) = withContext(ioDispatcher) {
        downloadDao.attachBook(id, bookId, now())
    }

    override suspend fun remove(id: String) = withContext(ioDispatcher) { downloadDao.deleteById(id) }

    override suspend fun removeFinished(): List<String> = withContext(ioDispatcher) {
        // Read first: the caller has partial files to clean up and needs the ids to find them.
        val ids = downloadDao.getFinished().map { it.id }
        downloadDao.deleteFinished()
        ids
    }

    private fun now(): Long = Instant.now().toEpochMilli()
}
