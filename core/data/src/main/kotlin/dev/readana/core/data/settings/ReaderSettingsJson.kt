package dev.readana.core.data.settings

import dev.readana.core.model.ReaderSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wire form of [ReaderSettings] for the per-book override column.
 *
 * One JSON blob rather than a column per field: this is a whole settings object stored against a
 * book, and the flat key-per-field style DataStore uses for the global defaults would mean fifteen
 * columns and a migration every time a setting is added.
 *
 * A blob that cannot be read falls back to the defaults, so a bad write costs the reader their
 * adjustments for that one book and nothing else.
 */
@Serializable
private data class ReaderSettingsDto(
    val fontScale: Float? = null,
    val fontFamily: String? = null,
    val theme: String? = null,
    val lineHeight: Float? = null,
    val paragraphSpacing: Float? = null,
    val pageMargins: Float? = null,
    val pageTurnMode: String? = null,
    val columns: String? = null,
    val tapToTurnPages: Boolean? = null,
    val volumeKeysTurnPages: Boolean? = null,
    val keepScreenOn: Boolean? = null,
    val ttsSpeed: Float? = null
)

private val json = Json { ignoreUnknownKeys = true }

internal fun ReaderSettings.encodeToJson(): String = json.encodeToString(
    ReaderSettingsDto(
        fontScale = fontScale,
        fontFamily = fontFamily.name,
        theme = theme.name,
        lineHeight = lineHeight,
        paragraphSpacing = paragraphSpacing,
        pageMargins = pageMargins,
        pageTurnMode = pageTurnMode.name,
        columns = columns.name,
        tapToTurnPages = tapToTurnPages,
        volumeKeysTurnPages = volumeKeysTurnPages,
        keepScreenOn = keepScreenOn,
        ttsSpeed = ttsSpeed
    )
)

/**
 * Reads a stored override back. Every value is re-clamped and every enum re-checked, because the
 * ranges are the reader's own and may have narrowed since the blob was written.
 */
internal fun String?.decodeReaderSettings(): ReaderSettings? {
    if (this.isNullOrBlank()) return null
    val dto = try {
        json.decodeFromString<ReaderSettingsDto>(this)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    val defaults = ReaderSettings()
    return ReaderSettings(
        fontScale = dto.fontScale?.coerceIn(ReaderSettings.FONT_SCALE_RANGE) ?: defaults.fontScale,
        fontFamily = enumOrDefault(dto.fontFamily, defaults.fontFamily),
        theme = enumOrDefault(dto.theme, defaults.theme),
        lineHeight = dto.lineHeight?.coerceIn(ReaderSettings.LINE_HEIGHT_RANGE) ?: defaults.lineHeight,
        paragraphSpacing = dto.paragraphSpacing?.coerceIn(ReaderSettings.PARAGRAPH_SPACING_RANGE)
            ?: defaults.paragraphSpacing,
        pageMargins = dto.pageMargins?.coerceIn(ReaderSettings.PAGE_MARGIN_RANGE) ?: defaults.pageMargins,
        pageTurnMode = enumOrDefault(dto.pageTurnMode, defaults.pageTurnMode),
        columns = enumOrDefault(dto.columns, defaults.columns),
        tapToTurnPages = dto.tapToTurnPages ?: defaults.tapToTurnPages,
        volumeKeysTurnPages = dto.volumeKeysTurnPages ?: defaults.volumeKeysTurnPages,
        keepScreenOn = dto.keepScreenOn ?: defaults.keepScreenOn,
        ttsSpeed = dto.ttsSpeed?.coerceIn(ReaderSettings.TTS_SPEED_RANGE) ?: defaults.ttsSpeed
    )
}

/** A member that no longer exists reads as the default rather than throwing. */
private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: default
