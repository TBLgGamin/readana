package dev.readana.engine.reflow

/** Reflowed document content, in reading order. */
sealed interface Block {
    val text: String

    data class Heading(val level: Int, override val text: String) : Block {
        init {
            require(level in 1..MAX_LEVEL) { "Heading level must be 1..$MAX_LEVEL but was $level" }
        }

        companion object {
            const val MAX_LEVEL = 3
        }
    }

    data class Paragraph(override val text: String) : Block
}

/** Tunables for the PDF → reflowable text heuristics. Defaults suit typical body text at 9–12pt. */
data class ReflowOptions(
    val removeRepeatedHeadersAndFooters: Boolean = true,
    /** Fraction of the page height at top and bottom where running headers/footers live. */
    val marginBandFraction: Float = 0.08f,
    /** A line in the margin band repeated on at least this fraction of pages (min 2) is dropped. */
    val repeatThresholdFraction: Float = 0.3f,
    /** Vertical gap larger than this multiple of the usual line spacing starts a new paragraph. */
    val paragraphGapFactor: Float = 1.35f,
    /** Horizontal indent larger than this multiple of the body font size starts a new paragraph. */
    val indentFactor: Float = 1.2f,
    /** A sentence-ending line shorter than this fraction of the usual width ends its paragraph. */
    val shortLineFraction: Float = 0.7f,
    /** Font size at least this multiple of the body size marks a heading. */
    val headingSizeFactor: Float = 1.15f,
    val maxHeadingChars: Int = 120
)
