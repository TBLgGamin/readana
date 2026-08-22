package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stretch of reading, measured in Readium positions.
 *
 * Positions rather than pages or percentages: a position is about a kilobyte of content whatever
 * the book, so a rate learned from one book is worth something in the next. A row is extended as
 * the reader goes rather than written when they stop, which means a session survives the app being
 * killed — and on this device it will be (see docs/DEVICE.md).
 */
@Entity(tableName = "reading_sessions", indices = [Index("book_id")])
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "start_position") val startPosition: Int,
    @ColumnInfo(name = "end_position") val endPosition: Int
)
