package dev.readana.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Both halves of collections, in one DAO — as [AiThreadDao] covers threads and their messages. */
@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_books")
    fun observeMemberships(): Flow<List<CollectionBookEntity>>

    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Upsert
    suspend fun addBook(membership: CollectionBookEntity)

    @Query("DELETE FROM collection_books WHERE collection_id = :collectionId AND book_id = :bookId")
    suspend fun removeBook(collectionId: String, bookId: String)

    @Query("DELETE FROM collection_books WHERE collection_id = :collectionId")
    suspend fun clearCollection(collectionId: String)

    @Query("DELETE FROM collection_books WHERE book_id = :bookId")
    suspend fun removeBookEverywhere(bookId: String)
}
