package dev.readana.engine.pdf2epub

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.engine.epub.EpubMetadata
import dev.readana.engine.epub.EpubWriter
import dev.readana.engine.reflow.Block
import dev.readana.engine.reflow.EpubAssembler
import dev.readana.engine.reflow.ReflowEngine
import dev.readana.engine.reflow.model.TextExtractor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * End-to-end PDF → EPUB conversion: extract positioned text, reflow it into paragraphs and
 * headings, then package the result as EPUB 3. Scanned PDFs (no text layer) are rejected —
 * OCR is out of scope for now.
 *
 * Blocking: call from `Dispatchers.IO`.
 */
class PdfToEpubConverter(
    private val extractor: TextExtractor,
    private val engine: ReflowEngine = ReflowEngine(),
    private val writer: EpubWriter = EpubWriter()
) {
    data class Report(val pages: Int, val blocks: Int, val headings: Int, val chapters: Int)

    fun convert(input: InputStream, output: OutputStream, metadata: EpubMetadata): AppResult<Report> {
        val pages = try {
            extractor.extract(input)
        } catch (e: IOException) {
            return AppError.Parse(
                "Could not read the PDF: ${e.message ?: "unknown error"}",
                e
            ).asFailure()
        }
        if (pages.isEmpty()) return AppError.Unsupported("The PDF has no pages").asFailure()

        val blocks = engine.reflow(pages)
        if (blocks.isEmpty()) {
            return AppError.Unsupported(
                "The PDF has no extractable text (scanned document?); OCR is not supported"
            )
                .asFailure()
        }

        val book = EpubAssembler.assemble(blocks, metadata)
        writer.write(book, output)
        return Report(
            pages = pages.size,
            blocks = blocks.size,
            headings = blocks.count { it is Block.Heading },
            chapters = book.chapters.size
        ).asSuccess()
    }
}
