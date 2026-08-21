package dev.readana.core.model

import java.time.Instant

/** How a highlight is drawn over the text. */
enum class HighlightStyle {
    Highlight,
    Underline
}

/** The colours a highlight can take; the exact ARGB value is a UI concern. */
enum class HighlightTint {
    Yellow,
    Green,
    Blue,
    Pink,
    Purple
}

/** A marked passage in a book, optionally with a note attached. */
data class Highlight(
    val id: Long,
    val bookId: String,
    val locatorJson: String,
    /** The marked text, kept so highlights can be listed without opening the book. */
    val text: String,
    val tint: HighlightTint,
    val style: HighlightStyle,
    val note: String?,
    val createdAt: Instant
) {
    companion object {
        const val UNSAVED_ID = 0L
    }
}
