package dev.readana.core.model

import java.time.Instant

/** A saved place in a book, expressed as a Readium locator like [ReadingProgress]. */
data class Bookmark(
    val id: Long,
    val bookId: String,
    val locatorJson: String,
    /** How far into the book the bookmark sits, when the locator knows. */
    val progression: Double?,
    /** Chapter or section the bookmark is in, when the locator knows. */
    val title: String?,
    val createdAt: Instant
) {
    companion object {
        /** Id for a bookmark that has not been stored yet; Room assigns the real one. */
        const val UNSAVED_ID = 0L
    }
}
