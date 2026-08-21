package dev.readana.engine.reflow.model

import java.io.InputStream

/**
 * An embedded raster image, already decoded and re-encoded by the extractor. Coordinates match
 * [PageText]: PDF points, origin top-left, y growing downwards.
 *
 * Not a data class: it holds a [ByteArray], whose equality would be identity anyway.
 */
class PageImage(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val bytes: ByteArray,
    /** `image/jpeg` or `image/png`. */
    val mediaType: String,
    val pixelWidth: Int,
    val pixelHeight: Int
) {
    val bottom: Float get() = y + height

    val right: Float get() = x + width
}

/** Everything one page carries. */
data class PageContent(val text: PageText, val images: List<PageImage> = emptyList())

/** One entry of the PDF outline (its bookmark tree), resolved to a page. */
data class OutlineNode(val title: String, val pageIndex: Int, val children: List<OutlineNode> = emptyList())

data class DocumentContent(val pages: List<PageContent>, val outline: List<OutlineNode> = emptyList())

/**
 * Produces positioned text, images and the outline for a fixed-layout document (implemented with
 * PDFBox on Android). Supersedes [TextExtractor].
 *
 * [onPage] is called after each page is read; extraction dominates conversion time, so this is
 * what drives the progress bar.
 */
interface ContentExtractor {
    fun extract(input: InputStream, onPage: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> }): DocumentContent
}
