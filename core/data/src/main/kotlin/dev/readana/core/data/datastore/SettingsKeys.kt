package dev.readana.core.data.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings

internal object SettingsKeys {
    val FONT_SCALE = floatPreferencesKey("reader_font_scale")
    val FONT_FAMILY = stringPreferencesKey("reader_font_family")
    val THEME = stringPreferencesKey("reader_theme")
    val LINE_HEIGHT = floatPreferencesKey("reader_line_height")
    val PAGE_MARGINS = floatPreferencesKey("reader_page_margins")
    val PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
    val COLUMNS = stringPreferencesKey("reader_columns")
    val TAP_TO_TURN = booleanPreferencesKey("reader_tap_to_turn")
    val VOLUME_KEYS = booleanPreferencesKey("reader_volume_keys")
    val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")

    val SOURCE_OVERRIDES = stringPreferencesKey("source_overrides")
    val ANNAS_MIRRORS = stringPreferencesKey("source_annas_mirrors")
    val ANNAS_SECRET_KEY = stringPreferencesKey("source_annas_secret_key")
    val ANNAS_DOWNLOADS_LEFT = intPreferencesKey("source_annas_downloads_left")
    val ANNAS_DOWNLOADS_PER_DAY = intPreferencesKey("source_annas_downloads_per_day")

    val LIBRARY_FOLDER_URI = stringPreferencesKey("library_folder_uri")

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
        pageMargins = this[SettingsKeys.PAGE_MARGINS] ?: defaults.pageMargins,
        pageTurnMode = enumOrDefault(this[SettingsKeys.PAGE_TURN_MODE], defaults.pageTurnMode),
        columns = enumOrDefault(this[SettingsKeys.COLUMNS], defaults.columns),
        tapToTurnPages = this[SettingsKeys.TAP_TO_TURN] ?: defaults.tapToTurnPages,
        volumeKeysTurnPages = this[SettingsKeys.VOLUME_KEYS] ?: defaults.volumeKeysTurnPages,
        keepScreenOn = this[SettingsKeys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn
    )
}

internal fun MutablePreferences.write(settings: ReaderSettings) {
    this[SettingsKeys.FONT_SCALE] = settings.fontScale
    this[SettingsKeys.FONT_FAMILY] = settings.fontFamily.name
    this[SettingsKeys.THEME] = settings.theme.name
    this[SettingsKeys.LINE_HEIGHT] = settings.lineHeight
    this[SettingsKeys.PAGE_MARGINS] = settings.pageMargins
    this[SettingsKeys.PAGE_TURN_MODE] = settings.pageTurnMode.name
    this[SettingsKeys.COLUMNS] = settings.columns.name
    this[SettingsKeys.TAP_TO_TURN] = settings.tapToTurnPages
    this[SettingsKeys.VOLUME_KEYS] = settings.volumeKeysTurnPages
    this[SettingsKeys.KEEP_SCREEN_ON] = settings.keepScreenOn
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
    writeOrRemove(SettingsKeys.ANNAS_DOWNLOADS_LEFT, settings.annasDownloadsLeft)
    writeOrRemove(SettingsKeys.ANNAS_DOWNLOADS_PER_DAY, settings.annasDownloadsPerDay)
}

private fun MutablePreferences.writeOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) remove(key) else this[key] = value
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: default
