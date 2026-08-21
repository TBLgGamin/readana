package dev.readana.engine.reflow

import dev.readana.engine.epub.EpubMetadata
import dev.readana.engine.reflow.model.OutlineNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpubAssemblerTest {
    private val metadata = EpubMetadata(title = "Test Book", identifier = "urn:uuid:fixed")

    @Test
    fun `the pdf outline drives the chapters when it lines up with the text`() {
        val blocks = listOf(
            Block.Heading(1, "Introduction", pageIndex = 0),
            Block.Paragraph("Opening remarks.", pageIndex = 0),
            Block.Heading(1, "Methods", pageIndex = 4),
            Block.Paragraph("What we did.", pageIndex = 4)
        )
        val outline = listOf(OutlineNode("Introduction", 0), OutlineNode("Methods", 4))

        val assembly = EpubAssembler.assemble(blocks, metadata, outline)

        assertTrue(assembly.outlineUsed)
        assertEquals(listOf("Introduction", "Methods"), assembly.book.chapters.map { it.title })
        assertEquals(
            listOf("Introduction", "Methods"),
            assertNotNull(assembly.book.navigation).map { it.title }
        )
    }

    @Test
    fun `outline children become anchors inside their chapter`() {
        val blocks = listOf(
            Block.Heading(1, "Methods", pageIndex = 0),
            Block.Paragraph("Overview.", pageIndex = 0),
            Block.Heading(2, "Sampling", pageIndex = 1),
            Block.Paragraph("How we sampled.", pageIndex = 1),
            Block.Heading(1, "Results", pageIndex = 5),
            Block.Paragraph("What we found.", pageIndex = 5)
        )
        val outline = listOf(
            OutlineNode("Methods", 0, listOf(OutlineNode("Sampling", 1))),
            OutlineNode("Results", 5)
        )

        val assembly = EpubAssembler.assemble(blocks, metadata, outline)

        val nav = assertNotNull(assembly.book.navigation)
        val child = nav.first().children.single()
        assertEquals("Sampling", child.title)
        assertTrue(child.href.endsWith("#h-0001"), "child links to an anchor, was ${child.href}")
        assertTrue("id=\"h-0001\"" in assembly.book.chapters.first().bodyXhtml)
        assertEquals(0, assembly.droppedNavEntries)
    }

    @Test
    fun `an outline that does not match the text is ignored`() {
        val blocks = listOf(
            Block.Heading(1, "Only Chapter", pageIndex = 0),
            Block.Paragraph("Body.", pageIndex = 0)
        )
        // Both entries point past the end of the document.
        val outline = listOf(OutlineNode("Ghost One", 40), OutlineNode("Ghost Two", 41))

        val assembly = EpubAssembler.assemble(blocks, metadata, outline)

        assertTrue(!assembly.outlineUsed)
        assertEquals(listOf("Only Chapter"), assembly.book.chapters.map { it.title })
        assertNull(assembly.book.navigation, "the flat chapter list is used instead")
    }

    @Test
    fun `an outline whose pages run backwards is ignored`() {
        val blocks = listOf(
            Block.Heading(1, "A", pageIndex = 0),
            Block.Paragraph("Body.", pageIndex = 0),
            Block.Heading(1, "B", pageIndex = 1),
            Block.Paragraph("Body.", pageIndex = 1)
        )

        val assembly = EpubAssembler.assemble(
            blocks,
            metadata,
            listOf(OutlineNode("B", 1), OutlineNode("A", 0))
        )

        assertTrue(!assembly.outlineUsed)
    }

    @Test
    fun `images become resources referenced from the chapter`() {
        val blocks = listOf(
            Block.Paragraph("Before the figure.", pageIndex = 0),
            image(byteArrayOf(1, 2, 3), "image/jpeg"),
            Block.Paragraph("After the figure.", pageIndex = 0)
        )

        val book = EpubAssembler.assemble(blocks, metadata).book

        val resource = book.resources.single()
        assertEquals("images/img-0001.jpg", resource.href)
        assertEquals("image/jpeg", resource.mediaType)
        assertContentEquals(byteArrayOf(1, 2, 3), resource.bytes)
        assertTrue("""src="../images/img-0001.jpg"""" in book.chapters.single().bodyXhtml)
    }

    @Test
    fun `identical images are stored once`() {
        val logo = byteArrayOf(9, 9, 9)
        val blocks = listOf(
            image(logo, "image/png", page = 0),
            Block.Paragraph("Page one.", pageIndex = 0),
            image(logo, "image/png", page = 1),
            Block.Paragraph("Page two.", pageIndex = 1)
        )

        val book = EpubAssembler.assemble(blocks, metadata).book

        assertEquals(1, book.resources.size)
        assertEquals(2, book.chapters.single().bodyXhtml.split("images/img-0001.png").size - 1)
    }

    private fun image(bytes: ByteArray, mediaType: String, page: Int = 0) = Block.Image(
        bytes = bytes,
        mediaType = mediaType,
        pixelWidth = 100,
        pixelHeight = 80,
        altText = "",
        pageIndex = page
    )
}
