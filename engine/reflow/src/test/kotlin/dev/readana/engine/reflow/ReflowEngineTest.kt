package dev.readana.engine.reflow

import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflowEngineTest {
    private val engine = ReflowEngine()

    @Test
    fun `merges wrapped lines into one paragraph and re-joins hyphenated words`() {
        val page = page(
            0,
            line("The quick brown fox jumps over the lazy dog and keeps run-", baseline = 100f),
            line("ning through the forest until it reaches the river bank.", baseline = 112f)
        )

        assertEquals(
            listOf(
                Block.Paragraph(
                    "The quick brown fox jumps over the lazy dog and keeps running through the forest " +
                        "until it reaches the river bank."
                )
            ),
            engine.reflow(listOf(page))
        )
    }

    @Test
    fun `a large vertical gap starts a new paragraph`() {
        val page = page(
            0,
            line("First paragraph continues on this line and on the next one", baseline = 100f),
            line("and finally wraps up here with a few more words to fill it", baseline = 112f),
            line("Second paragraph begins after a visibly larger vertical gap", baseline = 148f),
            line("and also spans a couple of lines just like the first one did", baseline = 160f)
        )

        val blocks = engine.reflow(listOf(page))

        assertEquals(2, blocks.size)
        assertTrue(blocks[0].text.startsWith("First paragraph"))
        assertTrue(blocks[1].text.startsWith("Second paragraph"))
    }

    @Test
    fun `an indented first line starts a new paragraph`() {
        val page = page(
            0,
            line("Body text flows along the left margin for the whole width", baseline = 100f),
            line("of the column and continues without any indentation at all.", baseline = 112f),
            line("Indented opening of the next paragraph, clearly offset", baseline = 124f, x = 90f),
            line("and then back to the margin for the remaining wrapped lines.", baseline = 136f)
        )

        val blocks = engine.reflow(listOf(page))

        assertEquals(2, blocks.size)
        assertTrue(blocks[1].text.startsWith("Indented opening"))
    }

    @Test
    fun `paragraphs continue across page boundaries`() {
        val first = page(
            0,
            line("This sentence starts near the bottom of the first page and", baseline = 690f),
            line("keeps going because the author had more to say about the", baseline = 702f)
        )
        val second = page(
            1,
            line("subject, finishing the thought on the following page.", baseline = 100f)
        )

        val blocks = engine.reflow(listOf(first, second))

        assertEquals(1, blocks.size)
        assertTrue(blocks.single().text.endsWith("following page."))
    }

    @Test
    fun `running headers and page numbers are removed`() {
        val pages = (0 until 3).map { index ->
            page(
                index,
                line("A Tale of Two Layouts", baseline = 30f),
                line("Body line one of page ${index + 1} with enough words to look real.", baseline = 100f),
                line("Body line two of page ${index + 1} with enough words to look real.", baseline = 112f),
                line("${index + 12}", baseline = 770f, x = 300f)
            )
        }

        val text = engine.reflow(pages).joinToString("\n") { it.text }

        assertTrue("A Tale of Two Layouts" !in text)
        assertTrue(Regex("\\b1[234]\\b").containsMatchIn(text).not())
        assertTrue("Body line one of page 3" in text)
    }

    @Test
    fun `larger font lines become headings and split chapters`() {
        val page = page(
            0,
            line("Chapter One", baseline = 80f, fontSize = 18f),
            line("Opening paragraph of the first chapter with some wrapped words", baseline = 110f),
            line("that continue on the next line so the paragraph has real width.", baseline = 122f),
            line("Chapter Two", baseline = 180f, fontSize = 18f),
            line("Opening paragraph of the second chapter which is equally verbose", baseline = 210f)
        )

        val blocks = engine.reflow(listOf(page))
        val book = EpubAssembler.assemble(blocks, dev.readana.engine.epub.EpubMetadata(title = "Test"))

        assertEquals(Block.Heading(1, "Chapter One"), blocks.first())
        assertEquals(listOf("Chapter One", "Chapter Two"), book.chapters.map { it.title })
        assertTrue("<h1>Chapter Two</h1>" in book.chapters[1].bodyXhtml)
    }

    @Test
    fun `bold body-size lines become the lowest heading level`() {
        val page = page(
            0,
            line("Section title", baseline = 80f, font = "Helvetica-Bold"),
            line("Body paragraph under the bold section title with enough words", baseline = 100f),
            line("to count as ordinary running text across a couple of lines.", baseline = 112f)
        )

        val blocks = engine.reflow(listOf(page))

        assertEquals(Block.Heading(1, "Section title"), blocks.first())
        assertEquals(2, blocks.size)
    }

    @Test
    fun `documents without headings are chunked into chapters`() {
        val lines = (0 until 12).map { i ->
            line(
                "Paragraph number $i with a full stop at the end.",
                baseline =
                    100f + i * 30f
            )
        }
        val blocks = engine.reflow(listOf(page(0, *lines.toTypedArray(), height = 2000f)))

        val book = EpubAssembler.assemble(
            blocks,
            dev.readana.engine.epub.EpubMetadata(title = "Chunky"),
            maxParagraphsPerChapter = 5
        )

        assertEquals(12, blocks.size)
        assertEquals(3, book.chapters.size)
        assertEquals("Chunky", book.chapters.first().title)
    }

    @Test
    fun `normalizes ligatures and whitespace`() {
        assertEquals("The office fly", normalizeText("The  oﬃce ﬂy "))
        assertEquals("running", joinLines(listOf("run-", "ning")))
        assertEquals("well - known", joinLines(listOf("well -", "known")))
        assertEquals("Wi-Fi", joinLines(listOf("Wi-", "Fi")))
    }

    private fun page(index: Int, vararg lines: TextLine, width: Float = 612f, height: Float = 792f) =
        PageText(index, width, height, lines.toList())

    private fun line(
        text: String,
        baseline: Float,
        x: Float = 72f,
        fontSize: Float = 10f,
        font: String? = null,
        width: Float = text.length * fontSize * 0.5f
    ) = TextLine.of(text, x, baseline, width, fontSize, font)
}
