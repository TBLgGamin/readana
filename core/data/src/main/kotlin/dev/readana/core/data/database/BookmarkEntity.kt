package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.model.Bookmark
import java.time.Instant

@Entity(tableName = "bookmarks", indices = [Index("book_id")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "locator_json") val locatorJson: String,
    @ColumnInfo(name = "progression") val progression: Double?,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

fun BookmarkEntity.toModel(): Bookmark = Bookmark(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    progression = progression,
    title = title,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    progression = progression,
    title = title,
    createdAt = createdAt.toEpochMilli()
)
