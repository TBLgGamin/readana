package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.AiThreadDao
import dev.readana.core.data.database.toEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiThread
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Assistant conversations and their turns, newest conversation first. */
interface AiThreadsRepository {
    fun observeForBook(bookId: String): Flow<List<AiThread>>

    fun observeMessages(threadId: Long): Flow<List<AiMessage>>

    suspend fun messagesOf(threadId: Long): List<AiMessage>

    suspend fun find(threadId: Long): AiThread?

    /** The newest recap for a book, or null when there is none. */
    suspend fun latestRecap(bookId: String): AiThread?

    /** Stores [thread] and returns it with the id the database assigned. */
    suspend fun add(thread: AiThread): AiThread

    suspend fun addMessage(message: AiMessage): AiMessage

    suspend fun updateMessageText(id: Long, text: String)

    /** Records that a thread was used again, so it sorts to the top of the list. */
    suspend fun touch(thread: AiThread)

    suspend fun remove(threadId: Long)

    suspend fun removeForBook(bookId: String)
}

@Singleton
class RoomAiThreadsRepository @Inject constructor(
    private val aiThreadDao: AiThreadDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : AiThreadsRepository {
    override fun observeForBook(bookId: String): Flow<List<AiThread>> =
        aiThreadDao.observeForBook(bookId).map { threads -> threads.map { it.toModel() } }

    override fun observeMessages(threadId: Long): Flow<List<AiMessage>> =
        aiThreadDao.observeMessages(threadId).map { messages -> messages.map { it.toModel() } }

    override suspend fun messagesOf(threadId: Long): List<AiMessage> = withContext(ioDispatcher) {
        aiThreadDao.messagesOf(threadId).map { it.toModel() }
    }

    override suspend fun find(threadId: Long): AiThread? = withContext(ioDispatcher) {
        aiThreadDao.findById(threadId)?.toModel()
    }

    override suspend fun latestRecap(bookId: String): AiThread? = withContext(ioDispatcher) {
        aiThreadDao.latestRecap(bookId)?.toModel()
    }

    override suspend fun add(thread: AiThread): AiThread = withContext(ioDispatcher) {
        val id = aiThreadDao.insertThread(thread.toEntity())
        thread.copy(id = id)
    }

    override suspend fun addMessage(message: AiMessage): AiMessage = withContext(ioDispatcher) {
        val id = aiThreadDao.insertMessage(message.toEntity())
        message.copy(id = id)
    }

    override suspend fun updateMessageText(id: Long, text: String) = withContext(ioDispatcher) {
        aiThreadDao.updateMessageText(id, text)
    }

    override suspend fun touch(thread: AiThread) = withContext(ioDispatcher) {
        aiThreadDao.touch(thread.id, thread.title, thread.updatedAt.toEpochMilli())
    }

    // Messages have no foreign key, matching the other annotation tables, so they go explicitly.
    override suspend fun remove(threadId: Long) = withContext(ioDispatcher) {
        aiThreadDao.deleteMessagesOf(threadId)
        aiThreadDao.deleteThread(threadId)
    }

    override suspend fun removeForBook(bookId: String) = withContext(ioDispatcher) {
        aiThreadDao.deleteMessagesForBook(bookId)
        aiThreadDao.deleteForBook(bookId)
    }
}
