package dev.readana.core.data.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings

internal object SettingsKeys {
    val FONT_SCALE = floatPreferencesKey("reader_font_scale")
    val FONT_FAMILY = stringPreferencesKey("reader_font_family")
    val THEME = stringPreferencesKey("reader_theme")
    val LINE_HEIGHT = floatPreferencesKey("reader_line_height")
    val PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
    val PAGE_MARGINS = floatPreferencesKey("reader_page_margins")
    val PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
    val COLUMNS = stringPreferencesKey("reader_columns")
    val TAP_TO_TURN = booleanPreferencesKey("reader_tap_to_turn")
    val VOLUME_KEYS = booleanPreferencesKey("reader_volume_keys")
    val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
    val TTS_SPEED = floatPreferencesKey("reader_tts_speed")

    val SOURCE_OVERRIDES = stringPreferencesKey("source_overrides")
    val ANNAS_MIRRORS = stringPreferencesKey("source_annas_mirrors")
    val ANNAS_SECRET_KEY = stringPreferencesKey("source_annas_secret_key")
    val ANNAS_PREFERRED_MIRROR = stringPreferencesKey("source_annas_preferred_mirror")
    val ANNAS_DOWNLOADS_LEFT = intPreferencesKey("source_annas_downloads_left")
    val ANNAS_DOWNLOADS_PER_DAY = intPreferencesKey("source_annas_downloads_per_day")

    val CONVERSION_AUTO = booleanPreferencesKey("conversion_auto_pdf")
    val CONVERSION_STRIP_HEADERS = booleanPreferencesKey("conversion_strip_headers")
    val CONVERSION_HEADING_SENSITIVITY = stringPreferencesKey("conversion_heading_sensitivity")
    val CONVERSION_EXTRACT_IMAGES = booleanPreferencesKey("conversion_extract_images")

    val LIBRARY_FOLDER_URI = stringPreferencesKey("library_folder_uri")

    /** The whole assistant configuration, JSON-encoded: it holds a list of provider records. */
    val AI_SETTINGS = stringPreferencesKey("ai_settings_json")

    const val MIRROR_SEPARATOR = "\n"
    const val OVERRIDE_SEPARATOR = "\n"
    const val OVERRIDE_ASSIGNMENT = "="
}

internal fun Preferences.toReaderSettings(): ReaderSettings {
    val defaults = ReaderSettings()
    return ReaderSettings(
        fontScale = this[SettingsKeys.FONT_SCALE] ?: defaults.fontScale,
        fontFamily = enumOrDefault(this[SettingsKeys.FONT_FAMILY], defaults.fontFamily),
        theme = enumOrDefault(this[SettingsKeys.THEME], defaults.theme),
        lineHeight = this[SettingsKeys.LINE_HEIGHT] ?: defaults.lineHeight,
        paragraphSpacing = this[SettingsKeys.PARAGRAPH_SPACING] ?: defaults.paragraphSpacing,
        pageMargins = this[SettingsKeys.PAGE_MARGINS] ?: defaults.pageMargins,
        pageTurnMode = enumOrDefault(this[SettingsKeys.PAGE_TURN_MODE], defaults.pageTurnMode),
        columns = enumOrDefault(this[SettingsKeys.COLUMNS], defaults.columns),
        tapToTurnPages = this[SettingsKeys.TAP_TO_TURN] ?: defaults.tapToTurnPages,
        volumeKeysTurnPages = this[SettingsKeys.VOLUME_KEYS] ?: defaults.volumeKeysTurnPages,
        keepScreenOn = this[SettingsKeys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        ttsSpeed = this[SettingsKeys.TTS_SPEED] ?: defaults.ttsSpeed
    )
}

internal fun MutablePreferences.write(settings: ReaderSettings) {
    this[SettingsKeys.FONT_SCALE] = settings.fontScale
    this[SettingsKeys.FONT_FAMILY] = settings.fontFamily.name
    this[SettingsKeys.THEME] = settings.theme.name
    this[SettingsKeys.LINE_HEIGHT] = settings.lineHeight
    this[SettingsKeys.PARAGRAPH_SPACING] = settings.paragraphSpacing
    this[SettingsKeys.PAGE_MARGINS] = settings.pageMargins
    this[SettingsKeys.PAGE_TURN_MODE] = settings.pageTurnMode.name
    this[SettingsKeys.COLUMNS] = settings.columns.name
    this[SettingsKeys.TAP_TO_TURN] = settings.tapToTurnPages
    this[SettingsKeys.VOLUME_KEYS] = settings.volumeKeysTurnPages
    this[SettingsKeys.KEEP_SCREEN_ON] = settings.keepScreenOn
    this[SettingsKeys.TTS_SPEED] = settings.ttsSpeed
}

internal fun Preferences.toSourceSettings(): SourceSettings = SourceSettings(
    sourceOverrides = this[SettingsKeys.SOURCE_OVERRIDES]
        ?.split(SettingsKeys.OVERRIDE_SEPARATOR)
        ?.mapNotNull { line ->
            val (id, value) = line.split(SettingsKeys.OVERRIDE_ASSIGNMENT, limit = 2).takeIf {
                it.size ==
                    2
            }
                ?: return@mapNotNull null
            id.trim().takeIf { it.isNotEmpty() }?.let { it to value.trim().toBoolean() }
        }
        ?.toMap()
        .orEmpty(),
    annasMirrors = this[SettingsKeys.ANNAS_MIRRORS]
        ?.split(SettingsKeys.MIRROR_SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty(),
    annasSecretKey = this[SettingsKeys.ANNAS_SECRET_KEY]?.takeIf { it.isNotBlank() },
    annasPreferredMirror = this[SettingsKeys.ANNAS_PREFERRED_MIRROR]?.takeIf { it.isNotBlank() },
    annasDownloadsLeft = this[SettingsKeys.ANNAS_DOWNLOADS_LEFT],
    annasDownloadsPerDay = this[SettingsKeys.ANNAS_DOWNLOADS_PER_DAY]
)

internal fun MutablePreferences.write(settings: SourceSettings) {
    this[SettingsKeys.SOURCE_OVERRIDES] = settings.sourceOverrides.entries
        .joinToString(SettingsKeys.OVERRIDE_SEPARATOR) { (id, enabled) ->
            "$id${SettingsKeys.OVERRIDE_ASSIGNMENT}$enabled"
        }
    this[SettingsKeys.ANNAS_MIRRORS] =
        settings.annasMirrors.joinToString(SettingsKeys.MIRROR_SEPARATOR)
    val key = settings.annasSecretKey?.trim()
    if (key.isNullOrEmpty()) {
        remove(SettingsKeys.ANNAS_SECRET_KEY)
    } else {
        this[SettingsKeys.ANNAS_SECRET_KEY] =
            key
    }
    val mirror = settings.annasPreferredMirror?.trim()
    if (mirror.isNullOrEmpty()) {
        remove(SettingsKeys.ANNAS_PREFERRED_MIRROR)
    } else {
        this[SettingsKeys.ANNAS_PREFERRED_MIRROR] = mirror
    }
    writeOrRemove(SettingsKeys.ANNAS_DOWNLOADS_LEFT, settings.annasDownloadsLeft)
    writeOrRemove(SettingsKeys.ANNAS_DOWNLOADS_PER_DAY, settings.annasDownloadsPerDay)
}

private fun MutablePreferences.writeOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) remove(key) else this[key] = value
}

internal fun Preferences.toConversionSettings(): ConversionSettings {
    val defaults = ConversionSettings()
    return ConversionSettings(
        autoConvertPdf = this[SettingsKeys.CONVERSION_AUTO] ?: defaults.autoConvertPdf,
        removeHeadersAndFooters = this[SettingsKeys.CONVERSION_STRIP_HEADERS]
            ?: defaults.removeHeadersAndFooters,
        headingSensitivity = enumOrDefault(
            this[SettingsKeys.CONVERSION_HEADING_SENSITIVITY],
            defaults.headingSensitivity
        ),
        extractImages = this[SettingsKeys.CONVERSION_EXTRACT_IMAGES] ?: defaults.extractImages
    )
}

internal fun MutablePreferences.write(settings: ConversionSettings) {
    this[SettingsKeys.CONVERSION_AUTO] = settings.autoConvertPdf
    this[SettingsKeys.CONVERSION_STRIP_HEADERS] = settings.removeHeadersAndFooters
    this[SettingsKeys.CONVERSION_HEADING_SENSITIVITY] = settings.headingSensitivity.name
    this[SettingsKeys.CONVERSION_EXTRACT_IMAGES] = settings.extractImages
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: default
