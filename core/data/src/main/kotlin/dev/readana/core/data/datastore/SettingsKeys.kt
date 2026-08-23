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
import dev.readana.core.model.StoredQuota

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
    val PALM_REJECTION = booleanPreferencesKey("reader_palm_rejection")
    val PEN_MODE = stringPreferencesKey("reader_pen_mode")
    val TTS_SPEED = floatPreferencesKey("reader_tts_speed")

    val SOURCE_OVERRIDES = stringPreferencesKey("source_overrides")

    /** Every source's configuration, as one JSON object; see [SourceSettingsJson]. */
    val SOURCE_CONFIG = stringPreferencesKey("source_config_json")
    val SOURCE_QUOTAS = stringPreferencesKey("source_quotas_json")
    val SOURCE_CATALOGS = stringPreferencesKey("source_catalogs_json")

    // Written by builds before source configuration was generic. Read once, folded into
    // SOURCE_CONFIG, and then removed; see mergeLegacyAnnasConfig below.
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
        palmRejection = this[SettingsKeys.PALM_REJECTION] ?: defaults.palmRejection,
        penMode = enumOrDefault(this[SettingsKeys.PEN_MODE], defaults.penMode),
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
    this[SettingsKeys.PALM_REJECTION] = settings.palmRejection
    this[SettingsKeys.PEN_MODE] = settings.penMode.name
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
    config = mergeLegacyAnnasConfig(SourceSettingsJson.decodeConfig(this[SettingsKeys.SOURCE_CONFIG])),
    quotas = mergeLegacyAnnasQuota(SourceSettingsJson.decodeQuotas(this[SettingsKeys.SOURCE_QUOTAS])),
    catalogs = SourceSettingsJson.decodeCatalogs(this[SettingsKeys.SOURCE_CATALOGS])
)

/**
 * Source configuration used to be five fields on `SourceSettings`, all of them Anna's. Installs
 * from before that keep their keys until the next write, so they are folded in on read; anything
 * already in the generic map wins, because that is the newer value.
 */
private fun Preferences.mergeLegacyAnnasConfig(
    config: Map<String, Map<String, String>>
): Map<String, Map<String, String>> {
    val legacy = buildMap {
        this@mergeLegacyAnnasConfig[SettingsKeys.ANNAS_MIRRORS]
            ?.takeIf { it.isNotBlank() }
            ?.let { put(LegacySourceKeys.ANNAS_MIRRORS, it) }
        this@mergeLegacyAnnasConfig[SettingsKeys.ANNAS_SECRET_KEY]
            ?.takeIf { it.isNotBlank() }
            ?.let { put(LegacySourceKeys.ANNAS_SECRET_KEY, it) }
        this@mergeLegacyAnnasConfig[SettingsKeys.ANNAS_PREFERRED_MIRROR]
            ?.takeIf { it.isNotBlank() }
            ?.let { put(LegacySourceKeys.ANNAS_PREFERRED_MIRROR, it) }
    }
    if (legacy.isEmpty()) return config
    val merged = legacy + config[LegacySourceKeys.ANNAS_ID].orEmpty()
    return config + (LegacySourceKeys.ANNAS_ID to merged)
}

private fun Preferences.mergeLegacyAnnasQuota(quotas: Map<String, StoredQuota>): Map<String, StoredQuota> {
    if (quotas.containsKey(LegacySourceKeys.ANNAS_ID)) return quotas
    val left = this[SettingsKeys.ANNAS_DOWNLOADS_LEFT]
    val perDay = this[SettingsKeys.ANNAS_DOWNLOADS_PER_DAY]
    if (left == null && perDay == null) return quotas
    return quotas + (LegacySourceKeys.ANNAS_ID to StoredQuota(left, perDay))
}

internal fun MutablePreferences.write(settings: SourceSettings) {
    this[SettingsKeys.SOURCE_OVERRIDES] = settings.sourceOverrides.entries
        .joinToString(SettingsKeys.OVERRIDE_SEPARATOR) { (id, enabled) ->
            "$id${SettingsKeys.OVERRIDE_ASSIGNMENT}$enabled"
        }
    this[SettingsKeys.SOURCE_CONFIG] = SourceSettingsJson.encodeConfig(settings.config)
    this[SettingsKeys.SOURCE_QUOTAS] = SourceSettingsJson.encodeQuotas(settings.quotas)
    this[SettingsKeys.SOURCE_CATALOGS] = SourceSettingsJson.encodeCatalogs(settings.catalogs)

    // The old keys have been folded into the maps above by now; leaving them would let a stale
    // mirror list come back the next time the app reads settings.
    remove(SettingsKeys.ANNAS_MIRRORS)
    remove(SettingsKeys.ANNAS_SECRET_KEY)
    remove(SettingsKeys.ANNAS_PREFERRED_MIRROR)
    remove(SettingsKeys.ANNAS_DOWNLOADS_LEFT)
    remove(SettingsKeys.ANNAS_DOWNLOADS_PER_DAY)
}

/**
 * The one place that still names Anna's inside the data layer, and only so that settings written
 * by older builds can be found again.
 */
private object LegacySourceKeys {
    const val ANNAS_ID = "annas-archive"
    const val ANNAS_MIRRORS = "mirrors"
    const val ANNAS_SECRET_KEY = "secretKey"
    const val ANNAS_PREFERRED_MIRROR = "preferredMirror"
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
