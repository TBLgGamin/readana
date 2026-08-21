package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.data.ai.decodeAiFigures
import dev.readana.core.data.ai.encodeToJson
import dev.readana.core.model.AiThread
import dev.readana.core.model.AiThreadKind
import java.time.Instant

/**
 * One assistant conversation. [contextText] is the already-read text exactly as it was sent, so a
 * follow-up months later still asks about the same part of the book — and about no more of it.
 */
@Entity(tableName = "ai_threads", indices = [Index("book_id")])
data class AiThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "quote") val quote: String?,
    @ColumnInfo(name = "context_text") val contextText: String,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String?,
    @ColumnInfo(name = "covered_fraction") val coveredFraction: Float?,
    @ColumnInfo(name = "figures_json") val figuresJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun AiThreadEntity.toModel(): AiThread = AiThread(
    id = id,
    bookId = bookId,
    kind = AiThreadKind.entries.firstOrNull { it.name == kind } ?: AiThreadKind.Ask,
    title = title,
    quote = quote,
    contextText = contextText,
    chapterTitle = chapterTitle,
    coveredFraction = coveredFraction,
    figures = figuresJson.decodeAiFigures(),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun AiThread.toEntity(): AiThreadEntity = AiThreadEntity(
    id = id,
    bookId = bookId,
    kind = kind.name,
    title = title,
    quote = quote,
    contextText = contextText,
    chapterTitle = chapterTitle,
    coveredFraction = coveredFraction,
    figuresJson = figures.encodeToJson(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)
