package dev.readana.core.model

import java.time.Instant

/**
 * A book that was converted keeps one library entry: [Book.fileUri] points at the EPUB the reader
 * opens, and this records where the original came from so it can be re-converted or opened as-is.
 */
data class ConversionInfo(val originalFileUri: String, val convertedAt: Instant, val report: ConversionReport?)

/** A summary of what the converter made of a PDF, shown in the book details sheet. */
data class ConversionReport(
    val pages: Int,
    val blocks: Int,
    val headings: Int,
    val chapters: Int,
    val images: Int,
    val multiColumnPages: Int,
    val outlineUsed: Boolean,
    val warnings: List<String> = emptyList(),
    val durationMillis: Long = 0
)
