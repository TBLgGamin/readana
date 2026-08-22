package dev.readana.core.data.repository

import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.database.CollectionBookEntity
import dev.readana.core.data.database.CollectionDao
import dev.readana.core.data.database.CollectionEntity
import dev.readana.core.data.database.toModel
import dev.readana.core.model.BookCollection
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The shelves the reader has made, and which books are on them. */
interface CollectionsRepository {
    val collections: Flow<List<BookCollection>>

    /** Book ids by collection id, so the library can filter without a query per shelf. */
    val memberships: Flow<Map<String, Set<String>>>

    /** Creates a shelf with [name] and returns it. */
    suspend fun create(name: String): BookCollection

    suspend fun rename(id: String, name: String)

    suspend fun delete(id: String)

    suspend fun setMembership(collectionId: String, bookId: String, member: Boolean)

    /** Takes a deleted book off every shelf it was on. */
    suspend fun removeBook(bookId: String)
}

@Singleton
class RoomCollectionsRepository @Inject constructor(
    private val collectionDao: CollectionDao,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : CollectionsRepository {
    override val collections: Flow<List<BookCollection>> =
        collectionDao.observeAll().map { entities -> entities.map { it.toModel() } }

    override val memberships: Flow<Map<String, Set<String>>> =
        collectionDao.observeMemberships().map { rows ->
            rows.groupBy({ it.collectionId }, { it.bookId }).mapValues { it.value.toSet() }
        }

    override suspend fun create(name: String): BookCollection = withContext(ioDispatcher) {
        val collection = BookCollection(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = Instant.now()
        )
        collectionDao.upsert(
            CollectionEntity(
                id = collection.id,
                name = collection.name,
                createdAt = collection.createdAt.toEpochMilli()
            )
        )
        collection
    }

    override suspend fun rename(id: String, name: String) = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext
        collectionDao.upsert(
            CollectionEntity(id = id, name = trimmed, createdAt = Instant.now().toEpochMilli())
        )
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        // No foreign keys in this schema, so the memberships go explicitly.
        collectionDao.clearCollection(id)
        collectionDao.deleteCollection(id)
    }

    override suspend fun setMembership(collectionId: String, bookId: String, member: Boolean) =
        withContext(ioDispatcher) {
            if (member) {
                collectionDao.addBook(
                    CollectionBookEntity(
                        collectionId = collectionId,
                        bookId = bookId,
                        addedAt = Instant.now().toEpochMilli()
                    )
                )
            } else {
                collectionDao.removeBook(collectionId, bookId)
            }
        }

    override suspend fun removeBook(bookId: String) = withContext(ioDispatcher) {
        collectionDao.removeBookEverywhere(bookId)
    }
}
