package dev.readana.core.data.downloads

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Source extras and captured headers are stored as JSON text columns. */
internal fun Map<String, String>.encodeToJson(): String? = takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

internal fun String?.decodeStringMap(): Map<String, String> {
    if (this.isNullOrBlank()) return emptyMap()
    return try {
        json.decodeFromString<Map<String, String>>(this)
    } catch (_: SerializationException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }
}
