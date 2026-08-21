package dev.readana.feature.reader

import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

/** Readium has no pitch-black theme, so black is dark with the colours overridden. */
private const val BLACK_BACKGROUND = 0xFF000000
private const val BLACK_TEXT = 0xFFB8B8B8

/** Readium page margins run from half to double; our 0..3 scale maps onto that. */
private const val MARGIN_BASE = 0.5
private const val MARGIN_STEP = 0.5

/** Readium ignores line height while publisher styles are on. */
private const val MAX_LINE_HEIGHT = 2.0

/**
 * Maps the app's reading settings onto EPUB navigator preferences. [systemInDarkTheme] resolves
 * [ReaderTheme.System], which Readium itself has no notion of.
 */
internal fun ReaderSettings.toEpubPreferences(systemInDarkTheme: Boolean): EpubPreferences {
    val family = fontFamily.toReadiumFontFamily()
    val resolvedTheme = theme.resolve(systemInDarkTheme)
    return EpubPreferences(
        backgroundColor = if (theme == ReaderTheme.Black) Color(BLACK_BACKGROUND.toInt()) else null,
        columnCount = ColumnCount.AUTO,
        fontFamily = family,
        fontSize = fontScale.toDouble(),
        lineHeight = lineHeight.toDouble().coerceAtMost(MAX_LINE_HEIGHT),
        pageMargins = MARGIN_BASE + pageMargins * MARGIN_STEP,
        // Publisher styles win over our typography settings, so they go once anything is customised.
        publisherStyles = family == null && lineHeight == ReaderSettings().lineHeight,
        scroll = pageTurnMode == PageTurnMode.Scrolled,
        textColor = if (theme == ReaderTheme.Black) Color(BLACK_TEXT.toInt()) else null,
        theme = resolvedTheme
    )
}

/** PDFs get the reading direction and a page fit; typography does not apply to them. */
internal fun ReaderSettings.toPdfiumPreferences(): PdfiumPreferences = PdfiumPreferences(
    fit = Fit.CONTAIN,
    scrollAxis = if (pageTurnMode == PageTurnMode.Scrolled) Axis.VERTICAL else Axis.HORIZONTAL
)

private fun ReaderFont.toReadiumFontFamily(): FontFamily? = when (this) {
    ReaderFont.Publisher -> null
    ReaderFont.Serif -> FontFamily.SERIF
    ReaderFont.SansSerif -> FontFamily.SANS_SERIF
    ReaderFont.Monospace -> FontFamily.MONOSPACE
}

private fun ReaderTheme.resolve(systemInDarkTheme: Boolean): Theme = when (this) {
    ReaderTheme.System -> if (systemInDarkTheme) Theme.DARK else Theme.LIGHT
    ReaderTheme.Light -> Theme.LIGHT
    ReaderTheme.Sepia -> Theme.SEPIA
    ReaderTheme.Dark, ReaderTheme.Black -> Theme.DARK
}
