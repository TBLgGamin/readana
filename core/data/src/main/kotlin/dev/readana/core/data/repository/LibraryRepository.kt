package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.BookDao
import dev.readana.core.data.database.toEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.Book
import dev.readana.core.model.ReadingProgress
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The on-device library: what the user owns and where they are in each book. */
interface LibraryRepository {
    val books: Flow<List<Book>>

    fun observeBook(id: String): Flow<Book?>

    suspend fun getBook(id: String): Book?

    suspend fun upsert(book: Book)

    suspend fun remove(id: String)

    suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant = Instant.now())
}

@Singleton
class RoomLibraryRepository @Inject constructor(
    private val bookDao: BookDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : LibraryRepository {
    override val books: Flow<List<Book>> = bookDao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    override fun observeBook(id: String): Flow<Book?> = bookDao.observeById(id).map {
        it?.toModel()
    }

    override suspend fun getBook(id: String): Book? = withContext(ioDispatcher) {
        bookDao.getById(id)?.toModel()
    }

    override suspend fun upsert(book: Book) = withContext(ioDispatcher) {
        bookDao.upsert(book.toEntity())
    }

    override suspend fun remove(id: String) = withContext(ioDispatcher) { bookDao.deleteById(id) }

    override suspend fun updateProgress(id: String, progress: ReadingProgress, openedAt: Instant) =
        withContext(ioDispatcher) {
            bookDao.updateProgress(
                id,
                progress.locatorJson,
                progress.fraction,
                openedAt.toEpochMilli()
            )
        }
}
