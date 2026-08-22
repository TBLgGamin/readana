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
    /** Size of the file as it arrived, kept stable across conversion so duplicate checks still work. */
    val sizeBytes: Long?,
    val addedAt: Instant,
    val lastOpenedAt: Instant?,
    val progress: ReadingProgress?,
    val origin: BookOrigin?,
    /** Set once a PDF has been converted; the original file is kept alongside the EPUB. */
    val conversion: ConversionInfo? = null,
    /** Read to the end, or put down and called done. Set by the reader, not by the progress bar. */
    val finishedAt: Instant? = null,
    /** Taken from the book's own metadata at import; null for books that belong to no series. */
    val series: BookSeries? = null
) {
    val authorsLabel: String
        get() = authors.joinToString(", ")

    /**
     * Where this book stands.
     *
     * [ReadingStatus.Finished] the moment the reader says so, and also once the last few pages have
     * gone by — an EPUB's back matter means the position rarely reaches the very end, and a book
     * stuck at "97% read" for ever is a nuisance rather than an accurate record.
     */
    val readingStatus: ReadingStatus
        get() = when {
            finishedAt != null -> ReadingStatus.Finished
            progress == null -> ReadingStatus.Unread
            progress.fraction >= FINISHED_FRACTION -> ReadingStatus.Finished
            else -> ReadingStatus.Reading
        }

    companion object {
        /** Close enough to the end to call it read. */
        const val FINISHED_FRACTION = 0.98f
    }
}

/** A book's place in a series, as the publisher recorded it. */
data class BookSeries(val name: String, val index: Float?)

/** Where a book stands, derived from how far the reader has got. */
enum class ReadingStatus { Unread, Reading, Finished }

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
