package dev.readana.core.data.conversion

import dev.readana.core.model.ConversionReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wire form of [ConversionReport]. Kept separate so `core/model` stays free of serialization and
 * the stored column can gain fields without breaking older rows.
 */
@Serializable
private data class ConversionReportDto(
    val pages: Int = 0,
    val blocks: Int = 0,
    val headings: Int = 0,
    val chapters: Int = 0,
    val images: Int = 0,
    val multiColumnPages: Int = 0,
    val outlineUsed: Boolean = false,
    val warnings: List<String> = emptyList(),
    val durationMillis: Long = 0
)

private val json = Json { ignoreUnknownKeys = true }

internal fun ConversionReport.encodeToJson(): String = json.encodeToString(
    ConversionReportDto(
        pages = pages,
        blocks = blocks,
        headings = headings,
        chapters = chapters,
        images = images,
        multiColumnPages = multiColumnPages,
        outlineUsed = outlineUsed,
        warnings = warnings,
        durationMillis = durationMillis
    )
)

internal fun String?.decodeConversionReport(): ConversionReport? {
    if (this.isNullOrBlank()) return null
    val dto = try {
        json.decodeFromString<ConversionReportDto>(this)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    return ConversionReport(
        pages = dto.pages,
        blocks = dto.blocks,
        headings = dto.headings,
        chapters = dto.chapters,
        images = dto.images,
        multiColumnPages = dto.multiColumnPages,
        outlineUsed = dto.outlineUsed,
        warnings = dto.warnings,
        durationMillis = dto.durationMillis
    )
}
