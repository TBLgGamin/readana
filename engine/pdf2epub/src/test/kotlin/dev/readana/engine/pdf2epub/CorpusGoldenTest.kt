package dev.readana.engine.pdf2epub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dev.readana.core.common.result.AppResult
import dev.readana.engine.epub.EpubBook
import dev.readana.engine.epub.EpubMetadata
import dev.readana.engine.epub.EpubWriter
import dev.readana.engine.epub.NavEntry
import dev.readana.engine.reflow.Block
import dev.readana.engine.reflow.EpubAssembler
import dev.readana.engine.reflow.ReflowEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Snapshots of what the converter makes of each corpus PDF.
 *
 * The goldens hold extracted *content* — the block list, the table of contents and the report —
 * never the EPUB bytes. Zip output is already asserted to be reproducible in `engine:epub`, while
 * image encoders differ between Robolectric and a device, so byte goldens would fail for reasons
 * that have nothing to do with the conversion.
 */
@RunWith(AndroidJUnit4::class)
class CorpusGoldenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** PDFBox loads its standard fonts from assets, which needs the resource loader first. */
    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun `novel with chapter headings and running headers`() {
        check("novel", CorpusPdfs.novel())
    }

    @Test
    fun `two-column paper with a spanning title and a figure`() {
        check("two-column-paper", CorpusPdfs.twoColumnPaper())
    }

    @Test
    fun `report whose bookmarks define the chapters`() {
        check("outlined-report", CorpusPdfs.outlinedReport())
    }

    @Test
    fun `illustrated page with a figure between paragraphs`() {
        check("illustrated", CorpusPdfs.illustrated())
    }

    /**
     * epubcheck cannot run here: it and Robolectric fight over the classloader. EPUB validity is
     * asserted in `engine:epub`, which validates the same structures (nested navigation, image
     * resources) on the pure-JVM side. What is worth checking end to end is that a real PDF comes
     * out as a package with the parts the reader needs.
     */
    @Test
    fun `a converted corpus pdf is a complete epub package`() {
        val file = temporaryFolder.newFile("corpus.epub")
        file.outputStream().use { output ->
            val outcome = converter().convert(
                CorpusPdfs.illustrated().inputStream(),
                output,
                metadata()
            )
            assertIs<AppResult.Success<ConversionReport>>(outcome)
        }

        val entries = mutableListOf<String>()
        ZipInputStream(file.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries += it.name }
        }

        assertEquals("mimetype", entries.first(), "the OCF spec requires mimetype first")
        assertTrue(entries.containsAll(listOf("META-INF/container.xml", "OEBPS/content.opf", "OEBPS/nav.xhtml")))
        assertTrue(entries.any { it.startsWith("OEBPS/text/") }, "no chapters in $entries")
        assertTrue(entries.any { it.startsWith("OEBPS/images/") }, "the figure was not packaged: $entries")
    }

    /**
     * Regression: the collector used to recycle the bitmap PDFBox handed it. PDFBox caches that
     * bitmap per image XObject, so the second page drawing the same logo got a freed one back and
     * the whole conversion died with "Can't compress a recycled bitmap" — after processing every
     * page, which made it look like a late failure rather than an image problem.
     */
    @Test
    fun `an image repeated on every page converts instead of failing`() {
        val outcome = converter().convert(
            CorpusPdfs.repeatedLogo(pages = 3).inputStream(),
            ByteArrayOutputStream(),
            metadata()
        )

        val report = assertIs<AppResult.Success<ConversionReport>>(outcome, "$outcome").value
        assertEquals(3, report.pages)
        assertEquals(1, report.images, "the same logo should be stored once, not per page")
        assertTrue(report.warnings.none { "recycled" in it.lowercase() }, report.warnings.toString())
    }

    @Test
    fun `the running header and page number are stripped from the novel`() {
        val blocks = blocksOf(CorpusPdfs.novel())

        assertTrue(blocks.none { "A Test Novel" in it.text }, "running header survived: $blocks")
        assertTrue(blocks.none { it.text.trim() == "1" }, "page number survived")
    }

    /** Converts [pdf] and compares its content, contents list and report against the goldens. */
    private fun check(name: String, pdf: ByteArray) {
        val document = extractor().extract(pdf.inputStream())
        val reflowed = ReflowEngine().reflow(document)
        val assembly = EpubAssembler.assemble(reflowed.blocks, metadata(), document.outline)

        assertGolden("$name.blocks.txt", renderBlocks(reflowed.blocks))
        assertGolden("$name.nav.txt", renderNav(assembly.book))
        assertGolden("$name.report.txt", renderReport(name, pdf))
    }

    private fun renderBlocks(blocks: List<Block>): String = blocks.joinToString("\n") { block ->
        when (block) {
            is Block.Heading -> "H${block.level}|p${block.pageIndex}|${block.text}"

            is Block.Paragraph -> "P|p${block.pageIndex}|${block.text}"

            // Never the bytes: encoders differ between Robolectric and a device.
            is Block.Image ->
                "IMG|p${block.pageIndex}|${block.mediaType}|" +
                    "${block.pixelWidth}x${block.pixelHeight}"
        }
    }

    private fun renderNav(book: EpubBook): String {
        val entries = book.navigation ?: book.chapters.map { NavEntry(it.title, "text/${it.id}.xhtml") }
        return buildString { appendEntries(entries, depth = 0) }.trimEnd()
    }

    private fun StringBuilder.appendEntries(entries: List<NavEntry>, depth: Int) {
        entries.forEach { entry ->
            append("  ".repeat(depth)).append(entry.title).append(" -> ").append(entry.href).append('\n')
            appendEntries(entry.children, depth + 1)
        }
    }

    /** Durations and byte totals are left out: they are not reproducible. */
    private fun renderReport(name: String, pdf: ByteArray): String {
        val outcome = converter().convert(pdf.inputStream(), ByteArrayOutputStream(), metadata())
        val report = assertIs<AppResult.Success<ConversionReport>>(outcome, name).value
        return listOf(
            "pages=${report.pages}",
            "blocks=${report.blocks}",
            "headings=${report.headings}",
            "chapters=${report.chapters}",
            "images=${report.images}",
            "multiColumnPages=${report.multiColumnPages}",
            "outlineUsed=${report.outlineUsed}",
            "droppedNavEntries=${report.droppedNavEntries}",
            "warnings=${report.warnings}"
        ).joinToString("\n")
    }

    private fun blocksOf(pdf: ByteArray): List<Block> =
        ReflowEngine().reflow(extractor().extract(pdf.inputStream())).blocks

    private fun extractor() = PdfBoxContentExtractor(context)

    private fun converter() = PdfToEpubConverter(extractor(), EpubWriter()) { 0L }

    /** Pinned so nothing in the output depends on the clock or a random UUID. */
    private fun metadata() = EpubMetadata(
        title = "Corpus",
        identifier = "urn:uuid:00000000-0000-4000-8000-000000000000",
        modified = Instant.EPOCH
    )

    private fun assertGolden(fileName: String, actual: String) {
        val file = File(goldenDir, fileName)
        if (updateGoldens) {
            file.parentFile?.mkdirs()
            file.writeText(actual + "\n")
            updated += fileName
            return
        }
        assertTrue(file.isFile, "Missing golden $fileName — regenerate with -PupdateGoldens=true")
        assertEquals(
            file.readText().trimEnd(),
            actual.trimEnd(),
            "$fileName differs; if the change is intended, regenerate with -PupdateGoldens=true"
        )
    }

    private val goldenDir: File
        get() = File(System.getProperty("readana.moduleDir").orEmpty(), GOLDEN_PATH)

    private val updateGoldens: Boolean
        get() = System.getProperty("readana.updateGoldens") == "true"

    companion object {
        private const val GOLDEN_PATH = "src/test/resources/corpus"
        private val updated = mutableSetOf<String>()

        @JvmStatic
        @org.junit.AfterClass
        fun failIfRegenerated() {
            check(updated.isEmpty()) {
                "Goldens rewritten (${updated.sorted()}). Re-run without -PupdateGoldens to verify."
            }
        }
    }
}
