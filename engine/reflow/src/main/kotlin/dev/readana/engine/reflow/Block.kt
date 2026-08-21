package dev.readana.engine.reflow

/** Reflowed document content, in reading order. */
sealed interface Block {
    val text: String

    /** Page the block starts on; the outline maps its entries onto blocks through this. */
    val pageIndex: Int

    data class Heading(val level: Int, override val text: String, override val pageIndex: Int = 0) : Block {
        init {
            require(level in 1..MAX_LEVEL) { "Heading level must be 1..$MAX_LEVEL but was $level" }
        }

        companion object {
            const val MAX_LEVEL = 3
        }
    }

    data class Paragraph(override val text: String, override val pageIndex: Int = 0) : Block

    /**
     * A figure lifted out of the page. Not a data class: [bytes] would make equality meaningless.
     * [text] is the alt text, which is all a text-only consumer can use.
     */
    class Image(
        val bytes: ByteArray,
        val mediaType: String,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val altText: String,
        override val pageIndex: Int
    ) : Block {
        override val text: String get() = altText
    }
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
    val maxHeadingChars: Int = 120,
    /**
     * Split pages that look like two columns. A safety valve rather than a user setting: the
     * detector is conservative, but a pathological layout can still be forced back to one column.
     */
    val detectColumns: Boolean = true
)
