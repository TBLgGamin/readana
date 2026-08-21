package dev.readana.engine.reflow

import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

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
    fun reflow(pages: List<PageText>): List<Block> {
        val populated = pages
            .map { page ->
                page.copy(
                    lines = page.lines.filter {
                        it.text.isNotBlank()
                    }.sortedBy { it.baseline }
                )
            }
            .filter { it.lines.isNotEmpty() }
        if (populated.isEmpty()) return emptyList()

        val body = if (options.removeRepeatedHeadersAndFooters) {
            HeaderFooterFilter(
                options
            ).filter(populated)
        } else {
            populated
        }
        if (body.isEmpty()) return emptyList()

        val stats = LayoutStats.from(body)
        val paragraphs = ParagraphBuilder(options, stats).build(body)
        return HeadingClassifier(options, stats).classify(paragraphs)
    }
}

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
internal data class LayoutStats(
    val bodyFontSize: Float,
    val lineSpacing: Float,
    val typicalLineWidth: Float,
    private val leftMargins: Map<Int, Float>
) {
    fun leftMargin(page: PageText): Float = leftMargins[page.index] ?: page.lines.minOf { it.left }

    companion object {
        private const val BODY_FONT_TOLERANCE = 0.1f
        private const val MAX_SPACING_FACTOR = 3f
        private const val DEFAULT_SPACING_FACTOR = 1.2f
        private const val MARGIN_PERCENTILE = 0.1f

        fun from(pages: List<PageText>): LayoutStats {
            val lines = pages.flatMap { it.lines }
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
            val margins = pages.associate { page ->
                page.index to
                    page.lines.map { it.left }.percentile(MARGIN_PERCENTILE)
            }

            return LayoutStats(bodyFontSize, spacing, width, margins)
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

        private fun List<Float>.percentile(fraction: Float): Float {
            val sorted = sorted()
            val index = ((sorted.size - 1) * fraction).roundToInt().coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
    }
}

internal class RawParagraph(firstLine: TextLine) {
    val lines = mutableListOf(firstLine)
    val fontSize: Float = firstLine.fontSize
    val isBold: Boolean = firstLine.isBold
    val text: String
        get() = joinLines(lines.map { it.text })
}

internal class ParagraphBuilder(private val options: ReflowOptions, private val stats: LayoutStats) {
    fun build(pages: List<PageText>): List<RawParagraph> {
        val paragraphs = mutableListOf<RawParagraph>()
        var previous: Pair<TextLine, PageText>? = null
        for (page in pages) {
            for (line in page.lines) {
                val prev = previous
                if (prev == null || startsNewParagraph(prev.first, prev.second, line, page)) {
                    paragraphs += RawParagraph(line)
                } else {
                    paragraphs.last().lines += line
                }
                previous = line to page
            }
        }
        return paragraphs
    }

    private fun startsNewParagraph(prev: TextLine, prevPage: PageText, line: TextLine, page: PageText): Boolean {
        val fontChanged =
            abs(line.fontSize - prev.fontSize) >
                max(line.fontSize, prev.fontSize) * FONT_CHANGE_TOLERANCE
        if (fontChanged || line.isBold != prev.isBold) return true

        val indent = line.left - stats.leftMargin(page)
        if (indent > options.indentFactor * stats.bodyFontSize) return true

        val prevIsShort = prev.width < options.shortLineFraction * stats.typicalLineWidth
        if (prevIsShort && prev.text.endsWithSentenceTerminator()) return true

        if (page.index == prevPage.index) {
            val gap = line.baseline - prev.baseline
            if (gap > options.paragraphGapFactor * stats.lineSpacing) return true
        }
        return false
    }

    private companion object {
        const val FONT_CHANGE_TOLERANCE = 0.1f
    }
}

internal class HeadingClassifier(private val options: ReflowOptions, private val stats: LayoutStats) {
    fun classify(paragraphs: List<RawParagraph>): List<Block> {
        val headingSizes = paragraphs.filter {
            isHeading(it)
        }.map { it.fontSize }.distinct().sortedDescending()
        return paragraphs
            .filter { it.text.isNotBlank() }
            .map { paragraph ->
                if (isHeading(paragraph)) {
                    val level = (
                        headingSizes.indexOf(
                            paragraph.fontSize
                        ) + 1
                        ).coerceIn(1, Block.Heading.MAX_LEVEL)
                    Block.Heading(level, paragraph.text)
                } else {
                    Block.Paragraph(paragraph.text)
                }
            }
    }

    private fun isHeading(paragraph: RawParagraph): Boolean {
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
