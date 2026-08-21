package dev.readana.core.model

import java.time.Instant

/** A book that lives in the on-device library. */
data class Book(
    val id: String,
    val title: String,
    val authors: List<String>,
    val language: String?,
    val format: BookFormat,
    /** `content://` or `file://` URI of the readable file (always EPUB once conversion has run). */
    val fileUri: String,
    val coverUri: String?,
    val sizeBytes: Long?,
    val addedAt: Instant,
    val lastOpenedAt: Instant?,
    val progress: ReadingProgress?,
    val origin: BookOrigin?
) {
    val authorsLabel: String
        get() = authors.joinToString(", ")
}

/** Where a library entry came from, so it can be re-downloaded or de-duplicated later. */
data class BookOrigin(
    val sourceId: String,
    val recordId: String,
    /** Format as downloaded; differs from [Book.format] when a PDF was converted to EPUB. */
    val originalFormat: BookFormat
)

/** Reading position expressed as a Readium locator (JSON) plus a coarse overall fraction. */
data class ReadingProgress(val locatorJson: String, val fraction: Float) {
    init {
        require(fraction in 0f..1f) { "fraction must be within 0..1 but was $fraction" }
    }
}
