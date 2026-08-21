package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.model.Highlight
import dev.readana.core.model.HighlightStyle
import dev.readana.core.model.HighlightTint
import java.time.Instant

@Entity(tableName = "highlights", indices = [Index("book_id")])
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "locator_json") val locatorJson: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "tint") val tint: String,
    @ColumnInfo(name = "style") val style: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "progression") val progression: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

fun HighlightEntity.toModel(): Highlight = Highlight(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    text = text,
    tint = HighlightTint.entries.firstOrNull { it.name == tint } ?: HighlightTint.Yellow,
    style = HighlightStyle.entries.firstOrNull { it.name == style } ?: HighlightStyle.Highlight,
    note = note,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun Highlight.toEntity(progression: Double? = null): HighlightEntity = HighlightEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    text = text,
    tint = tint.name,
    style = style.name,
    note = note,
    progression = progression,
    createdAt = createdAt.toEpochMilli()
)
