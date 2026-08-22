package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.ReadingSessionDao
import dev.readana.core.data.database.ReadingSessionEntity
import dev.readana.core.data.reading.ReadingSpeed
import dev.readana.core.data.reading.ReadingStretch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * How long the reader has spent in each book, and how fast that makes them.
 *
 * [record] is called on the same beat as the debounced progress save, so a sitting is written down
 * as it happens rather than when it ends — there is no reliable "ends" on a device that kills
 * background work as readily as this one.
 */
interface ReadingSessionsRepository {
    /**
     * Notes that the reader is at [position] in [bookId] now, extending the current sitting or
     * beginning a new one after a long enough gap.
     */
    suspend fun record(bookId: String, position: Int)

    /** Positions per minute for this book, falling back to the reader's general pace. */
    fun observeRate(bookId: String): Flow<Double?>

    suspend fun removeForBook(bookId: String)
}

@Singleton
class RoomReadingSessionsRepository @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : ReadingSessionsRepository {
    override suspend fun record(bookId: String, position: Int) = withContext(ioDispatcher) {
        val now = Instant.now().toEpochMilli()
        val open = readingSessionDao.latestForBook(bookId)?.takeIf { now - it.endedAt <= SESSION_GAP_MILLIS }
        if (open == null) {
            readingSessionDao.insert(
                ReadingSessionEntity(
                    bookId = bookId,
                    startedAt = now,
                    endedAt = now,
                    startPosition = position,
                    endPosition = position
                )
            )
            return@withContext
        }
        readingSessionDao.extend(id = open.id, endedAt = now, endPosition = position)
    }

    override fun observeRate(bookId: String): Flow<Double?> = combine(
        readingSessionDao.observeForBook(bookId, HISTORY_LIMIT).map { it.toStretches() },
        readingSessionDao.observeRecent(HISTORY_LIMIT).map { it.toStretches() }
    ) { forBook, everywhere -> ReadingSpeed.rateForBook(forBook, everywhere) }

    override suspend fun removeForBook(bookId: String) = withContext(ioDispatcher) {
        readingSessionDao.deleteForBook(bookId)
    }

    private fun List<ReadingSessionEntity>.toStretches(): List<ReadingStretch> = map { session ->
        ReadingStretch(
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            positions = session.endPosition - session.startPosition
        )
    }

    private companion object {
        /** Longer than this and the reader went away; the next page turn starts a new sitting. */
        const val SESSION_GAP_MILLIS = 3L * 60 * 1000

        /** Enough history to know a pace; the weighting makes anything older count for nothing. */
        const val HISTORY_LIMIT = 30
    }
}
