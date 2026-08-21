package dev.readana.core.model

/** User preferences for the reading screen; persisted in DataStore. */
data class ReaderSettings(
    val fontScale: Float = 1.0f,
    val fontFamily: ReaderFont = ReaderFont.Publisher,
    val theme: ReaderTheme = ReaderTheme.System,
    val lineHeight: Float = 1.4f,
    val pageMargins: Float = 1.0f,
    val pageTurnMode: PageTurnMode = PageTurnMode.Paginated,
    val tapToTurnPages: Boolean = true,
    val volumeKeysTurnPages: Boolean = false,
    val keepScreenOn: Boolean = true
) {
    companion object {
        val FONT_SCALE_RANGE = 0.6f..2.5f
        val LINE_HEIGHT_RANGE = 1.0f..2.2f
        val PAGE_MARGIN_RANGE = 0.0f..3.0f
    }
}

enum class ReaderFont {
    Publisher,
    Serif,
    SansSerif,
    Monospace
}

enum class ReaderTheme {
    System,
    Light,
    Sepia,
    Dark,
    Black
}

enum class PageTurnMode {
    Paginated,
    Scrolled
}
