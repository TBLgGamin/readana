package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query(
        "SELECT * FROM downloads WHERE source_id = :sourceId AND record_id = :recordId " +
            "AND status IN ('Queued', 'Downloading', 'Converting') LIMIT 1"
    )
    suspend fun findActive(sourceId: String, recordId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query(
        "UPDATE downloads SET bytes_downloaded = :bytes, total_bytes = :total, " +
            "status = 'Downloading', updated_at = :now WHERE id = :id"
    )
    suspend fun updateProgress(id: String, bytes: Long, total: Long?, now: Long)

    @Query(
        "UPDATE downloads SET status = :status, error_kind = :errorKind, " +
            "error_message = :errorMessage, updated_at = :now WHERE id = :id"
    )
    suspend fun updateStatus(id: String, status: String, errorKind: String?, errorMessage: String?, now: Long)

    @Query(
        "UPDATE downloads SET resolved_url = :url, browser_url = :browserUrl, " +
            "total_bytes = COALESCE(:total, total_bytes), updated_at = :now WHERE id = :id"
    )
    suspend fun updateResolution(id: String, url: String?, browserUrl: String?, total: Long?, now: Long)

    @Query(
        "UPDATE downloads SET status = 'Completed', book_id = :bookId, error_kind = NULL, " +
            "error_message = NULL, updated_at = :now WHERE id = :id"
    )
    suspend fun markCompleted(id: String, bookId: String, now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}
