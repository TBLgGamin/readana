package dev.readana.engine.epub

import com.adobe.epubcheck.api.EpubCheck
import com.adobe.epubcheck.util.DefaultReportImpl
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class EpubWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val writer = EpubWriter()

    @Test
    fun `mimetype is the first entry, stored uncompressed at a fixed offset`() {
        val bytes = ByteArrayOutputStream().also { writer.write(sampleBook(), it) }.toByteArray()

        ZipInputStream(bytes.inputStream()).use { zip ->
            val first = assertNotNull(zip.nextEntry)
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertEquals(EpubWriter.MIMETYPE, String(zip.readBytes(), Charsets.US_ASCII))
        }
        // OCF: local header (30 bytes) + "mimetype" (8 bytes) → content at offset 38, no extra field.
        assertEquals(
            EpubWriter.MIMETYPE,
            String(bytes, MIMETYPE_OFFSET, EpubWriter.MIMETYPE.length, Charsets.US_ASCII)
        )
    }

    @Test
    fun `writes every chapter, the nav document and resources`() {
        val bytes = ByteArrayOutputStream().also { writer.write(sampleBook(), it) }.toByteArray()
        val names = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { names += it.name }
        }

        assertEquals(
            listOf(
                "mimetype",
                "META-INF/container.xml",
                "OEBPS/content.opf",
                "OEBPS/nav.xhtml",
                "OEBPS/styles/stylesheet.css",
                "OEBPS/text/ch-1.xhtml",
                "OEBPS/text/ch-2.xhtml",
                "OEBPS/images/cover.png"
            ),
            names
        )
    }

    @Test
    fun `escapes metadata and content`() {
        val opf = writer.packageDocument(sampleBook())

        assertTrue("<dc:title>Sample &amp; &lt;Test&gt;</dc:title>" in opf)
        assertTrue("<meta property=\"dcterms:modified\">2026-08-20T12:00:00Z</meta>" in opf)
        assertTrue("properties=\"cover-image\"" in opf)
    }

    @Test
    fun `output is byte-identical for identical input`() {
        val first = ByteArrayOutputStream().also { writer.write(sampleBook(), it) }.toByteArray()
        val second = ByteArrayOutputStream().also { writer.write(sampleBook(), it) }.toByteArray()

        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `generated epub passes epubcheck without errors`() {
        val file = temporaryFolder.newFile("sample.epub")
        file.outputStream().use { writer.write(sampleBook(), it) }

        val report = DefaultReportImpl(file.name)
        EpubCheck(file, report).doValidate()

        assertEquals(0, report.fatalErrorCount, "epubcheck reported fatal errors (see test output)")
        assertEquals(0, report.errorCount, "epubcheck reported errors (see test output)")
    }

    @Test
    fun `a nested table of contents with image resources passes epubcheck`() {
        val file = temporaryFolder.newFile("nested.epub")
        file.outputStream().use { writer.write(nestedNavBook(), it) }

        val report = DefaultReportImpl(file.name)
        EpubCheck(file, report).doValidate()

        assertEquals(0, report.fatalErrorCount, "epubcheck reported fatal errors (see test output)")
        assertEquals(0, report.errorCount, "epubcheck reported errors (see test output)")
    }

    @Test
    fun `nav renders children as a nested list`() {
        val nav = writer.navDocument(nestedNavBook())

        assertTrue("""<a href="text/ch-1.xhtml">Chapter One</a>""" in nav)
        assertTrue("""<a href="text/ch-1.xhtml#h-0001">A Section</a>""" in nav)
        // The child list is nested inside its parent's <li>, not appended after it.
        assertTrue(nav.indexOf("A Section") < nav.indexOf("Chapter Two"))
    }

    @Test
    fun `an empty table of contents is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            EpubBook(EpubMetadata(title = "x"), listOf(chapter("ch-1")), navigation = emptyList())
        }
    }

    @Test
    fun `rejects duplicate and reserved chapter ids`() {
        val metadata = EpubMetadata(title = "x")
        assertFailsWith<IllegalArgumentException> {
            EpubBook(metadata, listOf(chapter("same"), chapter("same")))
        }
        assertFailsWith<IllegalArgumentException> { chapter("nav") }
        assertFailsWith<IllegalArgumentException> { chapter("1-starts-with-digit") }
    }

    private fun chapter(id: String) = EpubChapter(id, "Title $id", Xhtml.paragraph("Body of $id"))

    private fun sampleBook(): EpubBook = EpubBook(
        metadata = EpubMetadata(
            title = "Sample & <Test>",
            identifier = "urn:uuid:8f6a5e1e-3b2a-4d4e-9c1d-2f5a7b8c9d00",
            language = "en",
            authors = listOf("Jane Doe", "John \"Quotes\" Roe"),
            publisher = "Readana Test Press",
            description = "A tiny book used to validate the writer.",
            modified = Instant.parse("2026-08-20T12:00:00Z")
        ),
        chapters = listOf(
            EpubChapter(
                id = "ch-1",
                title = "Chapter One",
                bodyXhtml =
                    Xhtml.heading(1, "Chapter One") +
                        Xhtml.paragraph("Hello & welcome <reader>.")
            ),
            EpubChapter(
                id = "ch-2",
                title = "Chapter Two",
                bodyXhtml = Xhtml.heading(1, "Chapter Two") +
                    Xhtml.paragraph("Second chapter.") +
                    Xhtml.image("../images/cover.png", "Cover")
            )
        ),
        cover = EpubResource("images/cover.png", "image/png", ONE_PIXEL_PNG)
    )

    private fun nestedNavBook(): EpubBook = EpubBook(
        metadata = EpubMetadata(
            title = "Nested",
            identifier = "urn:uuid:8f6a5e1e-3b2a-4d4e-9c1d-2f5a7b8c9d01",
            modified = Instant.parse("2026-08-20T12:00:00Z")
        ),
        chapters = listOf(
            EpubChapter(
                id = "ch-1",
                title = "Chapter One",
                bodyXhtml = Xhtml.heading(1, "Chapter One") +
                    Xhtml.paragraph("Opening.") +
                    Xhtml.heading(2, "A Section", id = "h-0001") +
                    Xhtml.paragraph("Section body.") +
                    Xhtml.image("../images/img-0001.png", "A figure")
            ),
            EpubChapter(
                id = "ch-2",
                title = "Chapter Two",
                bodyXhtml = Xhtml.heading(1, "Chapter Two") + Xhtml.paragraph("More.")
            )
        ),
        resources = listOf(EpubResource("images/img-0001.png", "image/png", ONE_PIXEL_PNG)),
        navigation = listOf(
            NavEntry(
                title = "Chapter One",
                href = "text/ch-1.xhtml",
                children = listOf(NavEntry("A Section", "text/ch-1.xhtml#h-0001"))
            ),
            NavEntry(title = "Chapter Two", href = "text/ch-2.xhtml")
        )
    )

    private companion object {
        const val MIMETYPE_OFFSET = 38

        /** A valid 1×1 transparent PNG. */
        val ONE_PIXEL_PNG: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
        )
    }
}
