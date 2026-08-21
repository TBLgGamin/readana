package dev.readana.engine.pdf2epub

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextExtractor
import dev.readana.engine.reflow.model.TextLine
import dev.readana.engine.reflow.model.TextSpan
import java.io.InputStream
import kotlin.math.abs

/**
 * [TextExtractor] backed by PDFBox-Android. Produces one [TextLine] per visual line with spans
 * split on font changes so the reflow engine can detect bold runs and headings.
 *
 * Blocking: call from `Dispatchers.IO`.
 */
class PdfBoxTextExtractor(context: Context) : TextExtractor {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override fun extract(input: InputStream): List<PageText> = PDDocument.load(input).use { document ->
        (0 until document.numberOfPages).map { index -> extractPage(document, index) }
    }

    private fun extractPage(document: PDDocument, index: Int): PageText {
        val mediaBox = document.getPage(index).mediaBox
        val stripper = LineCollectingStripper().apply {
            sortByPosition = true
            startPage = index + 1
            endPage = index + 1
        }
        stripper.getText(document)
        return PageText(
            index = index,
            width = mediaBox.width,
            height = mediaBox.height,
            lines = stripper.lines
        )
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
}
