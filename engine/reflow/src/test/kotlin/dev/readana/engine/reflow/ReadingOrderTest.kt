package dev.readana.engine.reflow

import dev.readana.engine.reflow.model.DocumentContent
import dev.readana.engine.reflow.model.PageContent
import dev.readana.engine.reflow.model.PageImage
import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Column detection and figure placement: the pre-pass that decides reading order. */
class ReadingOrderTest {
    private val engine = ReflowEngine()

    @Test
    fun `a two-column page is read down the left column then the right`() {
        val left = (0 until 6).map { i ->
            line("Left column line number $i carrying enough text.", x = 60f, baseline = 100f + i * 14f, width = 200f)
        }
        val right = (0 until 6).map { i ->
            line("Right column line number $i carrying enough text.", x = 330f, baseline = 100f + i * 14f, width = 200f)
        }

        val result = engine.reflow(document(pageOf(left + right)))

        val text = result.blocks.joinToString(" ") { it.text }
        assertEquals(1, result.multiColumnPages)
        assertTrue(text.indexOf("Left column line number 5") < text.indexOf("Right column line number 0"))
    }

    @Test
    fun `a full-width heading splits the columns into bands`() {
        val title = line(
            "A Title That Spans The Whole Page Width",
            x = 60f,
            baseline = 60f,
            width = 470f,
            fontSize = 18f
        )
        val left = (0 until 6).map { i ->
            line("Left body line $i with running text here.", x = 60f, baseline = 100f + i * 14f, width = 200f)
        }
        val right = (0 until 6).map { i ->
            line("Right body line $i with running text here.", x = 330f, baseline = 100f + i * 14f, width = 200f)
        }

        val result = engine.reflow(document(pageOf(listOf(title) + left + right)))

        val texts = result.blocks.map { it.text }
        assertTrue(texts.first().startsWith("A Title That Spans"), "the spanning title is read first")
        assertEquals(1, result.multiColumnPages)
    }

    @Test
    fun `an ordinary single-column page is left alone`() {
        val lines = (0 until 8).map { i ->
            line("Ordinary running text on line $i of a normal page.", x = 60f, baseline = 100f + i * 14f, width = 470f)
        }

        val result = engine.reflow(document(pageOf(lines)))

        assertEquals(0, result.multiColumnPages)
        assertEquals(1, result.blocks.size, "one flowing paragraph, not two columns")
    }

    @Test
    fun `a centred pull quote does not look like a second column`() {
        val lines = (0 until 8).map { i ->
            line("Body line $i running the full measure of the page.", x = 60f, baseline = 100f + i * 14f, width = 470f)
        } + line("A short aside", x = 300f, baseline = 220f, width = 90f)

        assertEquals(0, engine.reflow(document(pageOf(lines))).multiColumnPages)
    }

    @Test
    fun `an image is placed by its position and interrupts the paragraph`() {
        val lines = listOf(
            line("Text above the figure that runs on for a while.", x = 60f, baseline = 100f, width = 400f),
            line("More text below the figure continuing the discussion.", x = 60f, baseline = 300f, width = 400f)
        )
        val image = image(x = 60f, y = 150f, width = 400f, height = 100f)

        val blocks = engine.reflow(document(PageContent(PageText(0, 612f, 792f, lines), listOf(image)))).blocks

        assertEquals(3, blocks.size)
        assertTrue(blocks[0].text.startsWith("Text above"))
        assertIs<Block.Image>(blocks[1])
        assertTrue(blocks[2].text.startsWith("More text below"))
    }

    @Test
    fun `a page holding only a figure still contributes it`() {
        val image = image(x = 60f, y = 100f, width = 400f, height = 300f)
        val textPage = pageOf(
            (0 until 4).map { i ->
                line("Running text on line $i of the first page.", x = 60f, baseline = 100f + i * 14f, width = 400f)
            }
        )
        val figurePage = PageContent(PageText(1, 612f, 792f, emptyList()), listOf(image))

        val blocks = engine.reflow(DocumentContent(listOf(textPage, figurePage))).blocks

        assertIs<Block.Image>(blocks.last())
    }

    @Test
    fun `a full-width figure is read after both columns, not inside one`() {
        val left = (0 until 6).map { i ->
            line("Left column line number $i carrying enough text.", x = 60f, baseline = 100f + i * 14f, width = 200f)
        }
        val right = (0 until 6).map { i ->
            line("Right column line number $i carrying enough text.", x = 330f, baseline = 100f + i * 14f, width = 200f)
        }
        // Spans the gutter near the bottom of the page, as a figure in a paper does.
        val figure = image(x = 60f, y = 400f, width = 470f, height = 120f)

        val blocks = engine
            .reflow(document(PageContent(PageText(0, 612f, 792f, left + right), listOf(figure))))
            .blocks

        assertIs<Block.Image>(blocks.last())
        assertTrue(
            blocks.first().text.contains("Right column line number 5"),
            "both columns should be read before the figure, got ${blocks.map { it.text.take(30) }}"
        )
    }

    @Test
    fun `a figure inside one column stays in that column`() {
        val left = (0 until 6).map { i ->
            line("Left column line number $i carrying enough text.", x = 60f, baseline = 100f + i * 14f, width = 200f)
        }
        val right = (0 until 6).map { i ->
            line("Right column line number $i carrying enough text.", x = 330f, baseline = 100f + i * 14f, width = 200f)
        }
        val figure = image(x = 60f, y = 400f, width = 200f, height = 100f)

        val blocks = engine
            .reflow(document(PageContent(PageText(0, 612f, 792f, left + right), listOf(figure))))
            .blocks

        // Left column, its figure, then the right column.
        assertIs<Block.Image>(blocks[1])
        assertTrue(blocks[2].text.startsWith("Right column"))
    }

    @Test
    fun `column detection can be switched off`() {
        val left = (0 until 6).map { i ->
            line("Left column line number $i carrying enough text.", x = 60f, baseline = 100f + i * 14f, width = 200f)
        }
        val right = (0 until 6).map { i ->
            line("Right column line number $i carrying enough text.", x = 330f, baseline = 100f + i * 14f, width = 200f)
        }

        val result = ReflowEngine(ReflowOptions(detectColumns = false))
            .reflow(document(pageOf(left + right)))

        assertEquals(0, result.multiColumnPages)
    }

    @Test
    fun `blocks remember the page they start on`() {
        val first = pageOf(listOf(line("First page paragraph text here.", x = 60f, baseline = 100f, width = 400f)))
        // A larger font ends the paragraph, so the second page opens its own block.
        val second = PageContent(
            PageText(
                1,
                612f,
                792f,
                listOf(line("Chapter Two", x = 60f, baseline = 100f, width = 200f, fontSize = 18f))
            )
        )

        val blocks = engine.reflow(DocumentContent(listOf(first, second))).blocks

        assertEquals(listOf(0, 1), blocks.map { it.pageIndex })
    }

    @Test
    fun `a paragraph still runs across a page break`() {
        val first = pageOf(listOf(line("A sentence that is cut off mid", x = 60f, baseline = 700f, width = 400f)))
        val second = PageContent(
            PageText(
                1,
                612f,
                792f,
                listOf(line("way and finishes on the next page.", x = 60f, baseline = 100f, width = 400f))
            )
        )

        val blocks = engine.reflow(DocumentContent(listOf(first, second))).blocks

        assertEquals(1, blocks.size)
        assertEquals("A sentence that is cut off mid way and finishes on the next page.", blocks.single().text)
    }

    private fun document(vararg pages: PageContent) = DocumentContent(pages.toList())

    private fun pageOf(lines: List<TextLine>, index: Int = 0) = PageContent(PageText(index, 612f, 792f, lines))

    private fun line(text: String, x: Float, baseline: Float, width: Float, fontSize: Float = 10f) =
        TextLine.of(text, x = x, baseline = baseline, width = width, fontSize = fontSize)

    private fun image(x: Float, y: Float, width: Float, height: Float) = PageImage(
        x = x,
        y = y,
        width = width,
        height = height,
        bytes = byteArrayOf(1, 2, 3),
        mediaType = "image/jpeg",
        pixelWidth = 800,
        pixelHeight = 200
    )
}
