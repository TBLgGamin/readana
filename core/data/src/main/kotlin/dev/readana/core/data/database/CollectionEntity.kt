package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.model.BookCollection
import java.time.Instant

/** A shelf the reader made. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * Which books are on which shelf.
 *
 * No foreign keys, matching every other table in this schema; a deleted book's memberships are
 * removed explicitly by the repository.
 */
@Entity(
    tableName = "collection_books",
    primaryKeys = ["collection_id", "book_id"],
    indices = [Index("collection_id"), Index("book_id")]
)
data class CollectionBookEntity(
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long
)

fun CollectionEntity.toModel(): BookCollection = BookCollection(
    id = id,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun BookCollection.toEntity(): CollectionEntity = CollectionEntity(
    id = id,
    name = name,
    createdAt = createdAt.toEpochMilli()
)
