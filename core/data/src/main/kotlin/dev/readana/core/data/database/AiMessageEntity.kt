package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.readana.core.model.AiMessage
import dev.readana.core.model.AiRole
import java.time.Instant

@Entity(tableName = "ai_messages", indices = [Index("thread_id")])
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

fun AiMessageEntity.toModel(): AiMessage = AiMessage(
    id = id,
    threadId = threadId,
    role = AiRole.entries.firstOrNull { it.name == role } ?: AiRole.Assistant,
    text = text,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun AiMessage.toEntity(): AiMessageEntity = AiMessageEntity(
    id = id,
    threadId = threadId,
    role = role.name,
    text = text,
    createdAt = createdAt.toEpochMilli()
)
