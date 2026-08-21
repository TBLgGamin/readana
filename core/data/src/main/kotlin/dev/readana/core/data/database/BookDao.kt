package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(last_opened_at, added_at) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE title = :title AND size_bytes = :sizeBytes LIMIT 1")
    suspend fun findByTitleAndSize(title: String, sizeBytes: Long): BookEntity?

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE books SET progress_locator = :locator, progress_fraction = :fraction, last_opened_at = :openedAt " +
            "WHERE id = :id"
    )
    suspend fun updateProgress(id: String, locator: String, fraction: Float, openedAt: Long)
}
