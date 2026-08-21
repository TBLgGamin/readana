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
import dev.readana.engine.reflow.ReflowOptions
import dev.readana.engine.reflow.model.ContentExtractor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Where a conversion has got to. Extraction dominates, which is why it reports per page. */
enum class ConversionPhase { Extracting, Reflowing, Assembling, Writing }

data class ConversionProgress(val phase: ConversionPhase, val currentPage: Int = 0, val totalPages: Int = 0)

/** What a finished conversion produced, for the details sheet and the golden tests. */
data class ConversionReport(
    val pages: Int,
    val blocks: Int,
    val headings: Int,
    val chapters: Int,
    val images: Int,
    val multiColumnPages: Int,
    /** True when the PDF's own bookmarks defined the chapters, rather than heading heuristics. */
    val outlineUsed: Boolean,
    val droppedNavEntries: Int,
    val warnings: List<String>,
    val phaseDurationsMillis: Map<ConversionPhase, Long>
) {
    val durationMillis: Long get() = phaseDurationsMillis.values.sum()
}

/**
 * End-to-end PDF → EPUB conversion: extract positioned text, figures and the outline, reflow it
 * into paragraphs and headings, then package the result as EPUB 3. Scanned PDFs (no text layer)
 * are rejected — OCR is out of scope for now.
 *
 * Blocking: call from `Dispatchers.IO`.
 */
class PdfToEpubConverter(
    private val extractor: ContentExtractor,
    private val writer: EpubWriter = EpubWriter(),
    /** Injectable so tests are not at the mercy of the wall clock. */
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Suppress("ReturnCount")
    fun convert(
        input: InputStream,
        output: OutputStream,
        metadata: EpubMetadata,
        options: ReflowOptions = ReflowOptions(),
        onProgress: (ConversionProgress) -> Unit = {}
    ): AppResult<ConversionReport> {
        val timings = mutableMapOf<ConversionPhase, Long>()

        onProgress(ConversionProgress(ConversionPhase.Extracting))
        val document = try {
            timed(ConversionPhase.Extracting, timings) {
                extractor.extract(input) { index, count ->
                    onProgress(ConversionProgress(ConversionPhase.Extracting, index + 1, count))
                }
            }
        } catch (e: IOException) {
            return AppError.Parse("Could not read the PDF: ${e.message ?: "unknown error"}", e).asFailure()
        }
        if (document.pages.isEmpty()) return AppError.Unsupported("The PDF has no pages").asFailure()

        onProgress(ConversionProgress(ConversionPhase.Reflowing, document.pages.size, document.pages.size))
        val reflowed = timed(ConversionPhase.Reflowing, timings) {
            ReflowEngine(options).reflow(document)
        }
        if (reflowed.blocks.isEmpty()) {
            return AppError.Unsupported(
                "The PDF has no extractable text (scanned document?); OCR is not supported"
            ).asFailure()
        }

        onProgress(ConversionProgress(ConversionPhase.Assembling, document.pages.size, document.pages.size))
        val assembly = timed(ConversionPhase.Assembling, timings) {
            EpubAssembler.assemble(reflowed.blocks, metadata, document.outline)
        }

        onProgress(ConversionProgress(ConversionPhase.Writing, document.pages.size, document.pages.size))
        timed(ConversionPhase.Writing, timings) { writer.write(assembly.book, output) }

        return ConversionReport(
            pages = document.pages.size,
            blocks = reflowed.blocks.size,
            headings = reflowed.blocks.count { it is Block.Heading },
            chapters = assembly.book.chapters.size,
            images = assembly.book.resources.size,
            multiColumnPages = reflowed.multiColumnPages,
            outlineUsed = assembly.outlineUsed,
            droppedNavEntries = assembly.droppedNavEntries,
            warnings = extractorWarnings(),
            phaseDurationsMillis = timings
        ).asSuccess()
    }

    private fun extractorWarnings(): List<String> = (extractor as? PdfBoxContentExtractor)?.warnings.orEmpty()

    private inline fun <T> timed(
        phase: ConversionPhase,
        timings: MutableMap<ConversionPhase, Long>,
        block: () -> T
    ): T {
        val started = clock()
        val result = block()
        timings[phase] = clock() - started
        return result
    }
}
