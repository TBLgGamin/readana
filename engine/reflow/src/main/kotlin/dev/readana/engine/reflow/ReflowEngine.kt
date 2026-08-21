package dev.readana.engine.reflow

import dev.readana.engine.reflow.model.DocumentContent
import dev.readana.engine.reflow.model.PageContent
import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Turns positioned page text (from a PDF) into reflowable blocks using layout heuristics,
 * in the spirit of Calibre's PDF input plugin:
 *
 * 1. drop running headers/footers and bare page numbers that repeat across pages,
 * 2. merge wrapped lines into paragraphs (gap, indent, short-line and font-change rules),
 * 3. re-join words hyphenated at line ends,
 * 4. promote short, larger or bold paragraphs to headings.
 *
 * Pure Kotlin so it is unit-testable without Android or PDFBox.
 */
class ReflowEngine(private val options: ReflowOptions = ReflowOptions()) {
    /** Text-only entry point, kept for callers that have no images or outline. */
    fun reflow(pages: List<PageText>): List<Block> = reflow(DocumentContent(pages.map { PageContent(it) })).blocks

    fun reflow(document: DocumentContent): ReflowResult {
        val populated = document.pages
            .map { page ->
                page.copy(
                    text = page.text.copy(
                        lines = page.text.lines.filter { it.text.isNotBlank() }.sortedBy { it.baseline }
                    )
                )
            }
            .filter { it.text.lines.isNotEmpty() || it.images.isNotEmpty() }
        if (populated.isEmpty()) return ReflowResult(emptyList(), multiColumnPages = 0)

        val body = if (options.removeRepeatedHeadersAndFooters) {
            filterHeadersAndFooters(populated)
        } else {
            populated
        }
        if (body.isEmpty()) return ReflowResult(emptyList(), multiColumnPages = 0)

        val stats = LayoutStats.from(body.map { it.text }.filter { it.lines.isNotEmpty() })
            ?: return ReflowResult(emptyList(), multiColumnPages = 0)

        val segmenter = PageSegmenter(options)
        val items = segmenter.segment(body, stats.bodyFontSize)
        val paragraphs = ParagraphBuilder(options, stats).build(items)
        return ReflowResult(
            blocks = HeadingClassifier(options, stats).classify(paragraphs),
            multiColumnPages = segmenter.multiColumnPages
        )
    }

    /** Runs the header/footer filter over the text while keeping each page's images attached. */
    private fun filterHeadersAndFooters(pages: List<PageContent>): List<PageContent> {
        val withText = pages.filter { it.text.lines.isNotEmpty() }
        val filtered = HeaderFooterFilter(options).filter(withText.map { it.text })
            .associateBy { it.index }
        return pages.mapNotNull { page ->
            val text = filtered[page.text.index]
            when {
                text != null -> page.copy(text = text)

                // Every line was a running header, but a figure on that page still counts.
                page.images.isNotEmpty() -> page.copy(text = page.text.copy(lines = emptyList()))

                else -> null
            }
        }
    }
}

/** Blocks plus what the heuristics noticed while producing them. */
data class ReflowResult(val blocks: List<Block>, val multiColumnPages: Int)

internal class HeaderFooterFilter(private val options: ReflowOptions) {
    fun filter(pages: List<PageText>): List<PageText> {
        val occurrences = HashMap<String, Int>()
        pages.forEach { page ->
            page.lines.filter { inMarginBand(it, page) }
                .map { signature(it.text) }
                .distinct()
                .forEach { occurrences.merge(it, 1, Int::plus) }
        }
        val threshold = max(MIN_REPEATS, ceil(pages.size * options.repeatThresholdFraction).toInt())
        val repeated = occurrences.filterValues { it >= threshold }.keys

        return pages
            .map { page ->
                page.copy(
                    lines = page.lines.filterNot { line ->
                        inMarginBand(line, page) &&
                            (signature(line.text) in repeated || isBarePageNumber(line.text))
                    }
                )
            }
            .filter { it.lines.isNotEmpty() }
    }

    private fun inMarginBand(line: TextLine, page: PageText): Boolean {
        val band = page.height * options.marginBandFraction
        return line.baseline <= band || line.baseline >= page.height - band
    }

    private fun signature(text: String): String = text.lowercase().replace(DIGITS, "#").replace(WHITESPACE, " ").trim()

    private fun isBarePageNumber(text: String): Boolean = PAGE_NUMBER.matches(text.trim())

    private companion object {
        const val MIN_REPEATS = 2
        val DIGITS = Regex("\\d+")
        val WHITESPACE = Regex("\\s+")
        val PAGE_NUMBER =
            Regex("^(page\\s*)?[0-9ivxlcdm]{1,7}(\\s*(/|of)\\s*\\d+)?$", RegexOption.IGNORE_CASE)
    }
}

/** Typography facts about the document used to calibrate the heuristics. */
internal data class LayoutStats(val bodyFontSize: Float, val lineSpacing: Float, val typicalLineWidth: Float) {
    companion object {
        private const val BODY_FONT_TOLERANCE = 0.1f
        private const val MAX_SPACING_FACTOR = 3f
        private const val DEFAULT_SPACING_FACTOR = 1.2f

        fun from(pages: List<PageText>): LayoutStats? {
            val lines = pages.flatMap { it.lines }
            if (lines.isEmpty()) return null
            val bodyFontSize = lines.groupBy { it.fontSize }
                .maxBy { (_, group) -> group.sumOf { it.text.length } }
                .key
            val bodyLines = lines.filter {
                abs(it.fontSize - bodyFontSize) <=
                    bodyFontSize * BODY_FONT_TOLERANCE
            }

            val spacing = pages
                .flatMap { page -> page.lines.zipWithNext { a, b -> b.baseline - a.baseline } }
                .filter { it > 0f && it < bodyFontSize * MAX_SPACING_FACTOR }
                .median() ?: (bodyFontSize * DEFAULT_SPACING_FACTOR)

            val width = bodyLines.map { it.width }.median() ?: lines.maxOf { it.width }

            return LayoutStats(bodyFontSize, spacing, width)
        }

        private fun List<Float>.median(): Float? {
            if (isEmpty()) return null
            val sorted = sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 ==
                1
            ) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2f
            }
        }
    }
}

/** A run of lines that will become one paragraph, or a figure standing on its own. */
internal sealed interface Chunk {
    val pageIndex: Int

    class Text(first: ReadingItem.Line) : Chunk {
        val lines = mutableListOf(first.line)
        val fontSize: Float = first.line.fontSize
        val isBold: Boolean = first.line.isBold
        override val pageIndex: Int = first.pageIndex
        val text: String
            get() = joinLines(lines.map { it.text })
    }

    class Figure(val item: ReadingItem.Image) : Chunk {
        override val pageIndex: Int = item.pageIndex
    }
}

internal class ParagraphBuilder(private val options: ReflowOptions, private val stats: LayoutStats) {
    fun build(items: List<ReadingItem>): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var previous: ReadingItem.Line? = null
        for (item in items) {
            when (item) {
                is ReadingItem.Image -> {
                    chunks += Chunk.Figure(item)
                    // A figure interrupts the text around it, whatever the spacing says.
                    previous = null
                }

                is ReadingItem.Line -> {
                    val prev = previous
                    val open = chunks.lastOrNull() as? Chunk.Text
                    if (prev == null || open == null || startsNewParagraph(prev, item)) {
                        chunks += Chunk.Text(item)
                    } else {
                        open.lines += item.line
                    }
                    previous = item
                }
            }
        }
        return chunks
    }

    private fun startsNewParagraph(prev: ReadingItem.Line, next: ReadingItem.Line): Boolean {
        val fontChanged =
            abs(next.line.fontSize - prev.line.fontSize) >
                max(next.line.fontSize, prev.line.fontSize) * FONT_CHANGE_TOLERANCE
        if (fontChanged || next.line.isBold != prev.line.isBold) return true

        // Measured against this column's own left edge, so a second column is not all "indented".
        val indent = next.line.left - next.columnLeft
        if (indent > options.indentFactor * stats.bodyFontSize) return true

        val prevIsShort = prev.line.width < options.shortLineFraction * stats.typicalLineWidth
        if (prevIsShort && prev.line.text.endsWithSentenceTerminator()) return true

        // Only meaningful within one column of one page; a paragraph may run across the break.
        if (next.pageIndex == prev.pageIndex && next.columnIndex == prev.columnIndex) {
            val gap = next.line.baseline - prev.line.baseline
            if (gap > options.paragraphGapFactor * stats.lineSpacing) return true
        }
        return false
    }

    private companion object {
        const val FONT_CHANGE_TOLERANCE = 0.1f
    }
}

internal class HeadingClassifier(private val options: ReflowOptions, private val stats: LayoutStats) {
    fun classify(chunks: List<Chunk>): List<Block> {
        val texts = chunks.filterIsInstance<Chunk.Text>()
        val headingSizes = texts.filter { isHeading(it) }.map { it.fontSize }.distinct().sortedDescending()
        return chunks.mapNotNull { chunk ->
            when (chunk) {
                is Chunk.Figure -> Block.Image(
                    bytes = chunk.item.image.bytes,
                    mediaType = chunk.item.image.mediaType,
                    pixelWidth = chunk.item.image.pixelWidth,
                    pixelHeight = chunk.item.image.pixelHeight,
                    altText = "",
                    pageIndex = chunk.pageIndex
                )

                is Chunk.Text -> chunk.takeIf { it.text.isNotBlank() }?.let { paragraph ->
                    if (isHeading(paragraph)) {
                        val level = (headingSizes.indexOf(paragraph.fontSize) + 1)
                            .coerceIn(1, Block.Heading.MAX_LEVEL)
                        Block.Heading(level, paragraph.text, paragraph.pageIndex)
                    } else {
                        Block.Paragraph(paragraph.text, paragraph.pageIndex)
                    }
                }
            }
        }
    }

    private fun isHeading(paragraph: Chunk.Text): Boolean {
        val larger = paragraph.fontSize >= options.headingSizeFactor * stats.bodyFontSize
        val boldAtBodySize =
            paragraph.isBold && paragraph.fontSize >= stats.bodyFontSize * BOLD_SIZE_TOLERANCE
        val short =
            paragraph.lines.size <= MAX_HEADING_LINES &&
                paragraph.text.length <= options.maxHeadingChars
        return (larger || boldAtBodySize) && short
    }

    private companion object {
        const val MAX_HEADING_LINES = 3
        const val BOLD_SIZE_TOLERANCE = 0.95f
    }
}

private val SENTENCE_TERMINATORS = setOf('.', '!', '?', '…')
private val CLOSING_PUNCTUATION = setOf('"', '”', '’', '\'', ')', ']')

internal fun String.endsWithSentenceTerminator(): Boolean {
    val trimmed = trimEnd().trimEnd { it in CLOSING_PUNCTUATION }
    return trimmed.lastOrNull() in SENTENCE_TERMINATORS
}

/** Joins wrapped lines, removing soft line-end hyphenation ("run-" + "ning" → "running"). */
internal fun joinLines(lines: List<String>): String {
    val builder = StringBuilder()
    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        // "run-" + "ning" → "running"; "Wi-" + "Fi" → "Wi-Fi"; a spaced dash ("well -") stays a dash.
        val wordHyphen = builder.length >= 2 &&
            builder.last() == '-' &&
            builder[builder.length - 2].isLetterOrDigit()
        when {
            builder.isEmpty() -> builder.append(line)

            wordHyphen && line.first().isLowerCase() -> {
                builder.setLength(builder.length - 1)
                builder.append(line)
            }

            wordHyphen && line.first().isUpperCase() -> builder.append(line)

            else -> builder.append(' ').append(line)
        }
    }
    return normalizeText(builder.toString())
}

private val LIGATURES = mapOf(
    "ﬀ" to "ff",
    "ﬁ" to "fi",
    "ﬂ" to "fl",
    "ﬃ" to "ffi",
    "ﬄ" to "ffl",
    "ﬅ" to "ft",
    "ﬆ" to "st"
)
private val CONTROL_CHARS = Regex("[\\p{Cntrl}\\u00AD]")
private val MULTI_SPACE = Regex("[\\s\\u00A0]+")

internal fun normalizeText(text: String): String {
    var result = text
    LIGATURES.forEach { (ligature, replacement) -> result = result.replace(ligature, replacement) }
    return result.replace(CONTROL_CHARS, "").replace(MULTI_SPACE, " ").trim()
}
