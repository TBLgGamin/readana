package dev.readana.engine.pdf2epub

import android.graphics.Bitmap
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.ByteArrayOutputStream

/**
 * The conversion corpus, built with PDFBox rather than checked in as binaries.
 *
 * Real-world PDFs would be checked in, but every candidate carries a licence to track and a few
 * hundred kilobytes of repository weight, and their goldens churn whenever the source is revised.
 * Generating them keeps the corpus deterministic and free, and the conversion still runs through
 * the same PDFBox extraction path as a downloaded book. The trade-off is that these pages are
 * tidier than a real scan or a real typesetter's output; see ADR 0012.
 */
internal object CorpusPdfs {
    private val PAGE_SIZE = PDRectangle(612f, 792f)
    private const val BODY_SIZE = 10f
    private const val HEADING_SIZE = 18f
    private const val SUBHEADING_SIZE = 13f
    private const val LINE_HEIGHT = 14f
    private const val MARGIN = 60f

    /** A short novel: chapter headings, running headers and page numbers, hyphenated wrapping. */
    fun novel(): ByteArray = document { document ->
        listOf("The Voyage Out" to 0, "The Storm" to 1, "Landfall" to 2).forEach { (title, index) ->
            page(document) { stream, page ->
                runningHeader(stream, "A Test Novel", page)
                var y = 700f
                y = write(stream, title, MARGIN, y, PDType1Font.HELVETICA_BOLD, HEADING_SIZE)
                y -= LINE_HEIGHT
                bodyParagraph(
                    stream,
                    y,
                    listOf(
                        "The ship left the harbour before dawn and the crew went about their",
                        "work in the half light, coiling rope and checking every fasten-",
                        "ing twice over as the swell began to lift beneath the hull."
                    )
                )
                pageNumber(stream, index + 1)
            }
        }
    }

    /** A two-column paper: a spanning title, then body text in two columns, plus a figure. */
    fun twoColumnPaper(): ByteArray = document { document ->
        page(document) { stream, _ ->
            write(
                stream,
                "On the Reflow of Multi-Column Documents",
                MARGIN,
                720f,
                PDType1Font.HELVETICA_BOLD,
                HEADING_SIZE
            )
            column(stream, x = MARGIN, top = 660f, side = "left")
            column(stream, x = 330f, top = 660f, side = "right")
        }
        page(document) { stream, _ ->
            column(stream, x = MARGIN, top = 720f, side = "left")
            column(stream, x = 330f, top = 720f, side = "right")
            image(document, stream, x = MARGIN, y = 200f, width = 470f, height = 120f)
        }
    }

    /** A report whose bookmarks, not its typography, define the chapters. */
    fun outlinedReport(): ByteArray = document { document ->
        val pages = listOf(
            "Introduction" to "This report sets out what was measured and why it matters here.",
            "Method" to "Measurements were repeated three times under identical conditions.",
            "Results" to "Every run agreed to within the tolerance stated in the method."
        ).map { (title, body) ->
            page(document) { stream, page ->
                runningHeader(stream, "Annual Report", page)
                var y = 700f
                y = write(stream, title, MARGIN, y, PDType1Font.HELVETICA_BOLD, SUBHEADING_SIZE)
                y -= LINE_HEIGHT
                bodyParagraph(stream, y, listOf(body, "A second line keeps the paragraph a real width."))
            } to title
        }

        val outline = PDDocumentOutline()
        pages.forEach { (page, title) ->
            outline.addLast(
                PDOutlineItem().apply {
                    this.title = title
                    setDestination(page)
                }
            )
        }
        document.documentCatalog.documentOutline = outline
    }

    /**
     * The same image XObject drawn on every page, the way a logo or a chapter ornament is. PDFBox
     * caches and reuses that bitmap, so anything which frees it breaks every later page.
     */
    fun repeatedLogo(pages: Int = 3): ByteArray = document { document ->
        val logo = LosslessFactory.createFromImage(document, solidBitmap())
        repeat(pages) { index ->
            page(document) { stream, _ ->
                stream.drawImage(logo, MARGIN, 700f, 80f, 40f)
                bodyParagraph(
                    stream,
                    640f,
                    listOf(
                        "Page ${index + 1} carries the same logo as every other page,",
                        "which is exactly the case that used to break conversion."
                    )
                )
            }
        }
    }

    /** An illustrated page: text, a figure, more text. */
    fun illustrated(): ByteArray = document { document ->
        page(document) { stream, _ ->
            bodyParagraph(
                stream,
                720f,
                listOf(
                    "The diagram below shows the arrangement described in the text",
                    "above it, drawn to scale and labelled for reference."
                )
            )
            image(document, stream, x = MARGIN, y = 400f, width = 240f, height = 180f)
            bodyParagraph(
                stream,
                340f,
                listOf(
                    "Beneath the figure the discussion resumes and runs on for a",
                    "further couple of lines so it reads as ordinary body text."
                )
            )
        }
    }

    private fun document(build: (PDDocument) -> Unit): ByteArray = PDDocument().use { document ->
        build(document)
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun page(document: PDDocument, draw: (PDPageContentStream, PDPage) -> Unit): PDPage {
        val page = PDPage(PAGE_SIZE)
        document.addPage(page)
        PDPageContentStream(document, page).use { stream -> draw(stream, page) }
        return page
    }

    private fun column(stream: PDPageContentStream, x: Float, top: Float, side: String) {
        var y = top
        repeat(COLUMN_LINES) { index ->
            y = write(
                stream,
                "The $side column line $index carries enough words to measure.",
                x,
                y,
                PDType1Font.HELVETICA,
                BODY_SIZE
            )
        }
    }

    private fun bodyParagraph(stream: PDPageContentStream, top: Float, lines: List<String>) {
        var y = top
        lines.forEach { line -> y = write(stream, line, MARGIN, y, PDType1Font.HELVETICA, BODY_SIZE) }
    }

    private fun runningHeader(stream: PDPageContentStream, text: String, page: PDPage) {
        write(stream, text, MARGIN, page.mediaBox.height - 30f, PDType1Font.HELVETICA, 8f)
    }

    private fun pageNumber(stream: PDPageContentStream, number: Int) {
        write(stream, number.toString(), 300f, 30f, PDType1Font.HELVETICA, 8f)
    }

    private fun write(stream: PDPageContentStream, text: String, x: Float, y: Float, font: PDFont, size: Float): Float {
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
        return y - LINE_HEIGHT
    }

    private fun solidBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(IMAGE_PX, IMAGE_PX / 2, Bitmap.Config.ARGB_8888)
        for (px in 0 until bitmap.width) {
            for (py in 0 until bitmap.height) {
                bitmap.setPixel(px, py, if (px < bitmap.width / 2) Color.DKGRAY else Color.LTGRAY)
            }
        }
        return bitmap
    }

    /** A flat two-colour bitmap: small, deterministic, and a real image XObject once embedded. */
    private fun image(
        document: PDDocument,
        stream: PDPageContentStream,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val bitmap = solidBitmap()
        val xObject = LosslessFactory.createFromImage(document, bitmap)
        stream.drawImage(xObject, x, y, width, height)
        bitmap.recycle()
    }

    private const val COLUMN_LINES = 8
    private const val IMAGE_PX = 64
}
