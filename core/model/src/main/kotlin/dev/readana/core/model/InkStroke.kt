package dev.readana.core.model

import java.time.Instant

/**
 * A line drawn on a page with the pen.
 *
 * Where a stroke belongs is genuinely harder than where a highlight belongs. A highlight is a range
 * of text, so it survives any change of layout. A stroke is a shape over a place, and on a
 * reflowable EPUB that place moves the moment the font size or the margins change.
 *
 * So a stroke records both: the locator of the element it started on, and where it sat relative to
 * that element. That is enough to put it back when the layout is the same, and enough to know when
 * the layout is not — see [layoutFingerprint]. On a PDF or a fixed-layout EPUB the page never
 * reflows, and the stroke is always exact.
 */
data class InkStroke(
    val id: Long,
    val bookId: String,
    /** The resource the stroke was drawn on. */
    val resourceHref: String,
    /** The element the stroke started on, as a Readium locator. */
    val anchorLocatorJson: String,
    /** The stroke itself, normalised to the anchor's box so it survives a change of screen size. */
    val points: List<InkPoint>,
    val tint: HighlightTint,
    /**
     * The reading settings the stroke was drawn under. When the current settings differ, the text
     * underneath has moved and the stroke is shown but flagged, rather than silently drawn in the
     * wrong place.
     */
    val layoutFingerprint: String,
    val progression: Double? = null,
    val createdAt: Instant
) {
    companion object {
        const val UNSAVED_ID = 0L

        /**
         * What a stroke's position depends on. Anything here changing moves the text under the
         * ink; anything not here (the theme, keeping the screen on) does not.
         */
        fun fingerprintOf(settings: ReaderSettings): String = listOf(
            settings.fontScale,
            settings.fontFamily,
            settings.lineHeight,
            settings.paragraphSpacing,
            settings.pageMargins,
            settings.pageTurnMode,
            settings.columns
        ).joinToString("|")
    }
}

/** A point on a stroke, in fractions of the anchor's box, with the pressure it was drawn at. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f)
