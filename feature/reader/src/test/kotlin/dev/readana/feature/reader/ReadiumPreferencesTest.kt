package dev.readana.feature.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

@RunWith(AndroidJUnit4::class)
class ReadiumPreferencesTest {
    @Test
    fun `maps every reader theme, resolving the system one`() {
        assertEquals(Theme.LIGHT, preferences(theme = ReaderTheme.Light).theme)
        assertEquals(Theme.SEPIA, preferences(theme = ReaderTheme.Sepia).theme)
        assertEquals(Theme.DARK, preferences(theme = ReaderTheme.Dark).theme)
        assertEquals(
            Theme.LIGHT,
            preferences(theme = ReaderTheme.System, systemInDarkTheme = false).theme
        )
        assertEquals(
            Theme.DARK,
            preferences(theme = ReaderTheme.System, systemInDarkTheme = true).theme
        )
    }

    @Test
    fun `black is dark with the colours overridden`() {
        val black = preferences(theme = ReaderTheme.Black)

        assertEquals(Theme.DARK, black.theme)
        assertNotNull(black.backgroundColor)
        assertNotNull(black.textColor)
    }

    @Test
    fun `other themes leave the colours to Readium`() {
        val dark = preferences(theme = ReaderTheme.Dark)

        assertNull(dark.backgroundColor)
        assertNull(dark.textColor)
    }

    @Test
    fun `maps typefaces and keeps publisher styles only for the publisher font`() {
        assertNull(preferences(font = ReaderFont.Publisher).fontFamily)
        assertEquals(true, preferences(font = ReaderFont.Publisher).publisherStyles)
        assertEquals(FontFamily.SERIF, preferences(font = ReaderFont.Serif).fontFamily)
        assertEquals(false, preferences(font = ReaderFont.Serif).publisherStyles)
        assertEquals(FontFamily.SANS_SERIF, preferences(font = ReaderFont.SansSerif).fontFamily)
        assertEquals(FontFamily.MONOSPACE, preferences(font = ReaderFont.Monospace).fontFamily)
    }

    @Test
    fun `paragraph spacing replaces the publisher's indent, and takes their styles with it`() {
        // Readium ignores both while publisher styles are on, so asking for a gap has to turn
        // them off — otherwise the setting silently does nothing.
        val spaced = preferences(paragraphSpacing = 0.6f)
        assertEquals(0.6, checkNotNull(spaced.paragraphSpacing), TOLERANCE)
        assertEquals(0.0, spaced.paragraphIndent)
        assertEquals(false, spaced.publisherStyles)

        val untouched = preferences(paragraphSpacing = 0f)
        assertEquals(0.0, checkNotNull(untouched.paragraphSpacing), TOLERANCE)
        assertNull(untouched.paragraphIndent, "nothing to say about indents when there is no gap")
        assertEquals(true, untouched.publisherStyles)
    }

    @Test
    fun `a custom line height also switches publisher styles off`() {
        assertEquals(false, preferences(lineHeight = 1.8f).publisherStyles)
    }

    @Test
    fun `maps margins onto the half-to-double range`() {
        assertEquals(0.5, assertNotNull(preferences(pageMargins = 0f).pageMargins), TOLERANCE)
        assertEquals(1.0, assertNotNull(preferences(pageMargins = 1f).pageMargins), TOLERANCE)
        assertEquals(2.0, assertNotNull(preferences(pageMargins = 3f).pageMargins), TOLERANCE)
    }

    @Test
    fun `caps line height at what Readium accepts`() {
        assertEquals(2.0, assertNotNull(preferences(lineHeight = 2.2f).lineHeight), TOLERANCE)
    }

    @Test
    fun `scrolled mode turns paging off`() {
        assertEquals(false, preferences(pageTurn = PageTurnMode.Paginated).scroll)
        assertEquals(true, preferences(pageTurn = PageTurnMode.Scrolled).scroll)
    }

    @Test
    fun `font scale becomes the font size`() {
        assertEquals(1.4, assertNotNull(preferences(fontScale = 1.4f).fontSize), TOLERANCE)
    }

    @Test
    fun `maps the column choice`() {
        assertEquals(ColumnCount.AUTO, preferences(columns = ReaderColumns.Auto).columnCount)
        assertEquals(ColumnCount.ONE, preferences(columns = ReaderColumns.One).columnCount)
        assertEquals(ColumnCount.TWO, preferences(columns = ReaderColumns.Two).columnCount)
    }

    @Test
    fun `PDFs get a reading axis and a page fit`() {
        val paginated = settings(pageTurn = PageTurnMode.Paginated).toPdfiumPreferences()
        val scrolled = settings(pageTurn = PageTurnMode.Scrolled).toPdfiumPreferences()

        assertEquals(Axis.HORIZONTAL, paginated.scrollAxis)
        assertEquals(Axis.VERTICAL, scrolled.scrollAxis)
        assertEquals(Fit.CONTAIN, paginated.fit)
    }

    private companion object {
        /** Reader settings are floats; Readium preferences are doubles. */
        const val TOLERANCE = 0.0001
    }

    private fun preferences(
        theme: ReaderTheme = ReaderTheme.Light,
        font: ReaderFont = ReaderFont.Publisher,
        fontScale: Float = 1f,
        lineHeight: Float = ReaderSettings().lineHeight,
        pageMargins: Float = 1f,
        pageTurn: PageTurnMode = PageTurnMode.Paginated,
        columns: ReaderColumns = ReaderColumns.Auto,
        paragraphSpacing: Float = 0f,
        systemInDarkTheme: Boolean = false
    ) = settings(theme, font, fontScale, lineHeight, pageMargins, pageTurn, columns, paragraphSpacing)
        .toEpubPreferences(systemInDarkTheme)

    private fun settings(
        theme: ReaderTheme = ReaderTheme.Light,
        font: ReaderFont = ReaderFont.Publisher,
        fontScale: Float = 1f,
        lineHeight: Float = ReaderSettings().lineHeight,
        pageMargins: Float = 1f,
        pageTurn: PageTurnMode = PageTurnMode.Paginated,
        columns: ReaderColumns = ReaderColumns.Auto,
        paragraphSpacing: Float = 0f
    ) = ReaderSettings(
        fontScale = fontScale,
        fontFamily = font,
        theme = theme,
        lineHeight = lineHeight,
        paragraphSpacing = paragraphSpacing,
        pageMargins = pageMargins,
        pageTurnMode = pageTurn,
        columns = columns
    )
}
