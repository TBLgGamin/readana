package dev.readana.core.model

/** User preferences for the reading screen; persisted in DataStore. */
data class ReaderSettings(
    val fontScale: Float = 1.0f,
    val fontFamily: ReaderFont = ReaderFont.Publisher,
    val theme: ReaderTheme = ReaderTheme.System,
    val lineHeight: Float = 1.4f,
    /**
     * Blank space between paragraphs, in ems. Publishers usually mark a new paragraph with an
     * indent and no gap, which is hard going on a backlit screen, so the default adds a gap and
     * drops the indent. Zero hands the decision back to the publisher's own styling.
     */
    val paragraphSpacing: Float = 0.6f,
    val pageMargins: Float = 1.0f,
    val pageTurnMode: PageTurnMode = PageTurnMode.Paginated,
    val columns: ReaderColumns = ReaderColumns.Auto,
    val tapToTurnPages: Boolean = true,
    val volumeKeysTurnPages: Boolean = false,
    val keepScreenOn: Boolean = true,
    /** How fast the reading voice speaks. A preference of the reader's, not of the book. */
    val ttsSpeed: Float = 1.0f
) {
    companion object {
        val FONT_SCALE_RANGE = 0.6f..2.5f
        val LINE_HEIGHT_RANGE = 1.0f..2.2f
        val PAGE_MARGIN_RANGE = 0.0f..3.0f
        val PARAGRAPH_SPACING_RANGE = 0.0f..2.0f
        val TTS_SPEED_RANGE = 0.5f..3.0f
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

/**
 * Columns per page. [Auto] adapts reflowable text to the window but keeps fixed-layout books, such
 * as comics, at one full-size page. The other values force one or two columns/pages.
 */
enum class ReaderColumns {
    Auto,
    One,
    Two
}
