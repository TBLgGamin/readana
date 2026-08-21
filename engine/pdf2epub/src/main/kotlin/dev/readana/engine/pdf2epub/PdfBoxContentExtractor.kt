package dev.readana.engine.pdf2epub

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dev.readana.engine.reflow.model.ContentExtractor
import dev.readana.engine.reflow.model.DocumentContent
import dev.readana.engine.reflow.model.OutlineNode
import dev.readana.engine.reflow.model.PageContent
import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import dev.readana.engine.reflow.model.TextSpan
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

/** What the extractor should do with embedded images. */
data class PdfExtractionOptions(
    val extractImages: Boolean = true,
    /** Images are downscaled to fit this, so a 300-dpi scan does not bloat the EPUB. */
    val maxImageDimensionPx: Int = 1600,
    val jpegQuality: Int = 85,
    /** Anything smaller is a bullet, rule or spacer rather than a figure. */
    val minImageDimensionPx: Int = 24,
    val maxImagesPerDocument: Int = 300,
    val maxTotalImageBytes: Long = DEFAULT_MAX_TOTAL_IMAGE_BYTES
) {
    companion object {
        /** 40 MB of figures is already more than any book needs on a tablet. */
        const val DEFAULT_MAX_TOTAL_IMAGE_BYTES: Long = 40L * 1024 * 1024
    }
}

/**
 * [ContentExtractor] backed by PDFBox-Android: positioned text, embedded figures and the document
 * outline. Text lines carry spans split on font changes so the reflow engine can spot bold runs
 * and headings.
 *
 * Blocking: call from `Dispatchers.IO`.
 */
class PdfBoxContentExtractor(context: Context, private val options: PdfExtractionOptions = PdfExtractionOptions()) :
    ContentExtractor {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Warnings from the last [extract] call, e.g. figures in a codec Android cannot decode. */
    var warnings: List<String> = emptyList()
        private set

    override fun extract(input: InputStream, onPage: (pageIndex: Int, pageCount: Int) -> Unit): DocumentContent {
        val collectedWarnings = mutableListOf<String>()
        // Spills to a temp file above the threshold, so a large PDF does not sit entirely in RAM.
        val memory = MemoryUsageSetting.setupMixed(MAX_MEMORY_BYTES)
        return PDDocument.load(input, memory).use { document ->
            val images = ImageBudget(options, collectedWarnings)
            val count = document.numberOfPages
            val pages = (0 until count).map { index ->
                val page = extractPage(document, index, images)
                onPage(index, count)
                page
            }
            warnings = collectedWarnings.toList()
            DocumentContent(pages = pages, outline = readOutline(document, collectedWarnings))
        }
    }

    private fun extractPage(document: PDDocument, index: Int, images: ImageBudget): PageContent {
        val page = document.getPage(index)
        val mediaBox = page.mediaBox
        val stripper = LineCollectingStripper().apply {
            sortByPosition = true
            startPage = index + 1
            endPage = index + 1
        }
        stripper.getText(document)

        val figures = if (options.extractImages) {
            PageImageCollector(mediaBox.height, options, images).collect(page, index)
        } else {
            emptyList()
        }

        return PageContent(
            text = PageText(
                index = index,
                width = mediaBox.width,
                height = mediaBox.height,
                lines = stripper.lines
            ),
            images = figures
        )
    }

    /**
     * The PDF's bookmark tree. Entries whose destination cannot be resolved are skipped and their
     * children promoted, so one broken bookmark does not cost a whole branch.
     */
    private fun readOutline(document: PDDocument, warnings: MutableList<String>): List<OutlineNode> {
        val root = try {
            document.documentCatalog?.documentOutline
        } catch (e: IOException) {
            warnings += "Could not read the PDF outline: ${e.message ?: "unknown error"}"
            null
        } ?: return emptyList()
        return convert(root, document, depth = 0)
    }

    private fun convert(node: PDOutlineNode, document: PDDocument, depth: Int): List<OutlineNode> {
        if (depth > MAX_OUTLINE_DEPTH) return emptyList()
        val result = mutableListOf<OutlineNode>()
        var child: PDOutlineItem? = node.firstChild
        while (child != null) {
            val children = convert(child, document, depth + 1)
            val page = pageIndexOf(child, document)
            val title = child.title?.trim().orEmpty()
            if (page != null && title.isNotEmpty()) {
                result += OutlineNode(title, page, children)
            } else {
                result += children
            }
            child = child.nextSibling
        }
        return result
    }

    private fun pageIndexOf(item: PDOutlineItem, document: PDDocument): Int? = try {
        item.findDestinationPage(document)?.let { page: PDPage ->
            document.pages.indexOf(page).takeIf { it >= 0 }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private class LineCollectingStripper : PDFTextStripper() {
        val lines = mutableListOf<TextLine>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isEmpty()) return
            val spans = mutableListOf<TextSpan>()
            var runStart = 0
            for (i in 1 until textPositions.size) {
                if (!sameRun(textPositions[i - 1], textPositions[i])) {
                    spans += textPositions.subList(runStart, i).toSpan()
                    runStart = i
                }
            }
            spans += textPositions.subList(runStart, textPositions.size).toSpan()

            lines += TextLine(
                spans = spans,
                baseline = textPositions.maxOf { it.yDirAdj },
                height = textPositions.maxOf { it.heightDir }
            )
        }

        private fun sameRun(a: TextPosition, b: TextPosition): Boolean =
            abs(a.fontSizeInPt - b.fontSizeInPt) <= FONT_SIZE_TOLERANCE &&
                a.font?.name == b.font?.name

        private fun List<TextPosition>.toSpan(): TextSpan {
            val first = first()
            val last = last()
            return TextSpan(
                text = joinToString(separator = "") { it.unicode },
                x = first.xDirAdj,
                width = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj,
                fontSize = first.fontSizeInPt,
                fontName = first.font?.name
            )
        }

        private companion object {
            const val FONT_SIZE_TOLERANCE = 0.1f
        }
    }

    private companion object {
        const val MAX_MEMORY_BYTES = 50L * 1024 * 1024
        const val MAX_OUTLINE_DEPTH = 3
    }
}

/** Shared caps so one image-heavy document cannot fill the device. */
internal class ImageBudget(private val options: PdfExtractionOptions, val warnings: MutableList<String>) {
    private var count = 0
    private var bytes = 0L

    fun canTakeMore(): Boolean = count < options.maxImagesPerDocument && bytes < options.maxTotalImageBytes

    fun record(size: Int) {
        count++
        bytes += size
    }

    fun noteExhausted(pageIndex: Int) {
        if (count >= options.maxImagesPerDocument || bytes >= options.maxTotalImageBytes) {
            val message = "Stopped extracting images at page ${pageIndex + 1}: document image limit reached"
            if (message !in warnings) warnings += message
        }
    }
}
