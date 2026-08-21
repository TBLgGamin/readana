package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query(
        "SELECT * FROM highlights WHERE book_id = :bookId " +
            "ORDER BY COALESCE(progression, 0), created_at"
    )
    fun observeForBook(bookId: String): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    /** Only the parts the user can change; the locator and its progression stay put. */
    @Query("UPDATE highlights SET tint = :tint, style = :style, note = :note WHERE id = :id")
    suspend fun updateDetails(id: Long, tint: String, style: String, note: String?)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)
}
