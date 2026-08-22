package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.HighlightDao
import dev.readana.core.data.database.toEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.Highlight
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Marked passages and their notes, ordered by where they sit in the book. */
interface HighlightsRepository {
    fun observeForBook(bookId: String): Flow<List<Highlight>>

    /** Stores [highlight] and returns it with the id the database assigned. */
    suspend fun add(highlight: Highlight): Highlight

    suspend fun update(highlight: Highlight)

    suspend fun remove(id: Long)
}

@Singleton
class RoomHighlightsRepository @Inject constructor(
    private val highlightDao: HighlightDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : HighlightsRepository {
    override fun observeForBook(bookId: String): Flow<List<Highlight>> =
        highlightDao.observeForBook(bookId).map { entities -> entities.map { it.toModel() } }

    override suspend fun add(highlight: Highlight): Highlight = withContext(ioDispatcher) {
        val id = highlightDao.insert(highlight.toEntity())
        highlight.copy(id = id)
    }

    override suspend fun update(highlight: Highlight) = withContext(ioDispatcher) {
        highlightDao.updateDetails(
            id = highlight.id,
            tint = highlight.tint.name,
            style = highlight.style.name,
            note = highlight.note
        )
    }

    override suspend fun remove(id: Long) = withContext(ioDispatcher) {
        highlightDao.deleteById(id)
    }
}
