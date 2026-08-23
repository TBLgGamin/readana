package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InkStrokeDao {
    @Query(
        "SELECT * FROM ink_strokes WHERE book_id = :bookId " +
            "ORDER BY COALESCE(progression, 0), created_at"
    )
    fun observeForBook(bookId: String): Flow<List<InkStrokeEntity>>

    @Insert
    suspend fun insert(stroke: InkStrokeEntity): Long

    @Query("DELETE FROM ink_strokes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Clearing the ink on one page, which is what an eraser sweep amounts to. */
    @Query("DELETE FROM ink_strokes WHERE book_id = :bookId AND resource_href = :resourceHref")
    suspend fun deleteForResource(bookId: String, resourceHref: String)
}
