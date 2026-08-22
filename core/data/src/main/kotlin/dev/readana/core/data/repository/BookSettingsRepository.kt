package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.BookSettingsDao
import dev.readana.core.data.database.BookSettingsEntity
import dev.readana.core.data.settings.decodeReaderSettings
import dev.readana.core.data.settings.encodeToJson
import dev.readana.core.model.ReaderSettings
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reading settings a single book keeps for itself.
 *
 * A comic wants two full pages and no margins; a novel wants one column and room to breathe.
 * Rather than ask the reader to declare that, Readana remembers what they did while each book was
 * open, and [clear] hands the book back to the defaults.
 */
interface BookSettingsRepository {
    /** The book's own settings, or null while it is still reading with the global defaults. */
    fun observeFor(bookId: String): Flow<ReaderSettings?>

    suspend fun set(bookId: String, settings: ReaderSettings)

    suspend fun clear(bookId: String)
}

@Singleton
class RoomBookSettingsRepository @Inject constructor(
    private val bookSettingsDao: BookSettingsDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : BookSettingsRepository {
    override fun observeFor(bookId: String): Flow<ReaderSettings?> =
        bookSettingsDao.observeForBook(bookId).map { it?.settingsJson.decodeReaderSettings() }

    override suspend fun set(bookId: String, settings: ReaderSettings) = withContext(ioDispatcher) {
        bookSettingsDao.upsert(
            BookSettingsEntity(
                bookId = bookId,
                settingsJson = settings.encodeToJson(),
                updatedAt = Instant.now().toEpochMilli()
            )
        )
    }

    override suspend fun clear(bookId: String) = withContext(ioDispatcher) {
        bookSettingsDao.deleteForBook(bookId)
    }
}
