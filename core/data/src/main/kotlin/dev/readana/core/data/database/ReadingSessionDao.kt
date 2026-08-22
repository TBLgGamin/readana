package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    /** Newest first, so an estimator can weight recent reading and stop early. */
    @Query("SELECT * FROM reading_sessions ORDER BY ended_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY ended_at DESC LIMIT :limit")
    fun observeForBook(bookId: String, limit: Int): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY ended_at DESC LIMIT 1")
    suspend fun latestForBook(bookId: String): ReadingSessionEntity?

    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    /** Extends the open session rather than starting another one for the same sitting. */
    @Query("UPDATE reading_sessions SET ended_at = :endedAt, end_position = :endPosition WHERE id = :id")
    suspend fun extend(id: Long, endedAt: Long, endPosition: Int)

    @Query("DELETE FROM reading_sessions WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)
}
