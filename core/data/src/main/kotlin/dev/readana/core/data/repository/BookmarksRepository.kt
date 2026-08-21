package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.BookmarkDao
import dev.readana.core.data.database.toEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.Bookmark
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Saved places inside books, ordered by where they sit in the book. */
interface BookmarksRepository {
    fun observeForBook(bookId: String): Flow<List<Bookmark>>

    /** Stores [bookmark] and returns it with the id the database assigned. */
    suspend fun add(bookmark: Bookmark): Bookmark

    suspend fun remove(id: Long)
}

@Singleton
class RoomBookmarksRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : BookmarksRepository {
    override fun observeForBook(bookId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeForBook(bookId).map { entities -> entities.map { it.toModel() } }

    override suspend fun add(bookmark: Bookmark): Bookmark = withContext(ioDispatcher) {
        val id = bookmarkDao.insert(bookmark.toEntity())
        bookmark.copy(id = id)
    }

    override suspend fun remove(id: Long) = withContext(ioDispatcher) {
        bookmarkDao.deleteById(id)
    }
}
