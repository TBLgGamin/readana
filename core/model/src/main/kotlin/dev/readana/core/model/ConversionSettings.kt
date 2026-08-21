package dev.readana.core.model

/**
 * How PDFs are turned into EPUB. Deliberately small: the reflow engine has many tunables, but only
 * the ones a reader can judge from the result are exposed.
 */
data class ConversionSettings(
    /** Convert PDFs as they arrive. The original file is always kept either way. */
    val autoConvertPdf: Boolean = true,
    val removeHeadersAndFooters: Boolean = true,
    val headingSensitivity: HeadingSensitivity = HeadingSensitivity.Balanced,
    val extractImages: Boolean = true
)

/** How eager the converter is to call a line a heading. */
enum class HeadingSensitivity {
    /** Only clearly larger type; long documents end up with few, reliable chapters. */
    Conservative,

    Balanced,

    /** Slightly larger or bold type counts, which finds more chapters and more false ones. */
    Aggressive
}
