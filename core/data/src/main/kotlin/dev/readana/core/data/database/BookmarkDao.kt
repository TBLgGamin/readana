package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query(
        "SELECT * FROM bookmarks WHERE book_id = :bookId " +
            "ORDER BY COALESCE(progression, 0), created_at"
    )
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)
}
