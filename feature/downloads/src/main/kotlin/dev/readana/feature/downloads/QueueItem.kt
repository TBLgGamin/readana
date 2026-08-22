package dev.readana.feature.downloads

import dev.readana.core.model.DownloadJob
import dev.readana.engine.pdf2epub.ConversionPhase

/**
 * One thing the queue is working on.
 *
 * A book coming down the wire and a PDF being turned into an EPUB are the same thing to a reader:
 * work with a bar on it that ends with a book they can open. They share this screen so there is one
 * place to look, rather than a strip over the library for one and a page for the other.
 */
sealed interface QueueItem {
    /** Stable across emissions, so the list keeps its scroll position and animations. */
    val key: String

    /**
     * A download, and the conversion it started if it has got that far.
     *
     * The conversion is carried here rather than listed beside the download because it is the same
     * book at a later stage, not a second piece of work.
     */
    data class Download(
        val job: DownloadJob,
        val rate: DownloadRate? = null,
        val conversion: ActiveConversion? = null
    ) : QueueItem {
        override val key: String get() = "download:" + job.id
    }

    /** A conversion the reader started from a book already in the library. */
    data class Conversion(val conversion: ActiveConversion) : QueueItem {
        override val key: String get() = "conversion:" + conversion.bookId
    }
}

/** A PDF being turned into an EPUB right now. */
data class ActiveConversion(
    val bookId: String,
    val title: String,
    val coverUri: String? = null,
    val phase: ConversionPhase = ConversionPhase.Extracting,
    val currentPage: Int = 0,
    val totalPages: Int = 0
) {
    /**
     * How far through, while the extractor is still counting pages.
     *
     * The later phases report no page numbers at all, so the bar goes back to indeterminate rather
     * than sitting frozen at whatever the last page count worked out to.
     */
    val fraction: Float?
        get() = totalPages.takeIf { it > 0 }?.let { (currentPage.toFloat() / it).coerceIn(0f, 1f) }
}
