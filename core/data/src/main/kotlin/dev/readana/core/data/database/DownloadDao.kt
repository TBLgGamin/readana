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
            "AND status IN ('Queued', 'Resolving', 'Downloading', 'Converting', 'Paused') LIMIT 1"
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

    /**
     * Records which library book this download became, once the importer has run. It is what lets
     * the queue show a conversion under the download that started it rather than beside it.
     */
    @Query("UPDATE downloads SET book_id = :bookId, updated_at = :now WHERE id = :id")
    suspend fun attachBook(id: String, bookId: String, now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Clears everything that is no longer going anywhere; anything in the queue is left alone.
     *
     * 'Completed' is still listed because a library set up before finished downloads started
     * dropping themselves can hold rows in that state.
     */
    @Query("DELETE FROM downloads WHERE status IN ('Completed', 'Failed', 'Cancelled')")
    suspend fun deleteFinished(): Int

    @Query("SELECT * FROM downloads WHERE status IN ('Completed', 'Failed', 'Cancelled')")
    suspend fun getFinished(): List<DownloadEntity>
}
