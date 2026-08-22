package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSettingsDao {
    @Query("SELECT * FROM book_reader_settings WHERE book_id = :bookId")
    fun observeForBook(bookId: String): Flow<BookSettingsEntity?>

    @Upsert
    suspend fun upsert(settings: BookSettingsEntity)

    @Query("DELETE FROM book_reader_settings WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)
}
