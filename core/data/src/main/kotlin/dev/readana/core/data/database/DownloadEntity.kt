package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.model.BookFormat
import dev.readana.core.model.DownloadErrorKind
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus
import java.time.Instant

@Entity(tableName = "downloads", indices = [Index("source_id", "record_id")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val title: String,
    /** Pipe-separated list, as in [BookEntity]. */
    val authors: String,
    val format: String,
    val status: String,
    @ColumnInfo(name = "error_kind") val errorKind: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "bytes_downloaded") val bytesDownloaded: Long,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long?,
    /** JSON of the search result's source-private extras, so a retry can re-resolve the URL. */
    @ColumnInfo(name = "extras_json") val extrasJson: String?,
    @ColumnInfo(name = "resolved_url") val resolvedUrl: String?,
    /** JSON header map. Non-null means the URL came from the browser and must not be re-resolved. */
    @ColumnInfo(name = "headers_json") val headersJson: String?,
    /** Record page for the in-app browser fallback. */
    @ColumnInfo(name = "browser_url") val browserUrl: String?,
    @ColumnInfo(name = "book_id") val bookId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun DownloadEntity.toModel(): DownloadJob = DownloadJob(
    id = id,
    sourceId = sourceId,
    recordId = recordId,
    title = title,
    format = BookFormat.valueOf(format),
    status = DownloadStatus.valueOf(status),
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    errorMessage = errorMessage,
    errorKind = errorKind?.let(DownloadErrorKind::valueOf),
    bookId = bookId,
    browserUrl = browserUrl,
    createdAt = Instant.ofEpochMilli(createdAt)
)
