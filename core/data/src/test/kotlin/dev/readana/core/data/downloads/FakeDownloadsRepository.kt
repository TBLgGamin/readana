package dev.readana.core.data.downloads

import dev.readana.core.data.database.DownloadEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.data.repository.DownloadsRepository
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeDownloadsRepository : DownloadsRepository {
    val state = MutableStateFlow<Map<String, DownloadEntity>>(emptyMap())
    val progressWrites = mutableListOf<Pair<Long, Long?>>()

    override val jobs: Flow<List<DownloadJob>> = state.map { entities ->
        entities.values.sortedByDescending { it.createdAt }.map { it.toModel() }
    }

    override suspend fun getEntity(id: String): DownloadEntity? = state.value[id]

    override suspend fun getJob(id: String): DownloadJob? = state.value[id]?.toModel()

    override suspend fun findActive(sourceId: String, recordId: String): DownloadJob? = state.value.values.firstOrNull {
        it.sourceId == sourceId && it.recordId == recordId && it.toModel().isActive
    }?.toModel()

    override suspend fun upsert(entity: DownloadEntity) = state.update { it + (entity.id to entity) }

    override suspend fun updateProgress(id: String, bytes: Long, total: Long?) {
        progressWrites += bytes to total
        edit(id) { it.copy(bytesDownloaded = bytes, totalBytes = total ?: it.totalBytes) }
    }

    override suspend fun updateResolution(id: String, url: String?, browserUrl: String?, total: Long?) = edit(id) {
        it.copy(
            resolvedUrl = url ?: it.resolvedUrl,
            browserUrl = browserUrl ?: it.browserUrl,
            totalBytes = total ?: it.totalBytes
        )
    }

    override suspend fun setStatus(
        id: String,
        status: DownloadStatus,
        errorKind: DownloadErrorKind?,
        errorMessage: String?
    ) = edit(id) {
        it.copy(status = status.name, errorKind = errorKind?.name, errorMessage = errorMessage)
    }

    override suspend fun attachBook(id: String, bookId: String) = edit(id) { it.copy(bookId = bookId) }

    override suspend fun remove(id: String) = state.update { it - id }

    override suspend fun removeFinished(): List<String> {
        val ids = state.value.values.filterNot { it.toModel().isPending }.map { it.id }
        state.update { entities -> entities - ids.toSet() }
        return ids
    }

    private fun edit(id: String, block: (DownloadEntity) -> DownloadEntity) = state.update { entities ->
        val entity = entities[id] ?: return@update entities
        entities + (id to block(entity))
    }
}

fun downloadEntity(
    id: String = "job-1",
    sourceId: String = "annas-archive",
    recordId: String = "0123456789abcdef0123456789abcdef",
    status: DownloadStatus = DownloadStatus.Queued,
    resolvedUrl: String? = "https://fast.test/dl/abc.epub",
    headersJson: String? = null,
    totalBytes: Long? = null,
    extrasJson: String? = """{"md5":"0123456789abcdef0123456789abcdef"}"""
) = DownloadEntity(
    id = id,
    sourceId = sourceId,
    recordId = recordId,
    title = "Pride and Prejudice",
    authors = "Jane Austen",
    format = "EPUB",
    status = status.name,
    errorKind = null,
    errorMessage = null,
    bytesDownloaded = 0,
    totalBytes = totalBytes,
    extrasJson = extrasJson,
    resolvedUrl = resolvedUrl,
    headersJson = headersJson,
    browserUrl = null,
    bookId = null,
    createdAt = Instant.EPOCH.toEpochMilli(),
    updatedAt = Instant.EPOCH.toEpochMilli()
)
