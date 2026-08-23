package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.InkStrokeDao
import dev.readana.core.data.database.toEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.InkStroke
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Lines drawn on a book's pages with the pen, in the order they appear in the book. */
interface InkRepository {
    fun observeForBook(bookId: String): Flow<List<InkStroke>>

    /** Stores [stroke] and returns it with the id the database assigned. */
    suspend fun add(stroke: InkStroke): InkStroke

    suspend fun remove(id: Long)

    suspend fun clearResource(bookId: String, resourceHref: String)
}

@Singleton
class RoomInkRepository @Inject constructor(
    private val inkStrokeDao: InkStrokeDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : InkRepository {
    override fun observeForBook(bookId: String): Flow<List<InkStroke>> =
        inkStrokeDao.observeForBook(bookId).map { entities ->
            // A stroke with no readable points cannot be drawn; dropping it here keeps every
            // consumer from having to think about it.
            entities.map { it.toModel() }.filter { it.points.isNotEmpty() }
        }

    override suspend fun add(stroke: InkStroke): InkStroke = withContext(ioDispatcher) {
        val id = inkStrokeDao.insert(stroke.toEntity())
        stroke.copy(id = id)
    }

    override suspend fun remove(id: Long) = withContext(ioDispatcher) {
        inkStrokeDao.deleteById(id)
    }

    override suspend fun clearResource(bookId: String, resourceHref: String) = withContext(ioDispatcher) {
        inkStrokeDao.deleteForResource(bookId, resourceHref)
    }
}
