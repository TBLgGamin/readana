package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiThreadDao {
    @Query("SELECT * FROM ai_threads WHERE book_id = :bookId ORDER BY updated_at DESC")
    fun observeForBook(bookId: String): Flow<List<AiThreadEntity>>

    @Query("SELECT * FROM ai_threads WHERE id = :id")
    suspend fun findById(id: Long): AiThreadEntity?

    /** The newest recap for a book, which is reused while the reader has barely moved on. */
    @Query(
        "SELECT * FROM ai_threads WHERE book_id = :bookId AND kind = 'Recap' " +
            "ORDER BY updated_at DESC LIMIT 1"
    )
    suspend fun latestRecap(bookId: String): AiThreadEntity?

    @Insert
    suspend fun insertThread(thread: AiThreadEntity): Long

    @Query("UPDATE ai_threads SET updated_at = :updatedAt, title = :title WHERE id = :id")
    suspend fun touch(id: Long, title: String, updatedAt: Long)

    @Query("DELETE FROM ai_threads WHERE id = :id")
    suspend fun deleteThread(id: Long)

    @Query("DELETE FROM ai_threads WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT * FROM ai_messages WHERE thread_id = :threadId ORDER BY created_at, id")
    fun observeMessages(threadId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE thread_id = :threadId ORDER BY created_at, id")
    suspend fun messagesOf(threadId: Long): List<AiMessageEntity>

    @Insert
    suspend fun insertMessage(message: AiMessageEntity): Long

    @Query("UPDATE ai_messages SET text = :text WHERE id = :id")
    suspend fun updateMessageText(id: Long, text: String)

    @Query("DELETE FROM ai_messages WHERE thread_id = :threadId")
    suspend fun deleteMessagesOf(threadId: Long)

    @Query("DELETE FROM ai_messages WHERE thread_id IN (SELECT id FROM ai_threads WHERE book_id = :bookId)")
    suspend fun deleteMessagesForBook(bookId: String)
}
