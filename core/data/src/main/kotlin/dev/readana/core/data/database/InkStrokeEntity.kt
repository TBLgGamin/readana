package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.model.HighlightTint
import dev.readana.core.model.InkPoint
import dev.readana.core.model.InkStroke
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Entity(tableName = "ink_strokes", indices = [Index("book_id")])
data class InkStrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "resource_href") val resourceHref: String,
    @ColumnInfo(name = "anchor_locator_json") val anchorLocatorJson: String,
    /** The points, JSON-encoded: a stroke is only ever read and written whole. */
    @ColumnInfo(name = "points_json") val pointsJson: String,
    @ColumnInfo(name = "tint") val tint: String,
    @ColumnInfo(name = "layout_fingerprint") val layoutFingerprint: String,
    @ColumnInfo(name = "progression") val progression: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class InkPointDto(val x: Float, val y: Float, val pressure: Float = 1f)

fun InkStrokeEntity.toModel(): InkStroke = InkStroke(
    id = id,
    bookId = bookId,
    resourceHref = resourceHref,
    anchorLocatorJson = anchorLocatorJson,
    // A stroke whose points cannot be read is a stroke that cannot be drawn; an empty one is
    // filtered out by the reader rather than crashing the page it was on.
    points = runCatching { json.decodeFromString<List<InkPointDto>>(pointsJson) }
        .getOrDefault(emptyList())
        .map { InkPoint(it.x, it.y, it.pressure) },
    tint = HighlightTint.entries.firstOrNull { it.name == tint } ?: HighlightTint.Yellow,
    layoutFingerprint = layoutFingerprint,
    progression = progression,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun InkStroke.toEntity(): InkStrokeEntity = InkStrokeEntity(
    id = id,
    bookId = bookId,
    resourceHref = resourceHref,
    anchorLocatorJson = anchorLocatorJson,
    pointsJson = json.encodeToString(points.map { InkPointDto(it.x, it.y, it.pressure) }),
    tint = tint.name,
    layoutFingerprint = layoutFingerprint,
    progression = progression,
    createdAt = createdAt.toEpochMilli()
)
