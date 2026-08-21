package dev.readana.engine.reflow.model

import java.io.InputStream
import kotlin.math.roundToInt

/**
 * A run of characters sharing one font on one line. Coordinates are in PDF points with the
 * origin at the top-left of the page (y grows downwards).
 */
data class TextSpan(
    val text: String,
    val x: Float,
    val width: Float,
    val fontSize: Float,
    val fontName: String? = null
) {
    val isBold: Boolean
        get() = fontName?.let { name ->
            BOLD_MARKERS.any { name.contains(it, ignoreCase = true) }
        } ==
            true

    private companion object {
        val BOLD_MARKERS = listOf("bold", "black", "heavy", "semibold", "demibold")
    }
}

data class TextLine(
    val spans: List<TextSpan>,
    /** Distance from the top of the page to the text baseline. */
    val baseline: Float,
    val height: Float
) {
    init {
        require(spans.isNotEmpty()) { "A line needs at least one span" }
    }

    val text: String
        get() = spans.joinToString(separator = "") { it.text }.trim()

    val left: Float
        get() = spans.minOf { it.x }

    val right: Float
        get() = spans.maxOf { it.x + it.width }

    val width: Float
        get() = right - left

    /** Font size (rounded to 0.5pt) that covers the most characters on the line. */
    val fontSize: Float
        get() = spans.groupBy { (it.fontSize * 2).roundToInt() / 2f }
            .maxBy { (_, runs) -> runs.sumOf { it.text.length } }
            .key

    /** True when most characters on the line use a bold face. */
    val isBold: Boolean
        get() {
            val total = spans.sumOf { it.text.length }.coerceAtLeast(1)
            val bold = spans.filter { it.isBold }.sumOf { it.text.length }
            return bold * 2 > total
        }

    companion object {
        /** Convenience for single-font lines (tests, simple extractors). */
        fun of(text: String, x: Float, baseline: Float, width: Float, fontSize: Float, fontName: String? = null) =
            TextLine(listOf(TextSpan(text, x, width, fontSize, fontName)), baseline, fontSize)
    }
}

data class PageText(
    /** Zero-based page index. */
    val index: Int,
    val width: Float,
    val height: Float,
    val lines: List<TextLine>
)

/** Produces positioned text for every page of a fixed-layout document (implemented with PDFBox on Android). */
interface TextExtractor {
    fun extract(input: InputStream): List<PageText>
}
