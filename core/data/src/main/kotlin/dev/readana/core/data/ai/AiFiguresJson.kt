package dev.readana.core.data.ai

import dev.readana.core.model.AiFigure
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wire form of a conversation's figure list, stored in one column. The pictures themselves stay in
 * the book — only where to find them again is written down.
 */
@Serializable
private data class AiFigureDto(
    val id: String,
    val href: String,
    val caption: String? = null,
    val mediaType: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

internal fun List<AiFigure>.encodeToJson(): String? {
    if (isEmpty()) return null
    return json.encodeToString(
        map { AiFigureDto(id = it.id, href = it.href, caption = it.caption, mediaType = it.mediaType) }
    )
}

internal fun String?.decodeAiFigures(): List<AiFigure> {
    if (this.isNullOrBlank()) return emptyList()
    val dtos = try {
        json.decodeFromString<List<AiFigureDto>>(this)
    } catch (_: SerializationException) {
        return emptyList()
    } catch (_: IllegalArgumentException) {
        return emptyList()
    }
    return dtos.map {
        AiFigure(id = it.id, href = it.href, caption = it.caption, mediaType = it.mediaType)
    }
}
